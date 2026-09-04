# Windows Monitoring
SkyWalking leverages Prometheus windows_exporter to collect metrics data from the Windows and leverages OpenTelemetry Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/mal.md).
Windows entity as a `Service` in OAP and on the `Layer: OS_WINDOWS`.
## Data flow
**For OpenTelemetry receiver:**
1. The Prometheus windows_exporter collects metrics data from the VMs.
2. The OpenTelemetry Collector fetches metrics from windows_exporter via Prometheus Receiver and pushes metrics to the SkyWalking OAP Server via OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.
## Setup
**For OpenTelemetry receiver:**
1. Setup [Prometheus windows_exporter](https://github.com/prometheus-community/windows_exporter).
2. Setup [OpenTelemetry Collector ](https://opentelemetry.io/docs/collector/). This is an example for OpenTelemetry Collector configuration [otel-collector-config.yaml](../../../../test/e2e-v2/cases/win/prometheus-windows_exporter/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

### Native OpenTelemetry hostmetrics (expanded alternative)
SkyWalking can also receive Windows host and process metrics directly from OpenTelemetry Collector Contrib, without Prometheus windows_exporter.

1. Install OpenTelemetry Collector Contrib `0.158.0` on the monitored Windows host.
2. Use the deployable reference configuration [otel-collector-hostmetrics-windows.yaml](otel-collector-hostmetrics-windows.yaml). It includes the Windows Performance Counters required by the existing `windows.yaml` virtual-memory and system-handle metrics.
3. Set `SW_OAP_GRPC_ADDRESS` to the SkyWalking OAP OTLP/gRPC endpoint. Optionally set `SW_PROCESS_NAME_REGEX` to restrict the process executable names to collect.
4. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md). The default OAP rule set enables both `windows` and `process-hostmetrics-windows`.

The configuration normalizes the `system.cpu.time` `state` attribute to the existing `mode` contract and maps native `process.handles` to `process.open_handles`. The process pipeline normalizes process identity and pre-aggregates operating-system processes with the same normalized `process_name` before OTLP export.

## Supported Metrics

The `OTel hostmetrics` column below refers to the complete OpenTelemetry Collector Contrib reference configuration documented above. Most values come from the `hostmetrics` receiver. Metrics marked with `*` use Windows Performance Counters in the same Collector configuration where `hostmetrics` does not expose the required equivalent semantics.

| Monitoring Panel | Unit | Metric Name | Description | windows_exporter | OTel hostmetrics |
|---|---|---|---|:---:|:---:|
| CPU Usage | % | `meter_win_cpu_total_percentage` | Total CPU usage across all logical CPUs | Yes | Yes |
| CPU Average Used | % | `meter_win_cpu_average_used` | CPU usage by mode/state | Yes | Yes |
| CPU Cores | count | `meter_win_cpu_cores_num` | Number of logical CPUs | — | Yes |
| Normalized CPU Usage | % | `meter_win_cpu_norm_percentage` | CPU usage normalized by the number of logical CPUs | — | Yes |
| CPU Load | | `meter_win_cpu_load1`<br />`meter_win_cpu_load5`<br />`meter_win_cpu_load15` |  CPU load metrics exposed by the Collector hostmetrics load scraper | — | Yes |
| Memory RAM Usage | MB | `meter_win_memory_used` | Total RAM usage | Yes | Yes |
| Memory RAM | MB | `meter_win_memory_total`<br />`meter_win_memory_available`<br />`meter_win_memory_used` | RAM Total / Available / Used | Yes | Yes |
| Virtual Memory Usage | % | `meter_win_memory_virtual_memory_percentage` | Percentage of committed virtual memory in use | Yes | Yes* |
| Virtual Memory | MB | `meter_win_memory_virtual_memory_free`<br />`meter_win_memory_virtual_memory_total` | Virtual memory Free / Total | Yes | Yes* |
| File System Mountpoint Usage | % | `meter_win_filesystem_percentage` | File-system usage at each volume or mount point | — | Yes |
| Disk R/W | KB/s | `meter_win_disk_read`<br />`meter_win_disk_written` | Disk read and write throughput | Yes | Yes |
| Network Bandwidth Usage | KB/s | `meter_win_network_receive`<br />`meter_win_network_transmit` | Network receive and transmit throughput | Yes | Yes |
| Allocated Handles | count | `meter_win_filehandles_allocated` | Current system-wide Windows handle count | — | Yes* |
| Pagefile Usage | % | `meter_win_memory_pagefile_percentage` | Percentage of Windows pagefile in use | — | Yes |
| Pagefile | MB | `meter_win_memory_pagefile_free`<br />`meter_win_memory_pagefile_total` | Windows pagefile Free / Total | — | Yes |

`*` The OpenTelemetry path obtains these values through the `windowsperfcounters` receiver included in `otel-collector-hostmetrics-windows.yaml`: `Memory\\Commit Limit`, `Memory\\Committed Bytes`, and `System\\Handle Count`. They are transported and processed through the same OpenTelemetry Collector pipeline but are not native `hostmetrics` scraper metrics.

## Customizing
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/windows.yaml`.
The dashboard panel confirmations ship from the SkyWalking Horizon UI bundle (apache/skywalking-horizon-ui); the OAP backend no longer hosts UI dashboard JSONs.
