# Redis monitoring
## Redis server performance from `redis-exporter`
SkyWalking leverages redis-exporter for collecting metrics data from Redis. It leverages OpenTelemetry Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).

### Data flow
1. redis-exporter collect metrics data from Redis.
2. OpenTelemetry Collector fetches metrics from redis-exporter via Prometheus Receiver and pushes metrics to SkyWalking OAP Server via OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

### Set up
1. Set up [redis-exporter](https://github.com/oliver006/redis_exporter#building-and-running-the-exporter).
2. Set up [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/getting-started/#docker). For details on Redis Receiver in OpenTelemetry Collector, refer to [here](../../../../test/e2e-v2/cases/redis/redis-exporter/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

### Redis Monitoring
Redis monitoring provides monitoring of the status and resources of the Redis server. Redis cluster is cataloged as a `Layer: REDIS` `Service` in OAP.
Each Redis server is cataloged as an `Instance` in OAP.
#### Supported Metrics
| Monitoring Panel                  | Unit   | Metric Name                                                                                       | Description                                        | Data Source    |
|-----------------------------------|--------|---------------------------------------------------------------------------------------------------|----------------------------------------------------|----------------|
| Uptime                            | day    | meter_redis_uptime                                                                                | The uptime of Redis.                               | redis-exporter |
| Connected Clients                 |        | meter_redis_connected_clients                                                                     | The number of connected clients.                   | redis-exporter |
| Blocked Clients                   |        | meter_redis_blocked_clients                                                                       | The number of blocked clients.                     | redis-exporter |
| Memory Max Bytes                  | MB     | meter_redis_memory_max_bytes                                                                      | The max bytes of memory.                           | redis-exporter |
| Hits Rate                         | %      | meter_redis_hit_rate                                                                              | Hit rate of redis when used as a cache.            | redis-exporter |
| Average Time Spend By Command     | second | meter_redis_average_time_spent_by_command                                                         | Average time to execute various types of commands. | redis-exporter |
| Total Commands Trend              |        | meter_redis_total_commands_rate                                                                   | The Trend of total commands.                       | redis-exporter |
| DB keys                           |        | meter_redis_evicted_keys_total  </br>   meter_redis_expired_keys_total  </br> meter_redis_db_keys | The number of Expired / Evicted / total keys.      | redis-exporter |
| Net Input/Output Bytes            | KB     | meter_redis_net_input_bytes  </br>  meter_redis_net_output_bytes                                  | Total bytes of input / output of redis net.        | redis-exporter |
| Memory Usage                      | %      | meter_redis_memory_usage                                                                          | Percentage of used memory.                         | redis-exporter |
| Total Time Spend By Command Trend |        | meter_redis_commands_duration_seconds_total_rate                                                  | The trend of total time spend by command           | redis-exporter |

### Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/redis`.
The Redis dashboard panel configurations are found in `/config/ui-initialized-templates/redis`.

