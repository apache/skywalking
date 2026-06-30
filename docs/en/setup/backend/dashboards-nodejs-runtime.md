# Node.js runtime metrics

The SkyWalking Node.js agent reports **runtime metrics** (process memory and CPU) through the
**`MeterReportService`** gRPC protocol—the same pipeline used by the Go and Python agents. OAP analyzes raw meters via
`meter-analyzer-config/nodejs-runtime.yaml` and stores **`meter_instance_nodejs_*`** metrics at the service instance level.

## Platform support

Runtime meters are collected on **Linux, macOS, and Windows** via Node.js built-in APIs
(`process.memoryUsage()`, `process.cpuUsage()`, `v8.getHeapStatistics()`).

## Data flow

1. On each collect interval (default **1 second**), the agent samples Node.js runtime APIs.
2. Samples are mapped into `MeterData` protobuf messages and buffered.
3. On each report interval (default **1 second**), buffered meters are sent to OAP over gRPC port **11800**
   via `MeterReportService.collect` (independent from trace export on the same address).
4. OAP applies MAL rules in `nodejs-runtime.yaml` and exposes **`meter_instance_nodejs_*`** metrics.
5. Horizon UI renders widgets on **General Service → Instance → Dashboard** when the corresponding
   `meter_instance_nodejs_*` metrics exist.

## Agent setup

Runtime metric reporting is **on by default**. Relevant environment variables:

| Variable | Description | Default |
| :--- | :--- | :--- |
| `SW_AGENT_NODEJS_RUNTIME_METRICS_REPORTER_ACTIVE` | Master switch for runtime metric export | `true` |
| `SW_AGENT_NODEJS_RUNTIME_METRICS_COLLECT_PERIOD` | Sample interval (ms) | `1000` |
| `SW_AGENT_NODEJS_RUNTIME_METRICS_REPORT_PERIOD` | Report interval (ms) | `1000` |
| `SW_AGENT_NODEJS_RUNTIME_METRICS_BUFFER_SIZE` | Max buffered samples before dropping oldest | `600` |

Deprecated aliases `SW_AGENT_RUNTIME_METRICS_*`, `SW_AGENT_NVM_METRICS_*`, and `SW_AGENT_NVM_JVM_*` are still accepted.

See the [Node.js agent README](https://github.com/apache/skywalking-nodejs/blob/master/README.md#nodejs-runtime-metrics)
for startup examples and the full field mapping from Node.js APIs to meter names.

## OAP setup

Ensure the gRPC receiver is reachable on the port configured in `SW_AGENT_COLLECTOR_BACKEND_SERVICES` (default `11800`).
The `nodejs-runtime` meter analyzer file is included in the default `meterAnalyzerActiveFiles` list—no extra
configuration is required for current Node.js agents.

Meter rules live in `oap-server/server-starter/src/main/resources/meter-analyzer-config/nodejs-runtime.yaml`.

## UI location

**Layer:** General Service (`GENERAL`)

**Path:** select a Node.js service → **Instance** → **Dashboard**

Widgets appear only when runtime data is present (`visibleWhen` checks each `meter_instance_nodejs_*` expression).

## Runtime metrics

The agent reports raw meter names; OAP prefixes them with `meter_` when exposing queryable metrics:

| Unit | Agent meter name | OAP / UI metric name | Description | Data Source |
| :--- | :--- | :--- | :--- | :--- |
| % | `instance_nodejs_process_cpu` | `meter_instance_nodejs_process_cpu` | Process CPU (user + system) over collect interval | SkyWalking Node.js Agent |
| bytes | `instance_nodejs_heap_used` | `meter_instance_nodejs_heap_used` | V8 heap used | SkyWalking Node.js Agent |
| bytes | `instance_nodejs_heap_total` | `meter_instance_nodejs_heap_total` | V8 heap total | SkyWalking Node.js Agent |
| bytes | `instance_nodejs_heap_limit` | `meter_instance_nodejs_heap_limit` | V8 max heap size | SkyWalking Node.js Agent |
| bytes | `instance_nodejs_rss` | `meter_instance_nodejs_rss` | Resident set size | SkyWalking Node.js Agent |
| bytes | `instance_nodejs_external_memory` | `meter_instance_nodejs_external_memory` | External memory | SkyWalking Node.js Agent |

## Customizations

You can customize MAL expressions or dashboard panels. Metric definitions and expression rules are in
`meter-analyzer-config/nodejs-runtime.yaml`. Instance dashboard widget templates ship from the
SkyWalking Horizon UI bundle (`general.json` in apache/skywalking-horizon-ui).
