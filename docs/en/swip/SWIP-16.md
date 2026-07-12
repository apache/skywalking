# SWIP-16 Support LLM-as-Judge on Top of GenAI Observability

## Motivation

SkyWalking already provides GenAI observability capabilities. Based on the existing GenAI semantic conventions and analysis pipeline, SkyWalking can recognize GenAI spans from multiple data sources, including SkyWalking native traces, OTLP, and Zipkin, extract GenAI-related attributes, build Virtual GenAI entities, and display runtime metrics such as traffic, latency, token usage, TTFT, TPOT, and estimated cost in the GenAI dashboard.

These capabilities answer "what happened during the invocation" and "how are performance and cost," but they do not yet answer "what is the quality of the model output." For GenAI applications in production, users usually also need to continuously observe quality signals such as:

- Whether the response is faithful to the given context
- Whether the response is relevant to the user query
- Whether the model completes the expected task
- Whether the response contains hallucinations

Today, such evaluation usually depends on external evaluation platforms, custom business-side scripts, or manual sampling workflows. This causes several problems:

- Evaluation results are disconnected from SkyWalking trace and span observability data
- Evaluation results cannot be stored as unified structured data in SkyWalking
- Evaluation tasks lack a unified OAP-side configuration model
- Evaluation results cannot be further aggregated into metrics and displayed in the existing GenAI dashboard

This SWIP proposes introducing `LLM-as-Judge` into SkyWalking OAP. On top of the existing GenAI observability capabilities, the whole evaluation feature reuses the current trace ingestion and GenAI span analysis pipeline and supports SkyWalking native traces, OTLP, and Zipkin as input sources. OAP samples runtime GenAI spans, extracts evaluation inputs, invokes a configurable judge model, and writes evaluation results into SkyWalking structured records. For `SCORE`-type evaluation results, OAP further generates metrics and displays them in the GenAI dashboard. Meanwhile, the UI adds an evaluation result page for detailed result browsing and supports jumping from a single evaluation result to the related trace, so that quality observability is integrated into the existing GenAI observability system.

## Architecture Graph

```text
GenAI spans / traces                  SkyWalking OAP                         Storage / Query / UI
-------------------                  --------------                         --------------------
SkyWalking native / OTLP / Zipkin  -> GenAI span analysis
                                      |- recognize GenAI spans
                                      |- extract context and tags
                                      '- trigger AI evaluation
                                                 |
                                                 v
                                      ai-evaluation module
                                      |- PPM sampling strategy
                                      |- evaluation planning
                                      |- prompt building
                                      |- Judge Provider
                                      '- result parsing
                                                 |
                                                 v
                                         External Judge Model
                                                 |
                                                 v
                                           Evaluation result
                                      |- write structured record
                                      '- generate MAL labeled metrics for SCORE results
                                                 |
                           +---------------------+----------------------+
                           |                                            |
                           v                                            v
                 records storage (`ai_evaluation_result`)      GraphQL / query API / GenAI dashboard / evaluation result view
```

## Proposed Changes

### 1. Introduce an independent AI evaluation module

OAP adds a dedicated `ai-evaluation` module to host runtime AI evaluation capabilities. In the current implementation, evaluation data is decoupled through an asynchronous local in-memory queue so that judge invocation is not executed synchronously on the trace analysis critical path. This module is responsible for:

- Loading evaluation-related configuration
- Validating judge model configuration
- Creating the judge provider
- Applying the sampling strategy
- Executing the evaluation strategy
- Persisting evaluation results
- Converting score-type evaluation results into MAL-based labeled metrics

This keeps evaluation logic modular and avoids coupling judge-related logic directly into the existing analyzer core.

### 2. Reuse the existing GenAI observability analysis entry

This capability does not introduce a new parallel collection pipeline. Instead, it is built on top of the existing GenAI observability capability and directly reuses the current trace ingestion paths, including SkyWalking native traces, OTLP, and Zipkin.

When OAP parses spans, the existing pipeline already recognizes GenAI spans. On top of that, the new analysis listener reuses runtime context and extracts the following information:

- `traceId`
- `spanId`
- `serviceName`
- `serviceInstanceName`
- `operationName`
- `providerName`
- `modelName`
- `startTimeMillis`
- `endTimeMillis`
- `error`
- `GenAI-related tags`

This information is packaged as the evaluation context and passed to the AI evaluation service, making evaluation results a natural extension of the existing GenAI observability model.

### 3. Introduce task-based LLM-as-Judge evaluation

The evaluation logic is task-driven rather than hardcoded around fixed dimensions. Each task defines the following fields:

- `name`
- `valueType`
- `instruction`

The initial default tasks include:

- `Faithfulness`
- `Relevance`
- `TaskCompletion`
- `Hallucination`

Based on the configured `system-prompt`, extracted GenAI context, and task list, OAP builds the prompt and sends it to the external judge model. The judge model returns structured JSON, and each task result contains at least:

- `value`
- `reason`

This design keeps evaluation dimensions configurable rather than hardcoded in the implementation.

### 4. Support an OpenAI-compatible judge provider

This SWIP introduces the `JudgeModelProvider` abstraction and provides the first implementation, `OpenAICompatibleProvider`.

Runtime configuration includes:

- `provider`
- `endpoint`
- `model`
- `api-key`

This allows OAP to call judge endpoints compatible with the OpenAI API format while leaving room for future provider extensions.

### 5. Introduce evaluation planning, prompt building, asynchronous queueing, and result parsing

The implementation introduces a clear runtime evaluation pipeline. In the current implementation, after a GenAI span hits sampling, OAP does not synchronously invoke evaluation. Instead, it first places the evaluation task into a local asynchronous in-memory queue, and a background evaluation consumer executes the remaining steps:

- `EvaluationInputExtractor`
- `EvaluationPlanner`
- `EvaluationPlan`
- `EvaluationPromptBuilder`
- `local async in-memory queue`
- `evaluation consumer`
- `EvaluationResultParser`
- `EvaluationResult`

The end-to-end flow is:

1. Extract evaluation input from the GenAI span context
2. Build the evaluation plan according to configured tasks
3. Put the evaluation task into the asynchronous local in-memory queue
4. The background evaluation consumer takes the task from the queue and builds the judge prompt
5. Invoke the judge model
6. Parse the returned JSON into structured evaluation results
7. Persist the evaluation results and generate MAL labeled metrics when applicable

This decouples evaluation orchestration from transport and storage logic and avoids blocking external model invocation on the trace analysis critical path.

### 6. Use PPM sampling for runtime evaluation

Because LLM-as-Judge introduces additional model invocation cost and runtime overhead, this SWIP does not evaluate all GenAI spans. Instead, it introduces a sampling strategy.

The sampling rate uses PPM, `parts per million`:

- `1_000_000` means 100% evaluation
- `100_000` means 10% evaluation
- `10_000` means 1% evaluation
- `0` means runtime evaluation is disabled

The module validates that the sampling rate is within `[0, 1_000_000]` and applies the configured sampling strategy before invoking the judge model.

### 7. Write evaluation results into SkyWalking structured records

Each evaluation result is written into SkyWalking storage through `AIEvaluationResultRecord` as a structured record.

The record includes the following core fields:

- `trace_id`
- `segment_id`
- `span_id`
- `span_type`
- `task_name`
- `value_type`
- `value`
- `evaluation_level`
- `reason`
- `judge_model`
- `evaluation_time`
- `time_bucket`

This lets each evaluation result directly link back to existing trace and span data. OAP also derives a normalized `evaluation_level` from the returned result when the value type supports level resolution, so later query and UI layers can filter and group records by coarse quality level in addition to raw value. In merged record storage mode, the data is written into the logical record table `ai_evaluation_result`.

### 8. Generate MAL labeled metrics from SCORE-type evaluation results

In addition to persisting evaluation results as structured records, this SWIP also proposes converting `SCORE`-type evaluation results into MAL-based labeled metrics.

For tasks where `valueType = SCORE`, the judge returns a numeric result in `[0.0, 1.0]`. OAP converts the result into a MAL `SampleFamily` and uses a MAL rule to generate the final metric. The task name is kept as a metric label instead of being encoded into the metric name, so newly configured evaluation tasks do not require additional OAL statements or new hardcoded metrics.

The initial metric is:

- `gen_ai_evaluation_score_ppm`

The metric is attached to the Virtual GenAI service instance dimension, using `service_name` as the service key and `model_name` as the instance key. The `task_name` remains as a labeled value dimension, allowing the same metric to represent scores for `Faithfulness`, `Relevance`, `TaskCompletion`, `Hallucination`, or any user-defined task.

Because SkyWalking MAL labeled values are stored as long values, the score is scaled before entering the MAL pipeline:

```text
stored value = score * 1,000,000
```

For example, a judge score of `0.86` is stored as `860000`. Query or UI code should divide the metric value by `1,000,000` when displaying the original score.

This labeled metric supports:

- Observing score trends by evaluation task
- Aggregating evaluation results by existing GenAI observability dimensions, especially service and model
- Displaying quality-related metrics grouped by `task_name` in the GenAI dashboard

This means the new capability is not only a record persistence feature, but also extends the GenAI dashboard from performance and cost observability to quality observability.

### 9. Add an evaluation result page and trace jump capability

In addition to aggregated dashboard metrics, the UI adds an evaluation result page for displaying structured evaluation details.

The page displays at least the following fields:

- `traceId`
- `segmentId`
- `spanId`
- `serviceName`
- `operationName`
- `taskName`
- `valueType`
- `value`
- `evaluationLevel`
- `reason`
- `judgeModel`
- `evaluationTime`

Users can filter the page by service, task name, evaluation level, time range, and other tag-based conditions, making it easier to investigate low scores, anomalies, or suspicious results.

Most importantly, each evaluation result keeps its association with the original trace. Users can click `traceId` or a jump button in the evaluation result page to open the related trace detail page directly and continue investigating the full call chain, contextual spans, and related GenAI tags.

This allows SkyWalking not only to show an evaluation result, but also to connect that result with runtime trace analysis and form a closed-loop troubleshooting experience from quality signal to execution context.

## Compatibility

This SWIP introduces a new OAP capability and a new record data model. The main compatibility impacts include:

- A new structured record type `ai_evaluation_result`, including the normalized `evaluation_level` field
- `SCORE`-type evaluation results additionally generate a MAL labeled metric for dashboard display
- The `gen_ai_evaluation_score_ppm` metric stores scores scaled by `1,000,000`; query and UI layers need to divide by `1,000,000` to display the original `[0.0, 1.0]` score
- The capability depends on the existing GenAI observability pipeline being able to recognize GenAI spans from SkyWalking native traces, OTLP, and Zipkin
- The UI adds an evaluation result page and trace jump based on `traceId`
- The current implementation uses an asynchronous local in-memory queue to carry evaluation tasks, and queue data is not part of any persistent protocol
- Runtime behavior is controlled jointly by the module switch, judge configuration, task configuration, and PPM sampling rate

## General usage docs

1. Enable the `ai-evaluation` module in OAP.
2. Configure the judge provider, endpoint, model, API key, system prompt, task list, and PPM sampling rate.
3. Ensure the existing GenAI observability pipeline is already receiving supported GenAI spans from SkyWalking native traces, OTLP, and Zipkin.
4. OAP generates evaluation tasks for sampled GenAI spans according to the PPM sampling rate and puts them into the asynchronous local in-memory queue.
5. A background evaluation consumer takes tasks from the queue and sends requests to the configured judge model.
6. OAP writes each evaluation result into SkyWalking structured records.
7. For `SCORE`-type tasks, OAP generates the MAL labeled metric `gen_ai_evaluation_score_ppm` from evaluation results.
8. Users observe both existing GenAI runtime metrics and newly added quality metrics in the GenAI dashboard. The UI should divide `gen_ai_evaluation_score_ppm` values by `1,000,000` to display the original score.
9. Users can also open the evaluation result page, inspect evaluation details, and jump from a single result to the related trace.