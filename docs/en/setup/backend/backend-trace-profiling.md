# Trace Profiling

Trace Profiling is bound within the auto-instrument agent and corresponds to [In-Process Profiling](../../concepts-and-designs/profiling.md#in-process-profiling). 

It is delivered to the agent in the form of a task, allowing for dynamic enabling or disabling. 
Trace Profiling tasks can be created when an `endpoint` within a service experiences high latency. 
When the agent receives the task, it periodically samples the thread stack related to the endpoint when requested. 
Once the sampling is complete, the thread stack within the endpoint can be analyzed to determine the specific line of business code causing the performance issue.

Lean more about the trace profiling, [please read this blog](../../concepts-and-designs/sdk-profiling.md).

## Active in the OAP
OAP and the agent use a brand-new protocol to exchange Trace Profiling data, so it is necessary to start OAP with the following configuration:

```yaml
receiver-profile:
  selector: ${SW_RECEIVER_PROFILE:default}
  default:
```

## Trace Profiling Task with Analysis

To use the Trace Profiling feature, please follow these steps:

1. **Create profiling task**: Use the UI or CLI tool to create a task.
2. **Generate requests**: Ensure that the service has generated requests.
3. **Query task details**: Check that the created task has Trace data generated.
4. **Analyze the data**: Analyze the Trace data to determine where performance bottlenecks exist in the service.

### Create profiling task

Creating a Trace Profiling task is used to notify all agent nodes that execute the service entity which endpoint needs to perform the Trace Profiling feature. 
This Endpoint is typically an HTTP request or an RPC request address.

When creating a task, the following configuration fields are required:

1. **Service**: Which agent under the service needs to be monitored.
2. **Endpoint**: The specific endpoint name, such as "POST:/path/to/request."
3. **Start Time**: The start time of the task, which can be executed immediately or at a future time.
4. **Duration**: The duration of the task execution.
5. **Min Duration Threshold**: The monitoring will only be triggered when the specified endpoint's execution time exceeds this threshold. This effectively prevents the collection of ineffective data due to short execution times.
6. **Dump Period**: The thread stack collection period, which will trigger thread sampling every specified number of milliseconds.
7. **Max Sampling Count**: The maximum number of traces that can be collected in a task. This effectively prevents the program execution from being affected by excessive trace sampling, such as the Stop The World situation in Java.

When the Agent receives a Trace Profiling task from OAP, it automatically generates a log to notify that the task has been acknowledged. The log contains the following field information:

1. **Instance**: The name of the instance where the Agent is located.
2. **Type**: Supports "NOTIFIED" and "EXECUTION_FINISHED", with the current log displaying "NOTIFIED".
3. **Time**: The time when the Agent received the task.

### Generate Requests

At this point, Tracing requests matching the specified Endpoint and other conditions would undergo Profiling.

Notice, whether profiling is thread sensitive, it relies on the agent side implementation. The Java Agent already supports cross-thread requests, so when a request involves cross-thread operations, it would also be periodically sampled for thread stack.

### Query task details

Once the Tracing request is completed, we can query the Tracing data associated with this Trace Profiling task, which includes the following information:

1. **TraceId**: The Trace ID of the current request.
2. **Instance**: The instance to which the current profiling data belongs.
3. **Duration**: The total time taken by the current instance to process the Tracing request.
4. **Spans**: The list of Spans associated with the current Tracing.
   1. **SpanId**: The ID of the current span.
   2. **Parent Span Id**: The ID of the parent span, allowing for a tree structure.
   3. **SegmentId**: The ID of the segment to which the span belongs.
   4. **Refs**: References of the current span, note that it only includes "CROSS_THREAD" type references.
   5. **Service**: The service entity information to which the current span belongs.
   6. **Instance**: The instance entity information to which the current span belongs.
   7. **Time**: The start and end time of the current span.
   8. **Endpoint Name**: The name of the current Span.
   9. **Type**: The type of the current span, either "Entry", "Local", or "Exit".
   10. **Peer**: The remote network address.
   11. **Component**: The name of the component used by the current span.
   12. **Layer**: The layer to which the current span belongs.
   13. **Tags**: The tags information contained in the current span.
   14. **Logs**: The log information in the current span.
   15. **Profiled**: Whether the current span supports Profiling data analysis.
   
### Analyze the data

Once we know which segments can be analyzed for profiling, we can then determine the time ranges available for thread stack analysis based on the "profiled" field in the span. Next, we can provide the following query content to analyze the data:

1. **segmentId**: The segment to be analyzed. Segments are usually bound to individual threads, so we can determine which thread needs to be analyzed.
2. **time range**: Includes the start and end time.

By combining the segmentId with the time range, we can confirm the data for a specific thread during a specific time period. 
This allows us to merge the thread stack data from the specified thread and time range and analyze which lines of code take longer to execute.
The following fields help you understand the program execution:
1. **Id**: Used to identify the current thread stack frame.
2. **Parent Id**: Combined with "id" to determine the hierarchical relationship.
3. **Code Signature**: The method signature of the current thread stack frame.
4. **Duration**: The total time consumed by the current thread stack frame.
5. **Duration Child Excluded**: Excludes the child method calls of the current method, only obtaining the time consumed by the current method.
6. **Count**: The number of times the current thread stack frame was sampled.

If you want to learn more about the thread stack merging mechanism, please read [this documentation](backend-profile-thread-merging.md).

## Exporter

If you find that the results of profiling data are not correct, you can report an issue through [this documentation](../../guides/backend-profile-export.md).