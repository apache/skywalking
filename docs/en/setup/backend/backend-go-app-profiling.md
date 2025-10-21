# Go App Profiling

Go App Profiling uses the Pprof for sampling

Pprof is bound within the auto-instrument agent and corresponds to [In-Process Profiling](../../concepts-and-designs/profiling.md#in-process-profiling).

It is delivered to the agent in the form of a task, allowing it to be enabled or disabled dynamically.
When service encounters performance issues (cpu usage, memory allocation, etc.), Pprof task can be created.
When the agent receives a task, it enables Pprof for sampling.
After sampling is completed, the sampling results are analyzed by requesting the server to render a flame graph for performance 
analysis to determine the specific business code lines that cause performance problems.
Note, tracing profiling in the Go agent relies on the Go runtime’s global CPU sampling used by pprof.
Since only one CPU profiler can run at a time within the same instance, tracing and pprof CPU profiling cannot be enabled simultaneously.
If both are activated on the same instance, one task may fail to start.

## Activate Pprof in the OAP
OAP and the agent use a brand-new protocol to exchange Pprof data, so it is necessary to start OAP with the following configuration:

```yaml
receiver-pprof:
  selector: ${SW_RECEIVER_PPROF:default}
  default:
    # Used to manage the maximum size of the pprof file that can be received, the unit is Byte, default is 30M
    pprofMaxSize: ${SW_RECEIVER_PPROF_MAX_SIZE:31457280}
    # Used to determine whether to receive pprof in memory file or physical file mode
    #
    # The memory file mode have fewer local file system limitations, so they are by default. But it costs more memory.
    #
    # The physical file mode will use less memory when parsing and is more friendly to parsing large files.
    # However, if the storage of the tmp directory in the container is insufficient, the oap server instance may crash.
    # It is recommended to use physical file mode when volume mounting is used or the tmp directory has sufficient storage.
    memoryParserEnabled: ${SW_RECEIVER_PPROF_MEMORY_PARSER_ENABLED:true}
```

## Pprof Task with Analysis

To use the Pprof feature, please follow these steps:

1. **Create Pprof task**: Use the UI or CLI tool to create a task.
2. **Wait agent collect data and upload**: Wait for Pprof to collect pprof data and report.
3. **Query task progress**: Query the progress of tasks, including analyzing successful and failed instances and task logs.
4. **Analyze the data**: Analyze the pprof data to determine where performance bottlenecks exist in the service.

### Create an Pprof task

Create an Pprof task to notify some go-agent instances in the execution service to start Pprof for data collection.

When creating a task, the following configuration fields are required:

1. **serviceId**: Define the service to execute the task.
2. **serviceInstanceIds**: Define which instances need to execute tasks.
3. **duration**: Define the duration of this task in minutes, required for CPU, BLOCK, MUTEX events.
4. **events**: Define which event types this task needs to collect.
5. **dumpPeriod**: Define the period of the pprof dump, required for BLOCK, MUTEX events.

When the Agent receives a Pprof task from OAP, it automatically generates a log to notify that the task has been acknowledged. The log contains the following field information:

1. **Instance**: The name of the instance where the Agent is located.
2. **Type**: Supports "NOTIFIED" and "EXECUTION_FINISHED" and "PPROF_UPLOAD_FILE_TOO_LARGE_ERROR", "EXECUTION_TASK_ERROR", with the current log displaying "NOTIFIED".
3. **Time**: The time when the Agent received the task.

### Wait the agent to collect data and upload

At this point, Pprof will trace the events you selected when you created the task:

1. CPU: samples CPU usage over time to show which functions consume the most processing time.
2. ALLOC, HEAP: 
	- HEAP: a sampling of memory allocations of live objects.
    - ALLOC: a sampling of all past memory allocations.
3. BLOCK, MUTEX: 
	- BLOCK: stack traces that led to blocking on synchronization primitives.
	- MUTEX: stack traces of holders of contended mutexes.
4. GOROUTINE, THREADCREAT:
	- GOROUTINE: stack traces of all current goroutines.
	- THREADCREATE: stack traces that led to the creation of new OS threads.

Finally, the agent will upload the pprof file produced by Pprof to the oap server for online performance analysis.

### Query the profiling task progresses

Wait for Pprof to complete data collection and upload successfully.
We can query the execution logs of the Pprof task and the task status, which includes the following information:

1. **successInstanceIds**: SuccessInstanceIds gives instances that have executed the task successfully.
2. **errorInstanceIds**: ErrorInstanceIds gives instances that failed to execute the task.
3. **logs**: All task execution logs of the current task.
    1. **id**: The task id.
    2. **instanceId**: InstanceId is the id of the instance which reported this task log.
    3. **instanceName**: InstanceName is the name of the instance which reported this task log.
    4. **operationType**: Contains "NOTIFIED" and "EXECUTION_FINISHED" and "PPROF_UPLOAD_FILE_TOO_LARGE_ERROR", "EXECUTION_TASK_ERROR".
    5. **operationTime**: operationTime is the time when the operation occurs.

### Analyze the profiling data

Once some agents completed the task, we can analyze the data through the following query:

1. **taskId**: The task id.
2. **instanceIds**: InstanceIds defines the instances to be included for analysis

After the query, the following data would be returned to render a flame graph:
1. **taskId**: The task id.
2. **elements**: Combined with "id" to determine the hierarchical relationship.
   1. **Id**: Id is the identity of the stack element.
   2. **parentId**: Parent element ID. The dependency relationship between elements can be determined using the element ID and parent element ID.
   3. **codeSignature**: Method signatures in tree nodes.
   4. **total**:The total number of samples of the current tree node, including child nodes.
   5. **self**: The sampling number of the current tree node, excluding samples of the children.