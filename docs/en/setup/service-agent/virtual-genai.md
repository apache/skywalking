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
- Tag key = `gen_ai.server.time_to_first_token`, value = The duration in milliseconds until the first token is received (streaming requests only)
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
* **OTLP / Zipkin Traces**: Any span containing the `gen_ai.response.model` tag will be identified as a GenAI operation.

**Note on OTLP / Zipkin Provider Identification**:
To ensure broad compatibility with different OpenTelemetry instrumentation versions, SkyWalking OAP identifies the GenAI provider using the following prioritized logic:

1. **`gen_ai.provider.name`**: SkyWalking first looks for this tag (the latest OTel semantic convention).
2. **`gen_ai.system`**: If the above is missing, it falls back to this legacy tag for backward compatibility with older instrumentation (e.g., current OTel Python auto-instrumentation).
3. **Prefix Matching**: If neither tag is present, SkyWalking attempts to identify the provider by matching the model name against the `prefix-match` rules defined in the `gen-ai-config.yml`.