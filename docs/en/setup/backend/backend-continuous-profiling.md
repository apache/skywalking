# Continuous Profiling

Continuous profiling utilizes [eBPF](https://ebpf.io), process monitoring, and other technologies to collect data. 
When the configured threshold is met, it would automatically start profiling tasks. Corresponds to [Continuous Profiling](../../concepts-and-designs/profiling.md#continuous-profiling) in the concepts and designs.
This approach helps identify performance bottlenecks and potential issues in a proactive manner, 
allowing users to optimize their applications and systems more effectively.

## Active in the OAP
Continuous profiling uses the same protocol service as eBPF Profiling, so you only need to ensure that the eBPF Profiling receiver is running.

```yaml
receiver-ebpf:
  selector: ${SW_RECEIVER_EBPF:default}
  default:
```

## Configuration of Continuous Profiling Policy

Continuous profiling can be configured on a service entity, with the following fields in the configuration:

1. **Service**: The service entity for which you want to monitor the processes.
2. **Targets**: Configuration conditions.
   1. **Target Type**: Target profiling type, currently supporting On CPU Profiling, Off CPU Profiling, and Network Profiling.
   2. **Check Items**: Detection conditions, only one of the multiple condition rules needs to be met to start the task.
       1. **Type**: Monitoring type, currently supporting "System Load", "Process CPU", "Process Thread Count", "HTTP Error Rate", "HTTP Avg Response Time". 
       2. **Threshold**: Check if the monitoring value meets the specified expectations. 
       3. **Period**: The time period(seconds) for monitoring data, which can also be understood as the most recent duration. 
       4. **Count**: The number of times(seconds) the threshold is triggered within the detection period, which can also be understood as the total number of times the specified threshold rule is triggered in the most recent duration(seconds). Once the count check is met, the specified Profiling task will be started.
       5. **URI**: For HTTP-related monitoring types, used to filter specific URIs.

## Monitoring

After saving the configuration, the eBPF agent can perform monitoring operations on the processes under the specified service based on the service-level configuration. 

### Metrics

While performing monitoring, the eBPF agent would report the monitoring data to OAP for storage, making it more convenient to understand the real-time monitoring status. The main metrics include:

| Monitor Type | Unit | Description |
|--------------|------|-------------|
| System Load | Load | System load average over a specified period. |
| Process CPU | Percentage | The CPU usage of the process as a percentage. |
| Process Thread Count | Count | The number of threads in the process. |
| HTTP Error Rate | Percentage | The percentage of HTTP requests that result in error responses (e.g., 4xx or 5xx status codes). |
| HTTP Avg Response Time | Millisecond | The average response time for HTTP requests. |

### Threshold With Trigger

In the eBPF agent, data is collected periodically, and the sliding time window technique is used to store the data from the most recent **Period** cycles. 
The **Threshold** rule is used to verify whether the data within each cycle meets the specified criteria. 
If the number of times the conditions are met within the sliding time window exceeds the **Count** value, the corresponding Profiling task would be triggered.

The sliding time window technique ensures that the most recent and relevant data is considered when evaluating the conditions. 
This approach allows for a more accurate and dynamic assessment of the system's performance, 
making it possible to identify and respond to issues in a timely manner. 
By triggering Profiling tasks when specific conditions are met, the system can automatically initiate performance analysis and help uncover potential bottlenecks or areas for improvement.

#### Causes

When the eBPF agent reports a Profiling task, it also reports the reason for triggering the Profiling task, which mainly includes the following information:

1. **Process**: The specific process that triggered the policy.
2. **Monitor Type**: The type of monitoring that was triggered.
3. **Threshold**: The configured threshold value.
4. **Current**: The monitoring value at the time the rule was triggered.

#### Silence Period

Upon triggering a continuous profiling task, the eBPF agent supports a feature that prevents re-triggering tasks within a specified period. 
This feature is designed to prevent an unlimited number of profiling tasks from being initiated if the process continuously reaches the threshold, 
which could potentially cause system issues.