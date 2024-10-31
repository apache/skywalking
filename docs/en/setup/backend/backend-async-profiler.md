# Async Profiler

Async Profiler is bound within the auto-instrument agent and corresponds to [In-Process Profiling](../../concepts-and-designs/profiling.md#in-process-profiling).

It is passed to the proxy in the form of a task, allowing it to be enabled or disabled dynamically.
When service encounters performance issues (cpu usage, memory allocation, locks), async-profiler task can be created.
When the proxy receives a task, it enables Async Profiler for sampling.
After sampling is completed, a flame graph will be generated for performance analysis to determine the specific business code line that caused the performance problem.

## Activate async profiler in the OAP
OAP and the agent use a brand-new protocol to exchange Async Profiler data, so it is necessary to start OAP with the following configuration:

```yaml
receiver-async-profiler:
  selector: ${SW_RECEIVER_ASYNC_PROFILER:default}
  default:
```

## Async Profiler Task with Analysis

To use the Async Profiler feature, please follow these steps:

1. **Create Async Profiler task**: Use the UI or CLI tool to create a task.
2. **Wait agent collect data and upload**: Wait for Async Profiler to collect JFR data and report
3. **Query task progress**: Query the progress of tasks, including analyzing successful and failed instances and task logs
4. **Analyze the data**: Analyze the JFR data to determine where performance bottlenecks exist in the service.

### Create an Async Profiler task

Create an Async Profiler task to notify some java-agent instances in the execution service to start Async Profiler for data collection.

When creating a task, the following configuration fields are required:

1. **serviceId**: Define the service to execute the task.
2. **serviceInstanceIds**: Define which instances need to execute tasks.
3. **duration**: Define the duration of this task (second).
4. **events**: Define which event types this task needs to collect.
5. **execArgs**: Other Async Profiler execution options, e.g. alloc=2k,lock=2s.

When the Agent receives a Async Profiler task from OAP, it automatically generates a log to notify that the task has been acknowledged. The log contains the following field information:

1. **Instance**: The name of the instance where the Agent is located.
2. **Type**: Supports "NOTIFIED" and "EXECUTION_FINISHED" and "JFR_UPLOAD_FILE_TOO_LARGE_ERROR", "EXECUTION_TASK_ERROR", with the current log displaying "NOTIFIED".
3. **Time**: The time when the Agent received the task.

### Wait agent collect data and upload

At this point, async-profiler will trace the following kinds of events:   

1. CPU cycles
2. Hardware and Software performance counters like cache misses, branch misses, page faults, context switches etc.
3. Allocations in Java Heap
4. Contented lock attempts, including both Java object monitors and ReentrantLocks

Finally, java agent will upload the jfr file produced by async-profiler to the oap server for online performance analysis.

### Query task progress

Wait for async-profiler to complete data collection and upload successfully，We can query the execution log of the async-profiler task and the successful and failed instances,which includes the following information:

1. **successInstanceIds**: SuccessInstanceIds gives instances that have executed the task successfully.
2. **errorInstanceIds**: ErrorInstanceIds gives instances that failed to execute the task.
3. **logs**: All task execution logs of the current task.
    1. **id**: The task id.
    2. **instanceId**: InstanceId is the id of the instance which reported this task log.
    3. **instanceName**: InstanceName is the name of the instance which reported this task log.
    4. **operationType**: Contains "NOTIFIED" and "EXECUTION_FINISHED" and "JFR_UPLOAD_FILE_TOO_LARGE_ERROR", "EXECUTION_TASK_ERROR".
    5. **operationTime**: operationTime is the time when the operation occurs.

### Analyze the data

Once we know which instances completed the task, we can then analyze the data by providing the following query:

1. **taskId**: The task id.
2. **instanceIds**: InstanceIds defines the instances to be included for analysis
3. **eventType**: EventType is the specific JFR Event type to be selected for analysis even if multiple events are included in the JFR file.

After the query, the following data would be returned. With the data, it's easy to generate a flame graph:
1. **type**: eventType in query parameters.
2. **elements**: Combined with "id" to determine the hierarchical relationship.
   1. **Id**: Id is the identity of the stack element.
   2. **parentId**: Parent element ID. The dependency relationship between elements can be determined using the element ID and parent element ID.
   3. **codeSignature**: Method signatures in tree nodes.
   4. **total**:The total number of samples of the current tree node, including child nodes.
   5. **self**: The sampling number of the current tree node, excluding samples of the children.