# AI Evaluation

SkyWalking can use an external OpenAI-compatible judge model to evaluate sampled GenAI spans. The result is stored as a `GenAIEvaluationRecord`; `SCORE` tasks also produce the `gen_ai_model_evaluation_score_ppm` metric for the Virtual GenAI dashboard.

The feature is disabled by default. It applies to GenAI spans received through SkyWalking native tracing, OTLP, and Zipkin, so the corresponding GenAI instrumentation and span attributes must already be present.

## Enable the module

Set the module selector when starting OAP:

```yaml
ai-evaluation:
  selector: ${SW_AI_EVALUATION:-}
```

For the built-in provider, set `SW_AI_EVALUATION=default`. The judge configuration is read from `config/ai-evaluation.yml`. OAP remains inactive when the judge endpoint, model, API key, or system prompt is missing.

## Instrumentation prerequisites

AI evaluation only processes GenAI spans containing non-empty `gen_ai.input.messages` and `gen_ai.output.messages` attributes. Spans missing either attribute are skipped as incomplete.

For Spring AI instrumented by the SkyWalking Java agent, enable message collection explicitly:

    SW_PLUGIN_SPRINGAI_COLLECT_INPUT_MESSAGES=true
    SW_PLUGIN_SPRINGAI_COLLECT_OUTPUT_MESSAGES=true

For OTLP and Zipkin ingestion, ensure the instrumentation exports the equivalent `gen_ai.input.messages` and `gen_ai.output.messages` attributes.

These attributes may contain user prompts and model responses, and the module sends their content to the configured judge endpoint. Review the endpoint's data handling, privacy requirements, and estimated model cost before enabling collection in production.

## Basic configuration

The shipped `ai-evaluation.yml` is a template. A minimal configuration is:

```yaml
judge:
  provider: openai
  endpoint: ${AI_EVALUATION_ENDPOINT:https://api.openai.com/v1/chat/completions}
  model: ${AI_EVALUATION_MODEL:gpt-4o-mini}
  api-key: ${AI_EVALUATION_API_KEY:}
  request-timeout-seconds: 30
  max-retries: 2
  temperature: 0.2
  max_tokens: 4096

system-prompt: |
  Return only valid JSON. Every requested task must contain value and reason.

tasks:
  - name: Faithfulness
    valueType: SCORE
    instruction: Evaluate factual grounding.
```

`provider` must currently be `openai`. The endpoint must accept the OpenAI Chat Completions request shape and return a response with `choices[0].message.content`. The API key is sent as a Bearer token.

`request-timeout-seconds` defaults to `30`. `max-retries` defaults to `2` and is limited to `5`; timeout, HTTP 429, and HTTP 5xx responses may be retried. `temperature` must be between `0` and `1`. `max_tokens` must be a positive integer.

## Sampling and queue settings

The module-level settings are configured in `application.yml` and can be supplied with environment variables:

| Setting | Environment variable | Default | Description |
| --- | --- | ---: | --- |
| Selector | `SW_AI_EVALUATION` | empty | Set to `default` to enable the module. |
| Sample rate | `SW_AI_EVALUATION_SAMPLE_RATE` | `1000000` | Deterministic PPM rate. `1000000` evaluates every eligible trace; `10000` evaluates about 1%. |
| Buffer size | `SW_AI_EVALUATION_BUFFER_SIZE` | `100` | Maximum queued evaluations before new tasks are dropped. |
| Consumer threads | `SW_AI_EVALUATION_CONSUMER_THREADS` | `8` | Number of evaluation consumers. Judge calls are I/O-bound. |
| Maximum content length | `SW_AI_EVALUATION_MAX_CONTENT_LENGTH` | `16384` | Maximum escaped characters included from each input or output message field. |

Sampling is based on the trace ID, so spans from the same trace are selected consistently. The queue is local to each OAP instance. Increase the buffer and consumer count only after checking judge capacity and OAP resource usage; a full queue drops evaluations rather than blocking trace ingestion.

## Evaluation tasks

Each task requires `name`, `valueType`, and `instruction`. Supported value types are:

* `SCORE`: a number from `0.0` to `1.0`. The stored metric uses parts per million, so `0.8` is stored as `800000`.
* `BOOLEAN`: `true` or `false`.
* `STRING`: a string. Use `allowedValues` to restrict the accepted values.
* `JSON`: a JSON object.

The judge must return one JSON object keyed by task name. Each task result must contain `value` and `reason`. For example:

```json
{
  "Faithfulness": {
    "value": 0.8,
    "reason": "The response is supported by the supplied context."
  }
}
```

Optional level rules can be configured under `level.score` and `level.boolean`. The level is persisted with each record. Invalid task results are skipped; a response with no valid task results is rejected.

## Query results

Evaluation records are available through the `queryGenAIEvaluationRecord` GraphQL query. Records retain the source trace reference, service, provider, model, operation, task, value, level, reason, judge model, and evaluation time. `SCORE` results are also available through the `gen_ai_model_evaluation_score_ppm` metric, grouped by Virtual GenAI service instance and task name.

The metric value is PPM. Divide it by `1000000` when displaying the original score.

## Operational considerations

Evaluation runs asynchronously after a GenAI span is sampled. A slow or unavailable judge affects evaluation throughput, not trace ingestion, but it can fill the local queue and increase dropped-evaluation counters. Monitor the `ai_evaluation_dropped_count` and `ai_evaluation_error_count` telemetry metrics when tuning the queue or judge settings.
