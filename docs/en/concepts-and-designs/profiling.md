# Profiling

The profiling is an on-demand diagnosing method to locate bottleneck of the services.
These typical scenarios usually are suitable for profiling through various profiling tools

1. Some methods slow down the API performance.
2. Too many threads and/or high-frequency I/O per OS process reduce the CPU efficiency.
3. Massive RPC requests block the network to cause responding slowly.
4. Unexpected network requests caused by security issues or codes' bug.

In the SkyWalking landscape, we provided three ways to support profiling within reasonable resource cost.

1. In-process profiling is bundled with auto-instrument agents.
2. Out-of-process profiling is powered by eBPF agent.
3. Continuous profiling is powered by eBPF agent.

## In-process profiling

In-process profiling is primarily provided by auto-instrument agents in the VM-based runtime.

### Tracing Profiling
This feature resolves the issue <1> through capture the snapshot of the thread stacks periodically.
The OAP would aggregate the thread stack per RPC request, and provide a hierarchy graph to indicate the slow methods
based
on continuous snapshot.

The period is usually every 10-100 milliseconds, which is not recommended to be less, due to this capture would usually
cause classical stop-the-world for the VM, which would impact the whole process performance.

Learn more tech details from the post, [**Use Profiling to Fix the Blind Spot of Distributed
Tracing**](sdk-profiling.md).

For now, Java and Python agents support this.

### Java App Profiling

Java App Profiling uses the [AsyncProfiler](https://github.com/async-profiler/async-profiler) for sampling

Async Profiler is a low overhead sampling profiler for Java that does not suffer from Safepoint bias problem. It features HotSpot-specific APIs to collect stack traces and to track memory allocations. The profiler works with OpenJDK and other Java runtimes based on the HotSpot JVM.

Async Profiler can trace the following kinds of events:

- CPU cycles
- Allocations in Java Heap
- Contented lock attempts, including both Java object monitors and ReentrantLocks
- and [more](https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilingModes.md)

Only Java agent support this.

## Out-of-process profiling

Out-of-process profiling leverage [eBPF](https://ebpf.io/) technology with origins in the Linux kernel.
It provides a way to extend the capabilities of the kernel safely and efficiently.

### On-CPU Profiling

On-CPU profiling is suitable for analyzing thread stacks when service CPU usage is high.  
If the stack is dumped more times, it means that the thread stack occupies more CPU resources.

This is pretty similar with in-process profiling to resolve the issue <1>, but it is made out-of-process and based on
Linux eBPF.
Meanwhile, this is made for languages without VM mechanism, which caused not supported by in-process agents, such as,
C/C++, Rust. Golang is a special case, it exposed the metadata of the VM for eBPF, so, it could be profiled.

### Off-CPU Profiling

Off-CPU profiling is suitable for performance issues that are not caused by high CPU usage, but may be on high CPU load.
This profiling aims to resolve the issue <2>.

For example,

1. When there are too many threads in one service, using off-CPU profiling could reveal which threads spend
   more time context switching.
2. Codes heavily rely on disk I/O or remote service performance would slow down the whole process.

Off-CPU profiling provides two perspectives

1. Thread switch count: The number of times a thread switches context. When the thread returns to the CPU, it completes
   one context switch. A thread stack with a higher switch count spends more time context switching.
2. Thread switch duration: The time it takes a thread to switch the context. A thread stack with a higher switch
   duration spends more time off-CPU.

Learn more tech details about ON/OFF CPU profiling from the post, [**Pinpoint Service Mesh Critical Performance Impact
by using eBPF**](ebpf-cpu-profiling.md)

### Network Profiling

Network profiling captures the network packages to analysis traffic at L4(TCP) and L7(HTTP) to recognize network traffic
from a specific process or a k8s pod. Through this traffic analysis, locate the root causes of the issues <3> and <4>.

Network profiling provides

1. Network topology and identify processes.
2. Observe TCP traffic metrics with TLS status.
3. Observe HTTP traffic metrics.
4. Sample HTTP request/response raw data within tracing context.
5. Observe time costs for local I/O costing on the OS. Such as the time of Linux process HTTP request/response.

Learn more tech details from the post, [**Diagnose Service Mesh Network Performance with
eBPF**](../academy/diagnose-service-mesh-network-performance-with-ebpf.md)

## Continuous Profiling

Continuous Profiling utilizes monitoring of system, processes, and network, 
and automatically initiates profiling tasks when conditions meet the configured thresholds and time windows.

### Monitor type

Continuous profiling periodically collects the following types of performance metrics for processes and systems:
1. System Load: Monitor current system load value.
2. Process CPU: Monitor process CPU usage percent, value in [0-100].
3. Process Thread Count: Monitor process thread count.
4. HTTP Error Rate: Monitor the process HTTP(/1.x) response error(response status >= 500) percent, value in [0-100].
5. HTTP Avg Response Time: Monitor the process HTTP(/1.x) response duration(ms).

### Trigger Target

When the collected metric data matches the configured threshold, the following types of profiling tasks could be triggered:
1. On CPU Profiling: Perform eBPF On CPU Profiling on processes that meet the threshold.
2. Off CPU Profiling: Perform eBPF Off CPU Profiling on processes that meet the threshold.
3. Network Profiling: Perform eBPF Network Profiling on all processes within the same instance as the processes that meet the threshold.
