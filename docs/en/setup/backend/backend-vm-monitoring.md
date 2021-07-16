# VMs monitoring 
SkyWalking leverages Prometheus node-exporter for collecting metrics data from the VMs, and leverages OpenTelemetry Collector to transfer the metrics to
[OpenTelemetry receiver](backend-receivers.md#opentelemetry-receiver) and into the [Meter System](./../../concepts-and-designs/meter.md).  
We define the VM entity as a `Service` in OAP, and use `vm::` as a prefix to identify it.  

## Data flow
1. The Prometheus node-exporter collects metrics data from the VMs.
2. The OpenTelemetry Collector fetches metrics from the node-exporter via Prometheus Receiver and pushes metrics to SkyWalking OAP Server via the OpenCensus gRPC Exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results. 

## Setup 
1. Setup [Prometheus node-exporter](https://prometheus.io/docs/guides/node-exporter/).
2. Setup [OpenTelemetry Collector ](https://opentelemetry.io/docs/collector/). This is an example for OpenTelemetry Collector configuration [otel-collector-config.yaml](../../../../test/e2e/e2e-test/docker/promOtelVM/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](backend-receivers.md#opentelemetry-receiver).
   
## Supported Metrics

| Monitoring Panel | Unit | Metric Name | Description | Data Source |
|-----|-----|-----|-----|-----|
| CPU Usage | % | cpu_total_percentage | The total percentage usage of the CPU core. If there are 2 cores, the maximum usage is 200%. | Prometheus node-exporter |
| Memory RAM Usage | MB | meter_vm_memory_used | The total RAM usage | Prometheus node-exporter |
| Memory Swap Usage | % | meter_vm_memory_swap_percentage | The percentage usage of swap memory | Prometheus node-exporter |
| CPU Average Used | % | meter_vm_cpu_average_used | The percentage usage of the CPU core in each mode | Prometheus node-exporter |
| CPU Load |  | meter_vm_cpu_load1<br />meter_vm_cpu_load5<br />meter_vm_cpu_load15 | The CPU 1m / 5m / 15m average load | Prometheus node-exporter |
| Memory RAM | MB | meter_vm_memory_total<br />meter_vm_memory_available<br />meter_vm_memory_used | The RAM statistics, including Total / Available / Used | Prometheus node-exporter |
| Memory Swap | MB | meter_vm_memory_swap_free<br />meter_vm_memory_swap_total | The swap memory statistics, including Free / Total | Prometheus node-exporter |
| File System Mountpoint Usage | % | meter_vm_filesystem_percentage | The percentage usage of the file system at each mount point | Prometheus node-exporter |
| Disk R/W | KB/s | meter_vm_disk_read,meter_vm_disk_written | The disk read and written | Prometheus node-exporter |
| Network Bandwidth Usage | KB/s | meter_vm_network_receive<br />meter_vm_network_transmit | The network receive and transmit | Prometheus node-exporter |
| Network Status |  | meter_vm_tcp_curr_estab<br />meter_vm_tcp_tw<br />meter_vm_tcp_alloc<br />meter_vm_sockets_used<br />meter_vm_udp_inuse | The number of TCPs established / TCP time wait / TCPs allocated / sockets in use / UDPs in use | Prometheus node-exporter |
| Filefd Allocated |  | meter_vm_filefd_allocated | The number of file descriptors allocated | Prometheus node-exporter |

## Customizing 
You can customize your own metrics/expression/dashboard panel.   
The metrics definition and expression rules are found in `/config/otel-oc-rules/vm.yaml`.  
The dashboard panel confirmations are found in `/config/ui-initialized-templates/vm.yml`.

## Blog
For more details, see blog article [SkyWalking 8.4 provides infrastructure monitoring](https://skywalking.apache.org/blog/2021-02-07-infrastructure-monitoring/).
