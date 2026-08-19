# Virtual GenAI AI Evaluation E2E Design

## Scope

Extend the existing `virtual-genai` E2E case to verify the complete AI evaluation
data path for the Java agent Virtual GenAI telemetry. The OTLP and Zipkin Virtual
GenAI cases are out of scope.

The case must reuse the existing mock controller used by the Virtual GenAI test
environment. It must not call an external judge model.

## Deterministic Judge Response

The OAP AI evaluation configuration for this case defines a small, fixed task set:

| Task | Value type | Mock value | Expected level |
| --- | --- | --- | --- |
| `Faithfulness` | `SCORE` | `0.8` | `good` |
| `TaskCompletion` | `BOOLEAN` | `true` | `excellent` |

The mock controller returns the results for all configured tasks in a single
OpenAI-compatible completion response. This matches the production execution
model: OAP builds one evaluation plan and calls the judge once for the plan; it
then parses the individual task results and persists one record per result.

The mock response is served by the existing `LLMMockController` at
`/llm/evaluation/v1/chat/completions`. It returns a non-streaming
OpenAI-compatible response whose `message.content` contains the task-result JSON
consumed by `EvaluationResultParser`.

## Test Environment Changes

Update `docker-compose.yml` in this case to configure the OAP container with:

- AI evaluation enabled and sampling set to `1000000`.
- The existing mock controller as the judge endpoint.
- A deterministic judge model name, for example `e2e-judge`.
- A non-empty mock API key because the OpenAI-compatible judge provider requires
  one during configuration validation.

Add a case-local AI evaluation configuration with only the two tasks above. This
keeps the result count deterministic and allows the assertions to cover the two
numeric storage semantics without depending on the default configuration.

The existing HTTP trigger remains unchanged. It already produces a Virtual GenAI
`chat` span with input and output messages, which is the prerequisite for
`SpanAIEvaluationStrategy` to evaluate the span.

## Metric Verification

Append a query in `virtual-genai.yaml` using `swctl metrics exec`:

```text
--expression=gen_ai_model_evaluation_score_ppm
--service-id=b3BlbmFp.0
--instance-name=gpt-4.1-mini-2025-04-14
```

The expected output must contain a non-empty time-series value for the
`Faithfulness` task with value `800000`.

`gen_ai_model_evaluation_score_ppm` is intentionally asserted in its stored PPM
representation. The evaluation reporter scales the score by `1,000,000` before
it enters MAL, so an input score of `0.8` becomes `800000`.

There must be no boolean metric assertion. Boolean task results are persisted as
records only; `AIEvaluationMetricReporter` is invoked only for `SCORE` results.

## Record Query Verification

Add raw GraphQL E2E queries using the same `curl | yq -P` pattern as other E2E
cases. Both queries use a time window ending at execution time, pagination, and
the Virtual GenAI provider/service identifiers.

### Score Record

Query `queryGenAIEvaluationRecord` with:

```json
{
  "valueType": "SCORE",
  "taskName": "Faithfulness",
  "minScore": 800000,
  "maxScore": 800000,
  "sortBy": "SCORE_VALUE"
}
```

The expected YAML must assert a returned record with:

- `valueType: SCORE`
- `taskName: Faithfulness`
- `scoreValue: 800000`
- `booleanValue: null`
- `stringValue: null`
- `evaluationLevel: good`
- `reason` equal to the fixed mock reason
- `judgeModel: e2e-judge`
- non-empty `traceId`, plus the expected provider/model/operation fields

This verifies that SCORE uses `evaNumberValue`, that GraphQL exposes the raw
long value, and that score range filters apply only to score records.

### Boolean Record

Query `queryGenAIEvaluationRecord` with:

```json
{
  "valueType": "BOOLEAN",
  "taskName": "TaskCompletion",
  "booleanValue": true
}
```

The expected YAML must assert:

- `valueType: BOOLEAN`
- `taskName: TaskCompletion`
- `scoreValue: null`
- `booleanValue: true`
- `stringValue: null`
- `evaluationLevel: excellent`
- the fixed mock reason and judge model

The score query must not return the boolean record even though both values use
`evaNumberValue`. The boolean query must not depend on `minScore` or `maxScore`.
Together, the two queries verify type-first filtering in the query service and
all storage implementations.

## Retry and Stability

Keep the existing case-level retry (`60` attempts with a `3s` interval). AI
evaluation, record persistence, metric aggregation, and storage visibility are
asynchronous. The expected files should use `contains` assertions for dynamic
trace identifiers and timestamps, while asserting exact values only for the
fixed mock output and its derived PPM values.

## Files To Change During Implementation

- `test/e2e-v2/cases/virtual-genai/docker-compose.yml`
- `test/e2e-v2/cases/virtual-genai/ai-evaluation.yml`
- `test/e2e-v2/cases/virtual-genai/virtual-genai.yaml`
- `test/e2e-v2/cases/virtual-genai/expected/` for one metric expectation and
  two record-query expectations
- `test/e2e-v2/java-test-service/e2e-service-provider/.../LLMMockController.java`
  for the deterministic all-task judge response
