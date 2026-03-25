# SWIP-10 Support Envoy AI Gateway Observability

## Motivation
[Envoy AI Gateway](https://aigateway.envoyproxy.io/) is a gateway/proxy for AI/LLM API traffic (OpenAI, Anthropic,
AWS Bedrock, Azure OpenAI, Google Gemini, etc.) built on top of Envoy Proxy. It provides GenAI-specific observability
following [OpenTelemetry GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/), including
token usage tracking, request latency, time-to-first-token (TTFT), and inter-token latency.

SkyWalking should support monitoring Envoy AI Gateway as a first-class integration, providing:
1. **Metrics monitoring** via OTLP push for GenAI metrics.
2. **Access log collection** via OTLP log sink for per-request AI metadata analysis.

This is complementary to [PR #13745](https://github.com/apache/skywalking/pull/13745) (agent-based Virtual GenAI
monitoring). The agent-based approach monitors LLM calls from the client application side, while this SWIP monitors
from the gateway (infrastructure) side. Both can coexist — the AI Gateway provides infrastructure-level visibility
regardless of whether the calling application is instrumented.

## Architecture Graph

### Metrics Path (OTLP Push)
```
┌─────────────────┐      OTLP gRPC       ┌─────────────────┐
│  Envoy AI       │  ──────────────────>  │  SkyWalking OAP │
│  Gateway        │   (push, port 11800)   │  (otel-receiver) │
│                 │                       │                 │
│  4 GenAI metrics│                       │  MAL rules      │
│  + labels       │                       │  → aggregation  │
└─────────────────┘                       └─────────────────┘
```

### Access Log Path (OTLP Push)
```
┌─────────────────┐      OTLP gRPC       ┌─────────────────┐
│  Envoy AI       │  ──────────────────>  │  SkyWalking OAP │
│  Gateway        │   (push, port 11800)   │  (otel-receiver) │
│                 │                       │                 │
│  access logs    │                       │  LAL rules      │
│  with AI meta   │                       │  → analysis     │
└─────────────────┘                       └─────────────────┘
```
The AI Gateway natively supports an OTLP access log sink (via Envoy Gateway's OpenTelemetry sink),
pushing structured access logs directly to the OAP's OTLP receiver. No FluentBit or external log
collector is needed.

## Proposed Changes

### 1. New Layer: `ENVOY_AI_GATEWAY`

Add a new layer in `Layer.java`:
```java
/**
 * Envoy AI Gateway is an AI/LLM traffic gateway built on Envoy Proxy,
 * providing observability for GenAI API traffic.
 */
ENVOY_AI_GATEWAY(46, true),
```

This is a **normal** layer (`isNormal=true`) because the AI Gateway is a real, instrumented infrastructure component
(similar to `KONG`, `APISIX`, `NGINX`), not a virtual/conjectured service.

### 2. Entity Model

#### `job_name` — Routing Tag for MAL/LAL Rules

SkyWalking's OTel receiver maps the OTLP resource attribute `service.name` to the internal tag `job_name`.
This tag is used by MAL rule filters to route metrics to the correct rule set. All Envoy AI Gateway
deployments must use a fixed `OTEL_SERVICE_NAME` value so that SkyWalking can identify the traffic:

```bash
OTEL_SERVICE_NAME=envoy-ai-gateway
```

This becomes `job_name=envoy-ai-gateway` in MAL, and the rules filter on it:
```yaml
filter: "{ tags -> tags.job_name == 'envoy-ai-gateway' }"
```

`job_name` is NOT the SkyWalking service name — it is only used for metric/log routing.

#### Service and Instance Mapping

| SkyWalking Entity | Source | Example |
|---|---|---|
| **Service** | `aigw.service` resource attribute (K8s Deployment/Service name, set via CRD) | `envoy-ai-gateway-basic` |
| **Service Instance** | `service.instance.id` resource attribute (pod name, set via CRD + Downward API) | `aigw-pod-7b9f4d8c5` |

Each Kubernetes Gateway deployment is a separate SkyWalking **service**. Each pod (ext_proc replica) is a
**service instance**. Neither attribute is emitted by the AI Gateway by default — both must be explicitly
set via `OTEL_RESOURCE_ATTRIBUTES` in the `GatewayConfig` CRD (see below).

The **layer** (`ENVOY_AI_GATEWAY`) is set by MAL/LAL rules based on the `job_name` filter, not by the
client. This follows the same pattern as other SkyWalking OTel integrations (e.g., ActiveMQ, K8s).

Provider and model are **metric-level labels**, not separate entities in this layer. They are used for
fine-grained metric breakdowns within the gateway service dashboards rather than being modeled as separate
services (unlike the agent-based `VIRTUAL_GENAI` layer where provider=service, model=instance).

The MAL `expSuffix` uses the `aigw_service` tag (dots converted to underscores by OTel receiver) as the
SkyWalking service name and `service_instance_id` as the instance name:
```yaml
expSuffix: service(['aigw_service'], Layer.ENVOY_AI_GATEWAY).instance(['aigw_service', 'service_instance_id'])
```

#### Complete Kubernetes Setup Example

The following example shows a complete Envoy AI Gateway deployment configured for SkyWalking
observability via OTLP metrics and access logs.

```yaml
# 1. GatewayClass — standard Envoy Gateway controller
apiVersion: gateway.networking.k8s.io/v1
kind: GatewayClass
metadata:
  name: envoy-ai-gateway
spec:
  controllerName: gateway.envoyproxy.io/gatewayclass-controller
---
# 2. GatewayConfig — OTLP configuration for SkyWalking
#    One GatewayConfig per gateway. Sets job_name, service name, instance ID,
#    and enables OTLP push for both metrics and access logs.
apiVersion: aigateway.envoyproxy.io/v1alpha1
kind: GatewayConfig
metadata:
  name: my-gateway-config
  namespace: default
spec:
  extProc:
    kubernetes:
      env:
        # job_name — fixed value for MAL/LAL rule routing (same for ALL AI Gateway deployments)
        - name: OTEL_SERVICE_NAME
          value: "envoy-ai-gateway"
        # OTLP endpoint — SkyWalking OAP gRPC receiver
        - name: OTEL_EXPORTER_OTLP_ENDPOINT
          value: "http://skywalking-oap.skywalking:11800"
        - name: OTEL_EXPORTER_OTLP_PROTOCOL
          value: "grpc"
        # Enable OTLP for both metrics and access logs
        - name: OTEL_METRICS_EXPORTER
          value: "otlp"
        - name: OTEL_LOGS_EXPORTER
          value: "otlp"
        # Gateway name = Gateway CRD metadata.name (e.g., "my-ai-gateway")
        # Read from pod label gateway.envoyproxy.io/owning-gateway-name,
        # which is auto-set by the Envoy Gateway controller on every envoy pod.
        - name: GATEWAY_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.labels['gateway.envoyproxy.io/owning-gateway-name']
        # Pod name (e.g., "envoy-default-my-ai-gateway-76d02f2b-xxx")
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        # aigw.service → SkyWalking service name (= Gateway CRD name, auto-resolved)
        # service.instance.id → SkyWalking instance name (= pod name, auto-resolved)
        # $(VAR) substitution references the valueFrom env vars defined above.
        - name: OTEL_RESOURCE_ATTRIBUTES
          value: "aigw.service=$(GATEWAY_NAME),service.instance.id=$(POD_NAME)"
---
# 3. Gateway — references the GatewayConfig via annotation
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: my-ai-gateway
  namespace: default
  annotations:
    aigateway.envoyproxy.io/gateway-config: my-gateway-config
spec:
  gatewayClassName: envoy-ai-gateway
  listeners:
    - name: http
      protocol: HTTP
      port: 80
---
# 4. AIGatewayRoute — routing rules + token metadata for access logs
apiVersion: aigateway.envoyproxy.io/v1alpha1
kind: AIGatewayRoute
metadata:
  name: my-ai-gateway-route
  namespace: default
spec:
  parentRefs:
    - name: my-ai-gateway
      kind: Gateway
      group: gateway.networking.k8s.io
  # Enable token counts in access logs
  llmRequestCosts:
    - metadataKey: llm_input_token
      type: InputToken
    - metadataKey: llm_output_token
      type: OutputToken
    - metadataKey: llm_total_token
      type: TotalToken
  # Route all models to the backend
  rules:
    - backendRefs:
        - name: openai-backend
---
# 5. AIServiceBackend + Backend — LLM provider
apiVersion: aigateway.envoyproxy.io/v1alpha1
kind: AIServiceBackend
metadata:
  name: openai-backend
  namespace: default
spec:
  schema:
    name: OpenAI
  backendRef:
    name: openai-backend
    kind: Backend
    group: gateway.envoyproxy.io
---
apiVersion: gateway.envoyproxy.io/v1alpha1
kind: Backend
metadata:
  name: openai-backend
  namespace: default
spec:
  endpoints:
    - fqdn:
        hostname: api.openai.com
        port: 443
```

**Key env var mapping:**

| Env Var / Resource Attribute | SkyWalking Concept | Example Value |
|---|---|---|
| `OTEL_SERVICE_NAME` | `job_name` (MAL/LAL rule routing) | `envoy-ai-gateway` (fixed for all deployments) |
| `aigw.service` | Service name | `my-ai-gateway` (auto-resolved from gateway name label) |
| `service.instance.id` | Instance name | `envoy-default-my-ai-gateway-...` (auto-resolved from pod name) |

**No manual per-gateway configuration needed** for service and instance names:
- `GATEWAY_NAME` is auto-resolved from the pod label `gateway.envoyproxy.io/owning-gateway-name`,
  which is set automatically by the Envoy Gateway controller on every envoy pod.
- `POD_NAME` is auto-resolved from the pod name via the Downward API.
- Both are injected into `OTEL_RESOURCE_ATTRIBUTES` via standard Kubernetes `$(VAR)` substitution.

The `GatewayConfig.spec.extProc.kubernetes.env` field accepts full `corev1.EnvVar` objects (including
`valueFrom`), merged into the ext_proc container by the gateway mutator webhook. Verified on Kind
cluster — the gateway label resolves correctly (e.g., `my-ai-gateway`).

**Important:** The `resource.WithFromEnv()` code path in the AI Gateway (`internal/metrics/metrics.go`)
is conditional — it only executes when `OTEL_EXPORTER_OTLP_ENDPOINT` is set (or `OTEL_METRICS_EXPORTER=console`).
The ext_proc runs in-process (not as a subprocess), so there is no env var propagation issue.

### 3. MAL Rules for OTLP Metrics

Create `oap-server/server-starter/src/main/resources/otel-rules/envoy-ai-gateway/` with MAL rules consuming
the 4 GenAI metrics from Envoy AI Gateway.

All MAL rule files use the `job_name` filter to match only AI Gateway traffic:
```yaml
filter: "{ tags -> tags.job_name == 'envoy-ai-gateway' }"
```

#### Source Metrics from AI Gateway

| Metric | Type | Labels |
|---|---|---|
| `gen_ai_client_token_usage` | Histogram (Delta) | `gen_ai.token.type` (input/output), `gen_ai.provider.name`, `gen_ai.response.model`, `gen_ai.operation.name` |
| `gen_ai_server_request_duration` | Histogram | `gen_ai.provider.name`, `gen_ai.response.model`, `gen_ai.operation.name` |
| `gen_ai_server_time_to_first_token` | Histogram | `gen_ai.provider.name`, `gen_ai.response.model`, `gen_ai.operation.name` |
| `gen_ai_server_time_per_output_token` | Histogram | `gen_ai.provider.name`, `gen_ai.response.model`, `gen_ai.operation.name` |

#### Proposed SkyWalking Metrics

**Gateway-level (Service) metrics:**

| Monitoring Panel | Unit | Metric Name | Description |
|---|---|---|---|
| Request CPM | count/min | `meter_envoy_ai_gw_request_cpm` | Requests per minute |
| Request Latency Avg | ms | `meter_envoy_ai_gw_request_latency_avg` | Average request duration |
| Request Latency Percentile | ms | `meter_envoy_ai_gw_request_latency_percentile` | P50/P75/P90/P95/P99 request duration |
| Input Tokens Rate | tokens/min | `meter_envoy_ai_gw_input_token_rate` | Input tokens per minute (total across all models) |
| Output Tokens Rate | tokens/min | `meter_envoy_ai_gw_output_token_rate` | Output tokens per minute (total across all models) |
| Total Tokens Rate | tokens/min | `meter_envoy_ai_gw_total_token_rate` | Total tokens per minute |
| TTFT Avg | ms | `meter_envoy_ai_gw_ttft_avg` | Average time to first token |
| TTFT Percentile | ms | `meter_envoy_ai_gw_ttft_percentile` | P50/P75/P90/P95/P99 time to first token |
| Time Per Output Token Avg | ms | `meter_envoy_ai_gw_tpot_avg` | Average inter-token latency |
| Time Per Output Token Percentile | ms | `meter_envoy_ai_gw_tpot_percentile` | P50/P75/P90/P95/P99 inter-token latency |
| Estimated Cost | cost/min | `meter_envoy_ai_gw_estimated_cost` | Estimated cost per minute (from token counts × config pricing) |

**Per-provider breakdown metrics (labeled, within gateway service):**

| Monitoring Panel | Unit | Metric Name | Description |
|---|---|---|---|
| Provider Request CPM | count/min | `meter_envoy_ai_gw_provider_request_cpm` | Requests per minute by provider |
| Provider Token Usage | tokens/min | `meter_envoy_ai_gw_provider_token_rate` | Token rate by provider and token type |
| Provider Latency Avg | ms | `meter_envoy_ai_gw_provider_latency_avg` | Average latency by provider |

**Per-model breakdown metrics (labeled, within gateway service):**

| Monitoring Panel | Unit | Metric Name | Description |
|---|---|---|---|
| Model Request CPM | count/min | `meter_envoy_ai_gw_model_request_cpm` | Requests per minute by model |
| Model Token Usage | tokens/min | `meter_envoy_ai_gw_model_token_rate` | Token rate by model and token type |
| Model Latency Avg | ms | `meter_envoy_ai_gw_model_latency_avg` | Average latency by model |
| Model TTFT Avg | ms | `meter_envoy_ai_gw_model_ttft_avg` | Average TTFT by model |
| Model TPOT Avg | ms | `meter_envoy_ai_gw_model_tpot_avg` | Average inter-token latency by model |

#### Cost Estimation

Reuse the same `gen-ai-config.yml` pricing configuration from PR #13745. The MAL rules will:
1. Keep total token counts (input + output) per model from `gen_ai_client_token_usage`.
2. Look up per-million-token pricing from config.
3. Compute `estimated_cost = input_tokens × input_cost_per_m / 1_000_000 + output_tokens × output_cost_per_m / 1_000_000`.
4. Amplify by 10^6 (same as PR #13745) to avoid floating point precision issues.

No new MAL function is needed — standard arithmetic operations on counters/gauges are sufficient.

#### Metrics vs Access Logs for Token Cost

Both data sources provide token counts, but serve different cost analysis purposes:

| Aspect | OTLP Metrics (MAL) | Access Logs (LAL) |
|---|---|---|
| **Granularity** | Aggregated counters — token sums over time windows | Per-request — exact token count for each individual call |
| **Cost output** | Cost **rate** (e.g., $X/minute) — good for trends and capacity planning | Cost **per request** (e.g., this call cost $0.03) — good for attribution and audit |
| **Precision** | Approximate (counter deltas over scrape intervals) | Exact (individual request values) |
| **Use case** | Dashboard trends, billing estimates, provider comparison | Detect expensive individual requests, cost anomaly alerting, per-user/per-session attribution |

The metrics path provides aggregated cost trends. The access log path enables per-request cost
analysis — for example, alerting on a single request that consumed an unusually large number of tokens
(e.g., a runaway prompt). Both paths reuse the same `gen-ai-config.yml` pricing data.

### 4. Access Log Collection via OTLP

The AI Gateway natively supports an OTLP access log sink. When `OTEL_LOGS_EXPORTER=otlp` (or defaulting
to OTLP when `OTEL_EXPORTER_OTLP_ENDPOINT` is set), Envoy pushes structured access logs directly via
OTLP gRPC to the same endpoint as metrics. No FluentBit or external log collector is needed.

#### AI Gateway Configuration

The OTLP log sink shares the same `GatewayConfig` CRD env vars as metrics (see Section 2).
`OTEL_LOGS_EXPORTER=otlp` and `OTEL_EXPORTER_OTLP_ENDPOINT` enable the log sink. The
`OTEL_RESOURCE_ATTRIBUTES` (including `aigw.service` and `service.instance.id`) are injected as
resource attributes on each OTLP log record, ensuring consistency between metrics and access logs.

Additionally, enable token metadata population in `AIGatewayRoute` so token counts appear in access logs:
```yaml
apiVersion: aigateway.envoyproxy.io/v1alpha1
kind: AIGatewayRoute
spec:
  llmRequestCosts:
    - metadataKey: llm_input_token
      type: InputToken
    - metadataKey: llm_output_token
      type: OutputToken
    - metadataKey: llm_total_token
      type: TotalToken
```

#### OTLP Log Record Structure (Verified)

Each access log record is pushed as an OTLP LogRecord with the following structure:

**Resource attributes** (from `OTEL_RESOURCE_ATTRIBUTES` + Envoy metadata):

| Attribute | Example | Notes |
|---|---|---|
| `aigw.service` | `envoy-ai-gateway-basic` | From `OTEL_RESOURCE_ATTRIBUTES` — SkyWalking service name |
| `service.instance.id` | `aigw-pod-7b9f4d8c5` | From `OTEL_RESOURCE_ATTRIBUTES` — SkyWalking instance name |
| `service.name` | `envoy-ai-gateway` | From `OTEL_SERVICE_NAME` — mapped to `job_name` for rule routing |
| `node_name` | `default-aigw-run-85f8cf28` | Envoy node identifier |
| `cluster_name` | `default/aigw-run` | Envoy cluster name |

**Log record attributes** (per-request, LLM traffic):

| Attribute | Example | Description |
|---|---|---|
| `gen_ai.request.model` | `llama3.2:latest` | Original requested model |
| `gen_ai.response.model` | `llama3.2:latest` | Actual model from response |
| `gen_ai.provider.name` | `openai` | Backend provider name |
| `gen_ai.usage.input_tokens` | `31` | Input token count |
| `gen_ai.usage.output_tokens` | `4` | Output token count |
| `session.id` | `sess-abc123` | Session identifier (if set via header mapping) |
| `response_code` | `200` | HTTP status code |
| `duration` | `1835` | Request duration (ms) |
| `request.path` | `/v1/chat/completions` | API path |
| `connection_termination_details` | `-` | Envoy connection termination reason |
| `upstream_transport_failure_reason` | `-` | Upstream failure reason |

Note: `total_tokens` is not a separate field in the OTLP log — it equals `input_tokens + output_tokens`
and can be computed in LAL rules. `connection_termination_details` and `upstream_transport_failure_reason`
serve as error/timeout indicators (replacing `response_flags` from the file-based log format).

**Log record attributes** (per-request, MCP traffic):

| Attribute | Example | Description |
|---|---|---|
| `mcp.method.name` | `tools/call` | MCP method name |
| `mcp.provider.name` | `kiwi` | MCP provider identifier |
| `jsonrpc.request.id` | `1` | JSON-RPC request ID |
| `mcp.session.id` | `sess-xyz` | MCP session ID |

#### LAL Rules — Sampling Policy

Create `oap-server/server-starter/src/main/resources/lal/envoy-ai-gateway.yaml` to process the OTLP
access logs.

**Sampling strategy:** Not all access logs need to be stored — only those that indicate abnormal or
expensive requests. The LAL rules apply the following sampling policy:

1. **High token cost** — persist logs where `input_tokens + output_tokens >= threshold` (default 10,000).
2. **Error responses** — always persist logs with `response_code >= 400`.
3. **Slow/timeout requests** — always persist logs where `duration` exceeds a configurable timeout
   threshold, or where `connection_termination_details` / `upstream_transport_failure_reason` indicate
   upstream failures. LLM requests are inherently slow (especially streaming), so timeout sampling is
   important for diagnosing provider availability issues.

All other access logs are dropped to avoid storage bloat.

**Industry token usage reference** (from [OpenRouter State of AI 2025](https://openrouter.ai/state-of-ai),
100 trillion token study):

| Use Case | Avg Input Tokens | Avg Output Tokens | Avg Total |
|---|---|---|---|
| Simple chat/Q&A | 500–1,000 | 200–400 | ~1,000 |
| Customer support | 500–3,000 | 300–400 | ~2,500 |
| RAG applications | 3,000–4,000 | 300–500 | ~3,500 |
| Programming/code | 6,000–20,000+ | 400–1,500 | ~10,000+ |
| Overall average (2025) | ~6,000 | ~400 | ~6,400 |

Note: The overall average is heavily skewed by programming workloads. Non-programming use cases
(chat, RAG, support) typically fall in the 1,000–3,500 total token range.

**Default sampling threshold: 10,000 total tokens** (configurable). This is approximately 3× the
non-programming median (~3,000), which captures genuinely expensive or abnormal requests without
logging every routine call. The threshold is configurable to accommodate different workload profiles:
- Lower (e.g., 5,000) for chat-heavy deployments where most requests are short.
- Higher (e.g., 30,000) for code-generation-heavy deployments where large prompts are normal.

The LAL rules would:
1. Extract AI metadata (`gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`, `gen_ai.request.model`,
   `gen_ai.provider.name`) from OTLP log record attributes.
2. Compute `total_tokens = input_tokens + output_tokens`.
3. Associate logs with the gateway service and instance using resource attributes (`service.name`,
   `service.instance.id`) in the `ENVOY_AI_GATEWAY` layer.
4. **Apply sampling**: persist only logs matching at least one of:
   - `total_tokens >= 10,000` (configurable threshold)
   - `response_code >= 400`
   - `duration >= timeout_threshold` or non-empty `upstream_transport_failure_reason`

### 5. UI Dashboard

**OAP side** — Create dashboard JSON templates under
`oap-server/server-starter/src/main/resources/ui-initialized-templates/envoy_ai_gateway/`:
- `envoy-ai-gateway-root.json` — Root list view of all AI Gateway services.
- `envoy-ai-gateway-service.json` — Service dashboard: Request CPM, latency, token rates, TTFT, TPOT,
  estimated cost, with provider and model breakdown panels.
- `envoy-ai-gateway-instance.json` — Instance (pod) level dashboard.

**UI side** — A separate PR in [skywalking-booster-ui](https://github.com/apache/skywalking-booster-ui)
is needed for i18n menu entries (similar to
[skywalking-booster-ui#534](https://github.com/apache/skywalking-booster-ui/pull/534) for Virtual GenAI).
The menu entry should be added under the infrastructure/gateway category.

## Imported Dependencies libs and their licenses.
No new dependency. The AI Gateway pushes both metrics and access logs via OTLP to SkyWalking's
existing otel-receiver.

## Compatibility
- New layer `ENVOY_AI_GATEWAY` — no breaking change, additive only.
- New MAL rules — opt-in via configuration.
- New LAL rules for OTLP access logs — opt-in via configuration.
- Reuses existing `gen-ai-config.yml` for cost estimation (shared with agent-based GenAI from PR #13745).
- No changes to query protocol or storage structure — uses existing meter and log storage.
- No external log collector (FluentBit, etc.) required — access logs are pushed via OTLP.

## General usage docs

### Prerequisites
- Envoy AI Gateway deployed with the `GatewayConfig` CRD configured (see Section 2 for the full
  env var setup including `OTEL_SERVICE_NAME`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_RESOURCE_ATTRIBUTES`).

### Step 1: Configure Envoy AI Gateway

Apply the `GatewayConfig` CRD from Section 2 to your AI Gateway deployment. Key env vars:

| Env Var | Value | Purpose |
|---|---|---|
| `OTEL_SERVICE_NAME` | `envoy-ai-gateway` | Routes metrics/logs to correct MAL/LAL rules via `job_name` (fixed for all deployments) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://skywalking-oap:11800` | SkyWalking OAP OTLP receiver |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` | OTLP transport |
| `OTEL_METRICS_EXPORTER` | `otlp` | Enable OTLP metrics push |
| `OTEL_LOGS_EXPORTER` | `otlp` | Enable OTLP access log push |
| `GATEWAY_NAME` | (auto from label) | Auto-resolved from pod label `gateway.envoyproxy.io/owning-gateway-name` |
| `POD_NAME` | (auto from Downward API) | Auto-resolved from pod name |
| `OTEL_RESOURCE_ATTRIBUTES` | `aigw.service=$(GATEWAY_NAME),service.instance.id=$(POD_NAME)` | SkyWalking service name (auto) + instance ID (auto) |

### Step 2: Configure SkyWalking OAP

Enable the OTel receiver, MAL rules, and LAL rules in `application.yml`:
```yaml
receiver-otel:
  selector: ${SW_OTEL_RECEIVER:default}
  default:
    enabledHandlers: ${SW_OTEL_RECEIVER_ENABLED_HANDLERS:"otlp-metrics,otlp-logs"}
    enabledOtelMetricsRules: ${SW_OTEL_RECEIVER_ENABLED_OTEL_METRICS_RULES:"envoy-ai-gateway"}

log-analyzer:
  selector: ${SW_LOG_ANALYZER:default}
  default:
    lalFiles: ${SW_LOG_LAL_FILES:"envoy-ai-gateway"}
```

### Cost Estimation

Update `gen-ai-config.yml` with pricing for the models served through the AI Gateway.
The same config file is shared with agent-based GenAI monitoring.

## Appendix A: OTLP Payload Verification

The following data was verified by capturing raw OTLP payloads from the AI Gateway
(`envoyproxy/ai-gateway-cli:latest` Docker image) via an OTel Collector debug exporter.

#### Resource Attributes

With `OTEL_RESOURCE_ATTRIBUTES=service.instance.id=test-instance-456` and
`OTEL_SERVICE_NAME=aigw-test-service`:

| Attribute | Value | Notes |
|---|---|---|
| `service.instance.id` | `test-instance-456` | Set via `OTEL_RESOURCE_ATTRIBUTES` — **confirmed working** |
| `service.name` | `aigw-test-service` | Set via `OTEL_SERVICE_NAME` env var |
| `telemetry.sdk.language` | `go` | SDK metadata |
| `telemetry.sdk.name` | `opentelemetry` | SDK metadata |
| `telemetry.sdk.version` | `1.40.0` | SDK metadata |

**Not present by default (without explicit env config):** `service.instance.id`, `aigw.service`, `host.name`.
These must be explicitly set via `OTEL_RESOURCE_ATTRIBUTES` in the `GatewayConfig` CRD (see Section 2).

`resource.WithFromEnv()` (source: `internal/metrics/metrics.go:35-94`) is called inside a conditional
block that requires `OTEL_EXPORTER_OTLP_ENDPOINT` to be set. When configured, `OTEL_RESOURCE_ATTRIBUTES`
is fully honored.

#### Metric-Level Attributes (Labels)

All 4 metrics carry:

| Label | Example Value | Notes |
|---|---|---|
| `gen_ai.operation.name` | `chat` | Operation type |
| `gen_ai.original.model` | `llama3.2:latest` | Original model from request |
| `gen_ai.provider.name` | `openai` | Backend provider name. In K8s mode with explicit backend routing, this is the configured backend name. |
| `gen_ai.request.model` | `llama3.2:latest` | Requested model |
| `gen_ai.response.model` | `llama3.2:latest` | Model from response |
| `gen_ai.token.type` | `input` / `output` / `cached_input` / `cache_creation_input` | Only on `gen_ai.client.token.usage`. **No `total` value** — total must be computed. `cached_input` and `cache_creation_input` are for Anthropic-style prompt caching. |

#### Metric Names and Types

| OTLP Metric Name | Type | Unit | Temporality |
|---|---|---|---|
| `gen_ai.client.token.usage` | **Histogram** (not Counter!) | `token` | **Delta** |
| `gen_ai.server.request.duration` | Histogram | `s` (seconds, not ms!) | Delta |
| `gen_ai.server.time_to_first_token` | Histogram | `s` | Delta (streaming only) |
| `gen_ai.server.time_per_output_token` | Histogram | `s` | Delta (streaming only) |

**Key findings:**
1. Token usage is a **Histogram**, not a Counter — Sum/Count/Min/Max available per bucket.
2. Duration is in **seconds** — MAL rules must multiply by 1000 for ms display.
3. Temporality is **Delta** — MAL needs `increase()` semantics, not `rate()`.
4. TTFT and TPOT **only appear for streaming requests** — non-streaming produces only token.usage + request.duration.
5. **Dots in metric names** — OTLP uses dots (`gen_ai.client.token.usage`), Prometheus converts to underscores.

#### Histogram Bucket Boundaries (verified from source: `internal/metrics/genai.go`)

Token usage (14 boundaries, power-of-4):
`1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576, 4194304, 16777216, 67108864`

Request duration (14 boundaries, power-of-2 seconds):
`0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96, 81.92`

TTFT (21 boundaries, finer granularity for streaming):
`0.001, 0.005, 0.01, 0.02, 0.04, 0.06, 0.08, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0, 15.0, 20.0, 30.0, 45.0, 60.0`

TPOT (13 boundaries, finest granularity):
`0.01, 0.025, 0.05, 0.075, 0.1, 0.15, 0.2, 0.3, 0.4, 0.5, 0.75, 1.0, 2.5`

#### Impact on Implementation

| Finding | Impact |
|---|---|
| No `service.instance.id` by default | `OTEL_RESOURCE_ATTRIBUTES=service.instance.id=<value>` **works** when OTLP exporter is configured (verified). MAL rules should treat instance as optional and document `OTEL_RESOURCE_ATTRIBUTES` configuration. |
| `gen_ai.provider.name` = backend name | In K8s mode with explicit backend config, this is the configured backend name. |
| Token usage is Histogram | MAL uses histogram sum/count, not counter value. |
| Delta temporality | SkyWalking OTel receiver must handle delta-to-cumulative conversion. |
| Duration in seconds | MAL rules multiply by 1000 for ms-based metrics. |
| TTFT/TPOT streaming-only | Dashboard should note these metrics may be absent for non-streaming workloads. |

#### Bonus: Traces Also Pushed

The AI Gateway also pushes OpenInference traces via OTLP, including full request/response content
in span attributes (`llm.input_messages`, `llm.output_messages`, `llm.token_count.*`). This is a
potential future integration point but out of scope for this SWIP.

## Appendix B: Raw OTLP Metric Data (Verified)

Captured from OTel Collector debug exporter. This is the actual OTLP payload from `envoyproxy/ai-gateway-cli:latest`.

### Resource Attributes
```
Resource SchemaURL: https://opentelemetry.io/schemas/1.39.0
Resource attributes:
     -> service.instance.id: Str(test-instance-456)
     -> service.name: Str(aigw-test-service)
     -> telemetry.sdk.language: Str(go)
     -> telemetry.sdk.name: Str(opentelemetry)
     -> telemetry.sdk.version: Str(1.40.0)
```

`OTEL_RESOURCE_ATTRIBUTES=service.instance.id=<value>` **is honored** when an OTLP exporter is configured
(i.e., `OTEL_EXPORTER_OTLP_ENDPOINT` is set). Without an OTLP endpoint, the resource block is skipped and
only the Prometheus reader is used (which does not carry resource attributes per-metric).

### InstrumentationScope
```
ScopeMetrics SchemaURL:
InstrumentationScope envoyproxy/ai-gateway
```

### Metric 1: gen_ai.client.token.usage (input tokens)
```
Name: gen_ai.client.token.usage
Description: Number of tokens processed.
Unit: token
DataType: Histogram
AggregationTemporality: Delta

Data point attributes:
     -> gen_ai.operation.name: Str(chat)
     -> gen_ai.original.model: Str(llama3.2:latest)
     -> gen_ai.provider.name: Str(openai)
     -> gen_ai.request.model: Str(llama3.2:latest)
     -> gen_ai.response.model: Str(llama3.2:latest)
     -> gen_ai.token.type: Str(input)
Count: 1
Sum: 31.000000
Min: 31.000000
Max: 31.000000
ExplicitBounds: [1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576, 4194304, 16777216, 67108864]
```

### Metric 1b: gen_ai.client.token.usage (output tokens)
```
Data point attributes:
     -> gen_ai.token.type: Str(output)
     (other attributes same as above)
Count: 1
Sum: 3.000000
```

### Metric 2: gen_ai.server.request.duration
```
Name: gen_ai.server.request.duration
Description: Generative AI server request duration such as time-to-last byte or last output token.
Unit: s
DataType: Histogram
AggregationTemporality: Delta

Data point attributes:
     -> gen_ai.operation.name: Str(chat)
     -> gen_ai.original.model: Str(llama3.2:latest)
     -> gen_ai.provider.name: Str(openai)
     -> gen_ai.request.model: Str(llama3.2:latest)
     -> gen_ai.response.model: Str(llama3.2:latest)
Count: 1
Sum: 10.432428
ExplicitBounds: [0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96, 81.92]
```

### Metric 3: gen_ai.server.time_to_first_token (streaming only)
```
Name: gen_ai.server.time_to_first_token
Description: Time to receive first token in streaming responses.
Unit: s
DataType: Histogram
AggregationTemporality: Delta
(Same attributes as request.duration, excluding gen_ai.token.type)
ExplicitBounds (from source code): [0.001, 0.005, 0.01, 0.02, 0.04, 0.06, 0.08, 0.1, 0.25, 0.5,
                                     0.75, 1.0, 2.5, 5.0, 7.5, 10.0, 15.0, 20.0, 30.0, 45.0, 60.0]
```

### Metric 4: gen_ai.server.time_per_output_token (streaming only)
```
Name: gen_ai.server.time_per_output_token
Description: Time per output token generated after the first token for successful responses.
Unit: s
DataType: Histogram
AggregationTemporality: Delta
(Same attributes as request.duration, excluding gen_ai.token.type)
ExplicitBounds (from source code): [0.01, 0.025, 0.05, 0.075, 0.1, 0.15, 0.2, 0.3, 0.4, 0.5,
                                     0.75, 1.0, 2.5]
```

## Appendix C: Access Log Format (from Envoy Config Dump)

The AI Gateway auto-configures two access log entries on the listener (one for LLM, one for MCP).
Verified from `config_dump` of the AI Gateway.

### LLM Access Log Format (JSON)

Filter: `request.headers['x-ai-eg-model'] != ''` (only logs requests processed by the AI Gateway ext_proc)

```json
{
  "start_time": "%START_TIME%",
  "method": "%REQ(:METHOD)%",
  "request.path": "%REQ(:PATH)%",
  "x-envoy-origin-path": "%REQ(X-ENVOY-ORIGINAL-PATH?:PATH)%",
  "response_code": "%RESPONSE_CODE%",
  "duration": "%DURATION%",
  "bytes_received": "%BYTES_RECEIVED%",
  "bytes_sent": "%BYTES_SENT%",
  "user-agent": "%REQ(USER-AGENT)%",
  "x-request-id": "%REQ(X-REQUEST-ID)%",
  "x-forwarded-for": "%REQ(X-FORWARDED-FOR)%",
  "x-envoy-upstream-service-time": "%RESP(X-ENVOY-UPSTREAM-SERVICE-TIME)%",
  "upstream_host": "%UPSTREAM_HOST%",
  "upstream_cluster": "%UPSTREAM_CLUSTER%",
  "upstream_local_address": "%UPSTREAM_LOCAL_ADDRESS%",
  "upstream_transport_failure_reason": "%UPSTREAM_TRANSPORT_FAILURE_REASON%",
  "downstream_remote_address": "%DOWNSTREAM_REMOTE_ADDRESS%",
  "downstream_local_address": "%DOWNSTREAM_LOCAL_ADDRESS%",
  "connection_termination_details": "%CONNECTION_TERMINATION_DETAILS%",
  "gen_ai.request.model": "%REQ(X-AI-EG-MODEL)%",
  "gen_ai.response.model": "%DYNAMIC_METADATA(io.envoy.ai_gateway:model_name_override)%",
  "gen_ai.provider.name": "%DYNAMIC_METADATA(io.envoy.ai_gateway:backend_name)%",
  "gen_ai.usage.input_tokens": "%DYNAMIC_METADATA(io.envoy.ai_gateway:llm_input_token)%",
  "gen_ai.usage.output_tokens": "%DYNAMIC_METADATA(io.envoy.ai_gateway:llm_output_token)%",
  "session.id": "%DYNAMIC_METADATA(io.envoy.ai_gateway:session.id)%"
}
```

**Code review corrections** (source: `internal/metrics/genai.go`, `examples/access-log/basic.yaml`,
`site/docs/capabilities/observability/accesslogs.md`):
- `response_flags` (`%RESPONSE_FLAGS%`) IS documented in AI Gateway access log docs and used in tests,
  but not in the default config. Can be added via `EnvoyProxy` resource if needed.
- `gen_ai.usage.total_tokens` IS supported via `%DYNAMIC_METADATA(io.envoy.ai_gateway:llm_total_token)%`
  when `AIGatewayRoute.spec.llmRequestCosts` includes `type: TotalToken`.
- Access log format is **user-configurable** via `EnvoyProxy` resource, not hardcoded by the AI Gateway.
  The AI Gateway only populates dynamic metadata; users define which fields appear in logs.
- Additional token cost types beyond input/output/total: `CachedInputToken` and `CacheCreationInputToken`
  (for Anthropic-style prompt caching, stored as `llm_cached_input_token` and
  `llm_cache_creation_input_token` in dynamic metadata).

### MCP Access Log Format (JSON)

Filter: `request.headers['x-ai-eg-mcp-backend'] != ''`

```json
{
  "start_time": "%START_TIME%",
  "method": "%REQ(:METHOD)%",
  "request.path": "%REQ(:PATH)%",
  "response_code": "%RESPONSE_CODE%",
  "duration": "%DURATION%",
  "mcp.method.name": "%DYNAMIC_METADATA(io.envoy.ai_gateway:mcp_method)%",
  "mcp.provider.name": "%DYNAMIC_METADATA(io.envoy.ai_gateway:mcp_backend)%",
  "mcp.session.id": "%REQ(MCP-SESSION-ID)%",
  "jsonrpc.request.id": "%DYNAMIC_METADATA(io.envoy.ai_gateway:mcp_request_id)%",
  "session.id": "%DYNAMIC_METADATA(io.envoy.ai_gateway:session.id)%"
}
```

