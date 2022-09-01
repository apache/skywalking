# Satellite self observability dashboard

SkyWalking Satellite collects and exports metrics in Prometheus format and SkyWalking metrics service protobuffer format for consuming,
it also provides a dashboard to visualize the Satellite metrics.

## Data flow
1. SkyWalking Satellite collects metrics data internally and pushes the metrics to SkyWalking OAP.
2. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

## Set up
1. Set up [SkyWalking Satellite Telemetry Exporter](https://github.com/apache/skywalking-satellite/blob/1987e1d566ac90f6b58a45fd9bfa27bf8faad635/docs/en/setup/examples/feature/telemetry-exporter/README.md).
2. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

## Self observability monitoring
Self observability monitoring provides monitoring of the status and resources of the OAP server itself. `oap-server` is a `Service` in OAP, and land on the `Layer: SO11Y_OAP`.

### Self observability metrics

| Monitoring Panel | Unit | Metric Name | Description | Data Source |
|-----|------|-----|-----|-----|
|  | Count | satellite_service_grpc_connect_count | Connection Count | SkyWalking Satellite |
|  | Percentage | satellite_service_server_cpu_utilization | CPU (%) | SkyWalking Satellite |
|  | Count | satellite_service_queue_used_count    | The used count of queue of pipeline | SkyWalking Satellite |
|  | Count | satellite_service_receive_event_count | Receive count of event from downstream | SkyWalking Satellite |
|  | Count | satellite_service_fetch_event_count   | Fetch count of event from downstream | SkyWalking Satellite |
|  | Count | satellite_service_queue_input_count   | The event count of push to the queue | SkyWalking Satellite |
|  | Count | satellite_service_send_event_count    | The event count of push data to the upstream | SkyWalking Satellite |

## Customizations
You can customize your own metrics/expression/dashboard panel.
The self observability dashboard panel configurations are found in `/config/ui-initialized-templates/so11y_satellite/so11y-root.json`.
