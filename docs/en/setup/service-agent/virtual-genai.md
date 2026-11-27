# Virtual GenAI

Virtual GenAI represents the Generative AI service nodes detected by [server agents' plugins](server-agents.md). The performance
metrics of the GenAI operations are from the GenAI client-side perspective.

For example, a Spring AI plugin in the Java agent could detect the latency of a chat completion request.
As a result, SkyWalking would show traffic, latency, success rate, token usage (input/output), and estimated cost in the GenAI dashboard.

# Data Sources
Virtual GenAI metrics are derived from distributed tracing data. SkyWalking OAP can ingest and analyze trace data adhering to GenAI semantic conventions from the following sources:

1. Native SkyWalking Traces via SkyWalking Java Agent
2. OpenTelemetry format trace 
3. Zipkin format Traces

## Span Contract

The GenAI operation span should have the following properties:
- It is an **Exit** span
- **Span's layer == GENAI**
- Tag key = `gen_ai.provider.name`, value = The Generative AI provider, e.g. openai, anthropic, ollama
- Tag key = `gen_ai.response.model`, value = The name of the GenAI model, e.g. gpt-4o, claude-3-5-sonnet
- Tag key = `gen_ai.usage.input_tokens`, value = The number of tokens used in the GenAI input (prompt)
- Tag key = `gen_ai.usage.output_tokens`, value = The number of tokens used in the GenAI response (completion)
- Tag key = `gen_ai.response.time_to_first_chunk`, value = The duration in seconds, such as `0.457`, until the first chunk of the response is received (streaming requests only). SkyWalking Java Agent 9.7 and 9.8 report `gen_ai.server.time_to_first_token` instead, in milliseconds, which is still read, see the note on time to first token under [Semantic Conventions and Compatibility](#semantic-conventions-and-compatibility)
- If the GenAI service is a remote API (e.g. OpenAI), the span's peer should be the network address (IP or domain) of the GenAI server.

## Provider Configuration

SkyWalking uses `gen-ai-config.yml` to map model names to providers and configure cost estimation.

When the `gen_ai.provider.name` tag is present in the span, it is used directly. Otherwise, SkyWalking matches the model name
against `prefix-match` rules to identify the provider. For example, a model name starting with `gpt` is mapped to `openai`.

To configure cost estimation, add `models` with pricing under the provider:


```yaml
providers:
- provider: openai
  prefix-match:
    - gpt
  models:
    - name: gpt-4o
      input-estimated-cost-per-m: 2.5    # estimated cost per 1,000,000 input tokens
      output-estimated-cost-per-m: 10    # estimated cost per 1,000,000 output tokens
```

## Metrics

The following metrics are available at the **provider** (service) level:
- `gen_ai_provider_cpm` - Calls per minute
- `gen_ai_provider_sla` - Success rate
- `gen_ai_provider_resp_time` - Average response time
- `gen_ai_provider_latency_percentile` - Latency percentiles
- `gen_ai_provider_input_tokens_sum / avg` - Input token usage
- `gen_ai_provider_output_tokens_sum / avg` - Output token usage
- `gen_ai_provider_total_estimated_cost / avg_estimated_cost` - Estimated cost

The following metrics are available at the **model** (service instance) level:
- `gen_ai_model_call_cpm` - Calls per minute
- `gen_ai_model_sla` - Success rate
- `gen_ai_model_latency_avg / percentile` - Latency
- `gen_ai_model_ttft_avg / percentile` - Time to first token (streaming only)
- `gen_ai_model_input_tokens_sum / avg` - Input token usage
- `gen_ai_model_output_tokens_sum / avg` - Output token usage
- `gen_ai_model_total_estimated_cost / avg_estimated_cost` - Estimated cost

## Requirement

### Version
`SkyWalking Java Agent` version >= 9.7

### Semantic Conventions and Compatibility
The tag keys used in Virtual GenAI follow the **OpenTelemetry GenAI Semantic Conventions**. SkyWalking OAP identifies GenAI-related spans based on the following criteria depending on the data source:

* **SkyWalking Native Agent**: Requires an **Exit** span with `SpanLayer == GENAI` and relevant `gen_ai.*` tags.
* **OTLP / Zipkin Traces**: A span is identified as a GenAI operation when it names a model and is a model call, whatever `service.layer` its resource sets, if any.

**Note on OTLP / Zipkin Span Identification**:

1. **The model**: `gen_ai.response.model`, or `gen_ai.request.model` when the span has no response model. A call that failed before the provider answered carries only the requested model, and is counted as a failed call of that model. The requested name can differ from the name the provider answers with, such as an alias and the dated version behind it, so the failed calls of a model can be listed under another model name than its successful ones.
2. **The operation**: a span whose `gen_ai.operation.name` describes no model call of its own is not counted: `fetch_response`, `create_agent`, `execute_tool`, `plan`, `retrieval`, `user_speech`, the memory operations (`search_memory`, `create_memory`, `update_memory`, `upsert_memory`, `delete_memory`, `create_memory_store`, `delete_memory_store`), and every name that starts with `invoke_`, such as `invoke_agent` and `invoke_workflow`. These spans can repeat the model and the token usage of the model calls made under them, or of a call made earlier, so counting them would count the same tokens twice. A span with any other operation name, or with none, is counted.

**Note on Time to First Token**:
The time to first token is read from `gen_ai.response.time_to_first_chunk`, the span attribute the OpenTelemetry GenAI semantic conventions define for it, a number of seconds, and reported in milliseconds. It is read the same way from the spans of a SkyWalking agent and from OTLP and Zipkin spans.

A span without `gen_ai.response.time_to_first_chunk` is read for `gen_ai.server.time_to_first_token`, in milliseconds. SkyWalking Java Agent 9.7 and 9.8 report the time to first token under that tag, and so do OTLP and Zipkin producers that followed the earlier version of this page. The OpenTelemetry GenAI semantic conventions give that name to a metric, not to a span attribute, so a new producer should report `gen_ai.response.time_to_first_chunk`. When a span carries both, `gen_ai.response.time_to_first_chunk` is used.

**Note on OTLP / Zipkin Provider Identification**:
To ensure broad compatibility with different OpenTelemetry instrumentation versions, SkyWalking OAP identifies the GenAI provider using the following prioritized logic:

1. **`gen_ai.provider.name`**: SkyWalking first looks for this tag (the latest OTel semantic convention).
2. **`gen_ai.system`**: If the above is missing, it falls back to this legacy tag for backward compatibility with older instrumentation (e.g., current OTel Python auto-instrumentation).
3. **Prefix Matching**: If neither tag is present, SkyWalking attempts to identify the provider by matching the model name against the `prefix-match` rules defined in the `gen-ai-config.yml`.

**Note on AI evaluation of OTLP spans**:
[AI evaluation](../backend/ai-evaluation.md) of OTLP spans stored natively (`otlpTraceStorage: otlp`, the default) is made by the `otlp-ai-evaluation` TAL rule. A TAL rule sees only the spans of the layer it is declared for, and this one is declared for `OTLP_GENERAL`, so it sees only the spans of services whose resource sets `service.layer=OTLP_GENERAL`. A GenAI application whose spans should be evaluated sets it, for example `OTEL_RESOURCE_ATTRIBUTES=service.layer=OTLP_GENERAL`. The Virtual GenAI metrics do not need it, and neither does the evaluation of Zipkin spans or of OTLP spans stored as Zipkin spans.
