# Linux Monitoring
SkyWalking leverages Prometheus node-exporter to collect metrics data from the VMs and leverages OpenTelemetry Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/mal.md).
VM entity as a `Service` in OAP and on the `Layer: OS_LINUX`.
SkyWalking also provides InfluxDB Telegraf to receive VMs' metrics data by [Telegraf receiver](./telegraf-receiver.md).
The telegraf receiver plugin receiver, process and convert the metrics, then it send converted metrics to [Meter System](./../../concepts-and-designs/mal.md).
VM entity as a `Service` in OAP and on the `Layer: OS_LINUX`.
## Data flow
**For OpenTelemetry receiver:**
1. The Prometheus node-exporter collects metrics data from the VMs.
2. The OpenTelemetry Collector fetches metrics from node-exporter via Prometheus Receiver and pushes metrics to the SkyWalking OAP Server via OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.
**For Telegraf receiver:**
1. The InfluxDB Telegraf [input plugins](https://docs.influxdata.com/telegraf/v1.24/plugins/) collects various metrics data from the VMs.
2. The cpu, mem, system, disk and diskio input plugins should be set in telegraf.conf file.
2. The InfluxDB Telegraf send `JSON` format metrics by `HTTP` messages to Telegraf Receiver, then pushes converted metrics to the SkyWalking OAP Server [Meter System](./../../concepts-and-designs/mal.md).
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate ad store the results.
4. The meter_vm_cpu_average_used metrics indicates the average usage of each CPU core for telegraf receiver.
## Setup
**For OpenTelemetry receiver:**
1. Setup [Prometheus node-exporter](https://prometheus.io/docs/guides/node-exporter/).
2. Setup [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/). This is an example for OpenTelemetry Collector configuration [otel-collector-config.yaml](../../../../test/e2e-v2/cases/vm/prometheus-node-exporter/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).
**For Telegraf receiver:**
1. Setup InfluxDB Telegraf's `telegraf.conf file` according to [Telegraf office document](https://docs.influxdata.com/telegraf/v1.24/).
2. Setup InfluxDB Telegraf's `telegraf.conf file` specific rules according to [Telegraf receiver document](telegraf-receiver.md).
3. Config SkyWalking [Telegraf receiver](telegraf-receiver.md).

### Native OpenTelemetry hostmetrics (extended alternative)
SkyWalking can also receive Linux host and process metrics directly from the OpenTelemetry Collector `hostmetrics` receiver, without Prometheus node-exporter.

1. Install OpenTelemetry Collector Contrib `0.158.0` on the monitored host.
2. Use the deployable reference configuration [otel-collector-hostmetrics-linux.yaml](otel-collector-hostmetrics-linux.yaml).
3. Set `SW_OAP_GRPC_ADDRESS` to the SkyWalking OAP OTLP/gRPC endpoint. Optionally set `SW_PROCESS_NAME_REGEX` to restrict the process executable names to collect.
4. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md). The default OAP rule set enables both `vm` and `process-hostmetrics-linux`.

The Collector normalizes host and process identity, compacts process resource metrics, aligns process datapoint timestamps to the collection window, and pre-aggregates operating-system processes with the same normalized `process_name` before OTLP export. `process-hostmetrics-linux.yaml` maps the aggregated metrics to the corresponding SkyWalking process instance.

## Supported Metrics

The two OpenTelemetry receiver paths do not expose exactly the same Linux kernel metrics. In particular, OpenTelemetry Collector Contrib `0.158.0` `hostmetrics` has no host-level metric equivalent to node-exporter's `node_filefd_allocated`, which is derived from `/proc/sys/fs/file-nr`. The per-process `process.open_file_descriptors` metric has different semantics and is intentionally not used as a substitute. Therefore, `meter_vm_filefd_allocated` remains available only through the node-exporter path.

Likewise, `meter_vm_tcp_alloc`, `meter_vm_sockets_used`, and `meter_vm_udp_inuse` remain node-exporter-only because `hostmetrics` `system.network.connections` does not expose equivalent Linux sockstat counters. `meter_vm_tcp_curr_estab` and `meter_vm_tcp_tw` do have compatible hostmetrics equivalents.

| Monitoring Panel | Unit | Metric Name | Description | node-exporter | OTel hostmetrics | Telegraf |
|---|---|---|---|:---:|:---:|:---:|
| CPU Usage | % | `meter_vm_cpu_total_percentage` | Total CPU usage across all logical CPUs | Yes | Yes | Yes |
| CPU Cores | count | `meter_vm_cpu_cores_num` | Number of logical CPUs | — | Yes | — |
| Normalized CPU Usage | % | `meter_vm_cpu_norm_percentage` | CPU usage normalized by the number of logical CPUs | — | Yes | — |
| CPU Average Used | % | `meter_vm_cpu_average_used` | CPU usage by mode/state | Yes | Yes | Yes |
| CPU Load | | `meter_vm_cpu_load1`<br />`meter_vm_cpu_load5`<br />`meter_vm_cpu_load15` | CPU 1m / 5m / 15m average load | Yes | Yes | Yes |
| Memory RAM Usage | MB | `meter_vm_memory_used` | Total RAM usage | Yes | Yes | Yes |
| Memory RAM | MB | `meter_vm_memory_total`<br />`meter_vm_memory_available`<br />`meter_vm_memory_used`<br />`meter_vm_memory_buff_cache` | RAM Total / Available / Used / Buff-Cache | Yes | Yes | Yes |
| Memory Swap Usage | % | `meter_vm_memory_swap_percentage` | Percentage of swap memory in use | Yes | Yes | Yes |
| Memory Swap | MB | `meter_vm_memory_swap_free`<br />`meter_vm_memory_swap_total` | Swap Free / Total | Yes | Yes | Yes |
| File System Mountpoint Usage | % | `meter_vm_filesystem_percentage` | File-system usage at each mount point | Yes | Yes | Yes |
| Disk R/W | KB/s | `meter_vm_disk_read`<br />`meter_vm_disk_written` | Disk read and write throughput | Yes | Yes | Yes |
| Network Bandwidth Usage | KB/s | `meter_vm_network_receive`<br />`meter_vm_network_transmit` | Network receive and transmit throughput | Yes | Yes | Yes |
| TCP Established / Close-Wait | count | `meter_vm_tcp_curr_estab` | TCP connections in ESTABLISHED or CLOSE-WAIT state | Yes | Yes | Yes |
| TCP Time Wait | count | `meter_vm_tcp_tw` | TCP connections in TIME-WAIT state | Yes | Yes | Yes |
| TCP Allocated | count | `meter_vm_tcp_alloc` | Allocated TCP sockets | Yes | No | Yes |
| Sockets Used | count | `meter_vm_sockets_used` | Kernel sockets currently in use | Yes | No | Yes |
| UDP In Use | count | `meter_vm_udp_inuse` | UDP sockets currently in use | Yes | No | Yes |
| Filefd Allocated | count | `meter_vm_filefd_allocated` | Host-level allocated file descriptors from Linux `/proc/sys/fs/file-nr` | Yes | No | — |
| Network Connections | count | `meter_vm_network_connections` | TCP connections grouped by protocol and state | — | Yes | — |

## Customizing
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/vm.yaml` and `/config/telegraf-rules/vm.yaml`.
The dashboard panel confirmations ship from the SkyWalking Horizon UI bundle (apache/skywalking-horizon-ui); the OAP backend no longer hosts UI dashboard JSONs.
## Blog
For more details, see the blog article [SkyWalking 8.4 provides infrastructure monitoring](https://skywalking.apache.org/blog/2021-02-07-infrastructure-monitoring/).
