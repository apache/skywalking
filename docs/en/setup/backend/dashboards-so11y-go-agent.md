# Go Agent self observability dashboard

SkyWalking go agent reports itself metrics by Meter APIS in order to measure tracing performance.
it also provides a dashboard to visualize the agent metrics.

## Data flow
1. SkyWalking go agent reports metrics data internally and automatically.
2. SkyWalking OAP accept these meters through native protocols.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

## Set up
Go Agent so11y is a build-in feature, it reports meters automatically after boot.

## Self observability monitoring
Self observability monitoring provides monitoring of the runtime performance of the go agent itself. `agent.service_name` is a `Service` in Agent so11y, and land on the `Layer: SO11Y_GO_AGENT`.

### Self observability metrics

| Unit             | Metric Name                                           | Description                                 | Data Source         |
|------------------|-------------------------------------------------------|---------------------------------------------|---------------------|
| Count Per Minute | meter_sw_go_created_tracing_context_count             | Created Tracing Context Count (Per Minute)  | SkyWalking Go Agent |
| Count Per Minute | meter_sw_go_finished_tracing_context_count            | Finished Tracing Context Count (Per Minute) | SkyWalking Go Agent |
| Count Per Minute | meter_sw_go_created_ignored_context_count             | Created Ignored Context Count (Per Minute)  | SkyWalking Go Agent |
| Count Per Minute | meter_sw_go_finished_ignored_context_count            | Finished Ignored Context Count (Per Minute) | SkyWalking Go Agent |
| Count Per Minute | meter_sw_go_possible_leaked_context_count             | Possible Leak Context Count (Per Minute)    | SkyWalking Go Agent |
| Count Per Minute | meter_sw_go_interceptor_error_count                   | Interceptor Error Count (Per Minute)        | SkyWalking Go Agent |
| ns               | meter_sw_go_tracing_context_execution_time_percentile | Tracing Context Execution Time (ns)         | SkyWalking Go Agent |

## Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/meter-analyzer-config/go-agent.yaml`
The self observability dashboard panel configurations are found in `/config/ui-initialized-templates/so11y_go_agent`.
