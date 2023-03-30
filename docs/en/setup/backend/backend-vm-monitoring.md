# Linux Monitoring
SkyWalking leverages Prometheus node-exporter to collect metrics data from the VMs and leverages OpenTelemetry Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).
VM entity as a `Service` in OAP and on the `Layer: OS_LINUX`.

SkyWalking also provides InfluxDB Telegraf to receive VMs' metrics data by [Telegraf receiver](./telegraf-receiver.md).
The telegraf receiver plugin receiver, process and convert the metrics, then it send converted metrics to [Meter System](./../../concepts-and-designs/meter.md).
VM entity as a `Service` in OAP and on the `Layer: OS_LINUX`.

## Data flow
**For OpenTelemetry receiver:**
1. The Prometheus node-exporter collects metrics data from the VMs.
2. The OpenTelemetry Collector fetches metrics from node-exporter via Prometheus Receiver and pushes metrics to the SkyWalking OAP Server via the OpenCensus gRPC Exporter or OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

**For Telegraf receiver:**
1. The InfluxDB Telegraf [input plugins](https://docs.influxdata.com/telegraf/v1.24/plugins/) collects various metrics data from the VMs.
2. The cpu, mem, system, disk and diskio input plugins should be set in telegraf.conf file.
2. The InfluxDB Telegraf send `JSON` format metrics by `HTTP` messages to Telegraf Receiver, then pushes converted metrics to the SkyWalking OAP Server [Meter System](./../../concepts-and-designs/meter.md).
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate ad store the results.
4. The meter_vm_cpu_average_used metrics indicates the average usage of each CPU core for telegraf receiver.

## Setup
**For OpenTelemetry receiver:**
1. Setup [Prometheus node-exporter](https://prometheus.io/docs/guides/node-exporter/).
2. Setup [OpenTelemetry Collector ](https://opentelemetry.io/docs/collector/). This is an example for OpenTelemetry Collector configuration [otel-collector-config.yaml](../../../../test/e2e-v2/cases/vm/prometheus-node-exporter/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

**For Telegraf receiver:**
1. Setup InfluxDB Telegraf's `telegraf.conf file` according to [Telegraf office document](https://docs.influxdata.com/telegraf/v1.24/).
2. Setup InfluxDB Telegraf's `telegraf.conf file` specific rules according to [Telegraf receiver document](telegraf-receiver.md).
3. Config SkyWalking [Telegraf receiver](telegraf-receiver.md).

## Supported Metrics

| Monitoring Panel             | Unit | Metric Name                                                                                                             | Description                                                                                    | Data Source                                         |
|------------------------------|------|-------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|-----------------------------------------------------|
| CPU Usage                    | %    | meter_vm_cpu_total_percentage                                                                                           | The total percentage usage of the CPU core. If there are 2 cores, the maximum usage is 200%.   | Prometheus node-exporter<br />Telegraf input plugin |
| Memory RAM Usage             | MB   | meter_vm_memory_used                                                                                                    | The total RAM usage                                                                            | Prometheus node-exporter<br />Telegraf input plugin |
| Memory Swap Usage            | %    | meter_vm_memory_swap_percentage                                                                                         | The percentage usage of swap memory                                                            | Prometheus node-exporter<br />Telegraf input plugin |
| CPU Average Used             | %    | meter_vm_cpu_average_used                                                                                               | The percentage usage of the CPU core in each mode                                              | Prometheus node-exporter<br />Telegraf input plugin |
| CPU Load                     |      | meter_vm_cpu_load1<br />meter_vm_cpu_load5<br />meter_vm_cpu_load15                                                     | The CPU 1m / 5m / 15m average load                                                             | Prometheus node-exporter<br />Telegraf input plugin |
| Memory RAM                   | MB   | meter_vm_memory_total<br />meter_vm_memory_available<br />meter_vm_memory_used />meter_vm_memory_buff_cache             | The RAM statistics, including Total / Available / Used / Buff-Cache                            | Prometheus node-exporter<br />Telegraf input plugin |
| Memory Swap                  | MB   | meter_vm_memory_swap_free<br />meter_vm_memory_swap_total                                                               | Swap memory statistics, including Free / Total                                                 | Prometheus node-exporter<br />Telegraf input plugin |
| File System Mountpoint Usage | %    | meter_vm_filesystem_percentage                                                                                          | The percentage usage of the file system at each mount point                                    | Prometheus node-exporter<br />Telegraf input plugin |
| Disk R/W                     | KB/s | meter_vm_disk_read,meter_vm_disk_written                                                                                | The disk read and written                                                                      | Prometheus node-exporter<br />Telegraf input plugin |
| Network Bandwidth Usage      | KB/s | meter_vm_network_receive<br />meter_vm_network_transmit                                                                 | The network receive and transmit                                                               | Prometheus node-exporter<br />Telegraf input plugin |
| Network Status               |      | meter_vm_tcp_curr_estab<br />meter_vm_tcp_tw<br />meter_vm_tcp_alloc<br />meter_vm_sockets_used<br />meter_vm_udp_inuse | The number of TCPs established / TCP time wait / TCPs allocated / sockets in use / UDPs in use | Prometheus node-exporter<br />Telegraf input plugin |
| Filefd Allocated             |      | meter_vm_filefd_allocated                                                                                               | The number of file descriptors allocated                                                       | Prometheus node-exporter                            |

## Customizing
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/vm.yaml` and `/config/telegraf-rules/vm.yaml`.
The dashboard panel confirmations are found in `/config/ui-initialized-templates/os_linux`.

## Blog
For more details, see the blog article [SkyWalking 8.4 provides infrastructure monitoring](https://skywalking.apache.org/blog/2021-02-07-infrastructure-monitoring/).
