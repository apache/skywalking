# Envoy AI Gateway Monitoring

## Envoy AI Gateway observability via OTLP

[Envoy AI Gateway](https://aigateway.envoyproxy.io/) is a gateway/proxy for AI/LLM API traffic
(OpenAI, Anthropic, AWS Bedrock, Azure OpenAI, Google Gemini, etc.) built on top of Envoy Proxy.
It natively emits GenAI metrics following
[OpenTelemetry GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/),
and also emits MCP (Model Context Protocol) metrics and access logs via OTLP.

SkyWalking receives OTLP metrics and logs directly on its gRPC port (11800) — no OpenTelemetry
Collector is needed between the AI Gateway and SkyWalking OAP.

### Prerequisites
- [Envoy AI Gateway](https://aigateway.envoyproxy.io/) deployed. See the
  [Envoy AI Gateway getting started](https://aigateway.envoyproxy.io/docs/getting-started/) for installation.

### Data flow
1. Envoy AI Gateway processes LLM API requests and MCP requests, recording GenAI metrics and MCP metrics.
2. The AI Gateway pushes metrics and access logs via OTLP gRPC to SkyWalking OAP.
3. SkyWalking OAP parses metrics with [MAL](../../concepts-and-designs/mal.md) rules and access logs
   with [LAL](../../concepts-and-designs/lal.md) rules.

### Set up

The MAL rules (`envoy-ai-gateway/*`) and LAL rules (`envoy-ai-gateway`) are enabled by default
in SkyWalking OAP. No OAP-side configuration is needed.

Configure the AI Gateway to push OTLP to SkyWalking by setting these environment variables:

| Env Var                       | Value                                               | Purpose                      |
|-------------------------------|-----------------------------------------------------|------------------------------|
| `OTEL_SERVICE_NAME`           | Per-deployment gateway name (e.g., `my-ai-gateway`) | SkyWalking service name      |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://skywalking-oap:11800`                       | SkyWalking OAP gRPC receiver |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc`                                              | OTLP transport               |
| `OTEL_METRICS_EXPORTER`       | `otlp`                                              | Enable OTLP metrics push     |
| `OTEL_LOGS_EXPORTER`          | `otlp`                                              | Enable OTLP access log push  |
| `OTEL_RESOURCE_ATTRIBUTES`    | See below                                           | Routing + instance + layer   |

**Required resource attributes** (in `OTEL_RESOURCE_ATTRIBUTES`):
- `job_name=envoy-ai-gateway` — Fixed routing tag for MAL/LAL rules. Same for all AI Gateway deployments.
- `service.instance.id=<instance-id>` — Instance identity. In Kubernetes, use the pod name via Downward API.
- `service.layer=ENVOY_AI_GATEWAY` — Routes access logs to the AI Gateway LAL rules.

**Example:**
```bash
OTEL_SERVICE_NAME=my-ai-gateway
OTEL_EXPORTER_OTLP_ENDPOINT=http://skywalking-oap:11800
OTEL_EXPORTER_OTLP_PROTOCOL=grpc
OTEL_METRICS_EXPORTER=otlp
OTEL_LOGS_EXPORTER=otlp
OTEL_RESOURCE_ATTRIBUTES=job_name=envoy-ai-gateway,service.instance.id=pod-abc123,service.layer=ENVOY_AI_GATEWAY
```

### Supported Metrics

SkyWalking observes the AI Gateway as a `LAYER: ENVOY_AI_GATEWAY` service. Each gateway deployment
is a service, each pod is an instance. Metrics include per-provider and per-model breakdowns.

#### Service Metrics

| Monitoring Panel           | Unit       | Metric Name                                  | Description                            |
|----------------------------|------------|----------------------------------------------|----------------------------------------|
| Request CPM                | calls/min  | meter_envoy_ai_gw_request_cpm                | Requests per minute                    |
| Request Latency Avg        | ms         | meter_envoy_ai_gw_request_latency_avg        | Average request duration               |
| Request Latency Percentile | ms         | meter_envoy_ai_gw_request_latency_percentile | P50/P75/P90/P95/P99                    |
| Input Token Rate           | tokens/min | meter_envoy_ai_gw_input_token_rate           | Input (prompt) tokens per minute       |
| Output Token Rate          | tokens/min | meter_envoy_ai_gw_output_token_rate          | Output (completion) tokens per minute  |
| TTFT Avg                   | ms         | meter_envoy_ai_gw_ttft_avg                   | Time to First Token (streaming only)   |
| TTFT Percentile            | ms         | meter_envoy_ai_gw_ttft_percentile            | P50/P75/P90/P95/P99 TTFT               |
| TPOT Avg                   | ms         | meter_envoy_ai_gw_tpot_avg                   | Time Per Output Token (streaming only) |
| TPOT Percentile            | ms         | meter_envoy_ai_gw_tpot_percentile            | P50/P75/P90/P95/P99 TPOT               |

#### Provider Breakdown Metrics

| Monitoring Panel     | Unit       | Metric Name                            | Description            |
|----------------------|------------|----------------------------------------|------------------------|
| Provider Request CPM | calls/min  | meter_envoy_ai_gw_provider_request_cpm | Requests by provider   |
| Provider Token Rate  | tokens/min | meter_envoy_ai_gw_provider_token_rate  | Token rate by provider |
| Provider Latency Avg | ms         | meter_envoy_ai_gw_provider_latency_avg | Latency by provider    |

#### Model Breakdown Metrics

| Monitoring Panel  | Unit       | Metric Name                         | Description         |
|-------------------|------------|-------------------------------------|---------------------|
| Model Request CPM | calls/min  | meter_envoy_ai_gw_model_request_cpm | Requests by model   |
| Model Token Rate  | tokens/min | meter_envoy_ai_gw_model_token_rate  | Token rate by model |
| Model Latency Avg | ms         | meter_envoy_ai_gw_model_latency_avg | Latency by model    |
| Model TTFT Avg    | ms         | meter_envoy_ai_gw_model_ttft_avg    | TTFT by model       |
| Model TPOT Avg    | ms         | meter_envoy_ai_gw_model_tpot_avg    | TPOT by model       |

#### Instance Metrics

All service-level metrics are also available per instance (pod) with `meter_envoy_ai_gw_instance_` prefix,
including per-provider and per-model breakdowns.

### MCP Metrics

When the AI Gateway is configured with MCP (Model Context Protocol) routes, SkyWalking collects
MCP-specific metrics. These appear in the **MCP** tab on the service and instance dashboards.

#### MCP Service Metrics

| Monitoring Panel                      | Unit      | Metric Name                                             | Description                                                       |
|---------------------------------------|-----------|---------------------------------------------------------|-------------------------------------------------------------------|
| MCP Request CPM                       | calls/min | meter_envoy_ai_gw_mcp_request_cpm                       | MCP requests per minute                                           |
| MCP Request Latency Avg               | ms        | meter_envoy_ai_gw_mcp_request_latency_avg               | Average MCP request duration                                      |
| MCP Request Latency Percentile        | ms        | meter_envoy_ai_gw_mcp_request_latency_percentile        | P50/P75/P90/P95/P99                                               |
| MCP Method CPM                        | calls/min | meter_envoy_ai_gw_mcp_method_cpm                        | Requests by MCP method (initialize, tools/list, tools/call, etc.) |
| MCP Error CPM                         | calls/min | meter_envoy_ai_gw_mcp_error_cpm                         | MCP error requests per minute                                     |
| MCP Initialization Latency Avg        | ms        | meter_envoy_ai_gw_mcp_initialization_latency_avg        | Average MCP session initialization time                           |
| MCP Initialization Latency Percentile | ms        | meter_envoy_ai_gw_mcp_initialization_latency_percentile | P50/P75/P90/P95/P99                                               |
| MCP Capabilities CPM                  | calls/min | meter_envoy_ai_gw_mcp_capabilities_cpm                  | Capabilities negotiated by type                                   |

#### MCP Backend Breakdown Metrics

| Monitoring Panel         | Unit      | Metric Name                                              | Description                    |
|--------------------------|-----------|----------------------------------------------------------|--------------------------------|
| Backend Request CPM      | calls/min | meter_envoy_ai_gw_mcp_backend_request_cpm                | Requests by MCP backend        |
| Backend Latency Avg      | ms        | meter_envoy_ai_gw_mcp_backend_request_latency_avg        | Latency by MCP backend         |
| Backend Method CPM       | calls/min | meter_envoy_ai_gw_mcp_backend_method_cpm                 | Requests by backend and method |
| Backend Error CPM        | calls/min | meter_envoy_ai_gw_mcp_backend_error_cpm                  | Errors by MCP backend          |
| Backend Init Latency Avg | ms        | meter_envoy_ai_gw_mcp_backend_initialization_latency_avg | Init latency by backend        |

#### MCP Instance Metrics

All MCP service-level metrics are also available per instance with `meter_envoy_ai_gw_mcp_instance_` prefix.

### Access Log Sampling

Access logs are tagged with `ai_route_type` (`llm` or `mcp`) for filtering in the log query UI.
The `ai_route_type` tag is searchable by default.

**LLM route logs:**
- **Error responses** (HTTP status >= 400) — always persisted.
- **Upstream failures** — always persisted.
- Normal successful responses are dropped.

**MCP route logs:**
- **Error responses** (HTTP status >= 400) — always persisted.
- Normal MCP requests are dropped (MCP observability is covered by metrics).

The sampling policy can be adjusted in `lal/envoy-ai-gateway.yaml`.
