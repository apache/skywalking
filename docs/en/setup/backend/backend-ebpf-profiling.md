# eBPF Profiling

eBPF Profiling utilizes the [eBPF](https://ebpf.io/) technology to monitor applications without requiring any modifications to the application itself. Corresponds to [Out-Process Profiling](../../concepts-and-designs/profiling.md#out-of-process-profiling).

To use eBPF Profiling, the SkyWalking Rover application (eBPF Agent) needs to be installed on the host machine. 
When the agent receives a Profiling task, it starts the Profiling task for the specific application to analyze performance bottlenecks for the corresponding type of Profiling.

Lean more about the eBPF profiling in following blogs:
1. [**Pinpoint Service Mesh Critical Performance Impact by using eBPF**](../../concepts-and-designs/ebpf-cpu-profiling.md)
2. [**Diagnose Service Mesh Network Performance with eBPF**](../../academy/diagnose-service-mesh-network-performance-with-ebpf.md)

## Active in the OAP
OAP and the agent use a brand-new protocol to exchange eBPF Profiling data, so it is necessary to start OAP with the following configuration:

```yaml
receiver-ebpf:
  selector: ${SW_RECEIVER_EBPF:default}
  default:
```

## Profiling type

eBPF Profiling leverages eBPF technology to provide support for the following types of tasks:

1. **On CPU Profiling**: Periodically samples the thread stacks of the current program while it's executing on the CPU using `PERF_COUNT_SW_CPU_CLOCK`.
2. **Off CPU Profiling**: Collects and aggregates thread stacks when the program executes the kernel function `finish_task_switch`.
3. **Network Profiling**: Collects the execution details of the application when performing network-related syscalls, and then aggregates them into a topology map and metrics for different network protocols.

### On CPU Profiling

On CPU Profiling periodically samples the thread stacks of the target program while it's executing on the CPU and aggregates the thread stacks to create a flame graph. 
This helps users identify performance bottlenecks based on the flame graph information.

#### Creating task

When creating an On CPU Profiling task, you need to specify which eligible processes need to be sampled. The required configuration information is as follows:

1. **Service**: The processes under which service entity need to perform Profiling tasks.
2. **Labels**: Specifies which processes with certain labels under the service entity can perform profiling tasks. If left blank, all processes under the specified service will require profiling.
3. **Start Time**: Whether the current task needs to be executed immediately or at a future point in time.
4. **Duration**: The execution time of the current profiling task.

The eBPF agent would periodically request from the OAP whether there are any eligible tasks among all the processes collected by the current eBPF agent. 
When the eBPF agent receives a task, it would start the profiling task with the process.

#### Profiling analyze

Once the eBPF agent starts a profiling task for a specific process, it would periodically collect data and report it to the OAP. 
At this point, a scheduling of task is generated. The scheduling data contains the following information:

1. **Schedule ID**: The ID of current schedule.
2. **Task**: The task to which the current scheduling data belongs.
3. **Process**: The process for which the current scheduling Profiling data is being collected.
4. **Start Time**: The execution start time of the current schedule.
5. **End Time**: The time when the last sampling of the current schedule was completed.

Once the schedule is created, we can use the existing scheduling ID and time range to query the CPU execution situation of the specified process within a specific time period. 
The query contains the following fields:
1. **Schedule ID**: The schedule ID you want to query.
2. **Time**: The start and end times you want to query.

After the query, the following data would be returned. With the data, it's easy to generate a flame graph:
1. **Id**: Element ID.
2. **Parent ID**: Parent element ID. The dependency relationship between elements can be determined using the element ID and parent element ID.
3. **Symbol**: The symbol name of the current element. Usually, it represents the method names of thread stacks in different languages.
4. **Stack Type**: The type of thread stack where the current element is located. Supports `KERNEL_SPACE` and `USER_SPACE`, which represent user mode and kernel mode, respectively.
5. **Dump Count**: The number of times the current element was sampled. The more samples of symbol, means the longer the method execution time.

### Off CPU Profiling

Off CPU Profiling can analyze the thread state when a thread switch occurs in the current process, thereby determining performance loss caused by blocked on I/O, locks, timers, paging/swapping, and other reasons. 
The execution flow between the eBPF agent and OAP in Off CPU Profiling is the same as in On CPU Profiling, but the data content being analyzed is different.

#### Create task

The process of creating an Off CPU Profiling task is the same as creating an On CPU Profiling task, 
with the only difference being that the Profiling task type is changed to OFF CPU Profiling. For specific parameters, please refer to the [previous section](#on-cpu-profiling).

#### Profiling analyze

When the eBPF agent receives a Off CPU Profiling task, it would also collect data and generate a schedule. 
When analyzing data, unlike On CPU Profiling, Off CPU Profiling can generate different flame graphs based on the following two aggregation methods:
1. **By Time**: Aggregate based on the time consumed by each method, allowing you to analyze which methods take longer.
2. **By Count**: Aggregate based on the number of times a method switches to non-CPU execution, allowing you to analyze which methods cause more non-CPU executions for the task.

### Network Profiling

Network Profiling can analyze and monitor network requests related to process, and based on the data, generate topology diagrams, metrics, and other information. 
Furthermore, it can be integrated with existing Tracing systems to enhance the data content.

#### Create task

Unlike On/Off CPU Profiling, Network Profiling requires specifying the instance entity information when creating a task. 
For example, in a Service Mesh, there may be multiple processes under a single instance(Pod), such as an application and Envoy. 
In network analysis, they usually work together, so analyzing them together can give you a better understanding of the network execution situation of the Pod. 
The following parameters are needed:

1. **Instance**: The current Instance entity.
2. **Sampling**: Sampling information for network requests.

Sampling represents how the current system samples raw data and combines it with the existing Tracing system, 
allowing you to see the complete network data corresponding to a Span in Tracing Span. 
Currently, it supports sampling Raw information for Spans using HTTP/1.x as RPC and parsing SkyWalking and Zipkin protocols. 
The sampling information configuration is as follows:

1. **URI Regex**: Only collect requests that match the specified URI. If empty, all requests will be collected.
2. **Min Duration**: Only sample data with a response time greater than or equal to the specified duration. If empty, all requests will be collected.
3. **When 4XX**: Only sample data with a response status code between 400 and 500 (exclusive).
4. **When 5XX**: Only sample data with a response status code between 500 and 600 (exclusive).
5. **Settings**: When network data meets the above rules, how to collect the data.
   1. **Require Complete Request**: Whether to collect request data.
   2. **Max Request Size**: The maximum data size for collecting requests. If empty, all data will be collected.
   3. **Require Complete Response**: Whether to collect response data.
   4. **Max Response Size**: The maximum data size for collecting responses. If empty, all data will be collected.

#### Profiling analysis

After starting the task, the following data can be analyzed:

1. **Topology**: Analyze the data flow and data types when the current instance interacts internally and externally.
2. **TCP Metrics**: Network Layer-4 metrics between two process.
3. **HTTP/1.x Metrics**: If there are HTTP/1.x requests between two nodes, the HTTP/1.x metrics would be analyzed based on the data content.
4. **HTTP Request**: If two nodes use HTTP/1.x and include a tracing system, the tracing data would be extended with events.

##### Topology

The topology can generate two types of data:
1. **Internal entities**: The network call relationships between all processes within the current instance.
2. **Entities and external**: The call relationships between processes inside the entity and external network nodes.

For external nodes, since eBPF can only collect remote IP and port information during data collection, 
OAP can use Kubernetes cluster information to recognize the corresponding **Service** or **Pod** names.

Between two nodes, data flow direction can be detected, and the following types of data protocols can be identified:

1. **HTTP**: Two nodes communicate using HTTP/1.x or HTTP/2.x protocol.
2. **HTTPS**: Two nodes communicate using HTTPS.
3. **TLS**: Two nodes use encrypted data for transition, such as when using `OpenSSL`.
4. **TCP**: There is TCP data transmission between two nodes.

##### TCP Metrics

In the TCP metrics, each metric includes both **client-side** and **server-side** data. The metrics are as follows:

|Name|Unit|Description|
|----|----|------|
|Write CPM|Count|Number of write requests initiated per minute|
|Write Total Bytes|B|Total data size written per minute|
|Write Avg Execute Time|ns|Average execution time for each write operation|
|Write RTT|ns|Round Trip Time (RTT)|
|Read CPM|Count|Number of read requests per minute|
|Read Total Bytes|B|Total data size read per minute|
|Read Avg Execute Time|ns|Average execution time for each read operation|
|Connect CPM|Count|Number of new connections established|
|Connect Execute Time|ns|Time taken to establish a connection|
|Close CPM|Count|Number of closed connections|
|Close Execute Time|ns|Time taken to close a connection|
|Retransmit CPM|Count|Number of data retransmissions per minute|
|Drop CPM|Count|Number of dropped packets per minute|

##### HTTP/1.x Metrics

If there is HTTP/1.x protocol communication between two nodes, the eBPF agent can recognize the request data and parse the following metric information:

|Name|Unit|Description|
|----|----|------|
|Request CPM|Count|Number of requests received per minute|
|Response Status CPM|Count|Number of occurrences of each response status code per minute|
|Request Package Size|B|Average request package data size|
|Response Package Size|B|Average response package data size|
|Client Duration|ns|Time taken for the client to receive a response|
|Server Duration|ns|Time taken for the server to send a response|

##### HTTP Request

If two nodes communicate using the HTTP/1.x protocol, and they employ a distributed tracing system, 
then eBPf agent can collect raw data according to the sampling rules configured in the previous sections.

###### Sampling Raw Data

When the sampling conditions are met, the original request or response data would be collected, including the following fields:

1. **Data Size**: The data size of the current request/response content.
2. **Data Content**: The raw data content. **Non-plain** format content would not be collected.
3. **Data Direction**: The data transfer direction, either Ingress or Egress.
4. **Data Type**: The data type, either Request or Response.
5. **Connection Role**: The current node's role as a client or server.
6. **Entity**: The entity information of the current process.
7. **Time**: The Request or response sent/received time.

###### Syscall Event

When sampling rules are applied, the related Syscall invocations for the request or response would also be collected, including the following information:

1. **Method Name**: System Syscall method names such as `read`, `write`, `readv`, `writev`, etc.
2. **Packet Size**: The current TCP packet size.
3. **Packet Count**: The number of sent or received packets.
4. **Network Interface Information**: The network interface from which the packet was sent.
