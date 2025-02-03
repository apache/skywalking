# JVM Metrics APIs

**Notice, SkyWalking has provided general available [meter APIs](meter.md) for all kinds of metrics. This API is still supported
for forward compatibility only. SkyWalking community would not accept new language specific metric APIs anymore.**

Uplink the JVM metrics, including PermSize, HeapSize, CPU, Memory, etc., every second.

[gRPC service define](https://github.com/apache/skywalking-data-collect-protocol/blob/master/language-agent/JVMMetric.proto)

```protobuf
syntax = "proto3";

package skywalking.v3;

option java_multiple_files = true;
option java_package = "org.apache.skywalking.apm.network.language.agent.v3";
option csharp_namespace = "SkyWalking.NetworkProtocol.V3";
option go_package = "skywalking.apache.org/repo/goapi/collect/language/agent/v3";

import "common/Common.proto";
import "common/Command.proto";

// Define the JVM metrics report service.
service JVMMetricReportService {
    rpc collect (JVMMetricCollection) returns (Commands) {
    }
}

message JVMMetricCollection {
    repeated JVMMetric metrics = 1;
    string service = 2;
    string serviceInstance = 3;
}

message JVMMetric {
    int64 time = 1;
    CPU cpu = 2;
    repeated Memory memory = 3;
    repeated MemoryPool memoryPool = 4;
    repeated GC gc = 5;
    Thread thread = 6;
    Class clazz = 7;
}

message Memory {
    bool isHeap = 1;
    int64 init = 2;
    int64 max = 3;
    int64 used = 4;
    int64 committed = 5;
}

message MemoryPool {
    PoolType type = 1;
    int64 init = 2;
    int64 max = 3;
    int64 used = 4;
    int64 committed = 5;
}

enum PoolType {
    CODE_CACHE_USAGE = 0;
    NEWGEN_USAGE = 1;
    OLDGEN_USAGE = 2;
    SURVIVOR_USAGE = 3;
    PERMGEN_USAGE = 4;
    METASPACE_USAGE = 5;
    ZHEAP_USAGE = 6;
    COMPRESSED_CLASS_SPACE_USAGE = 7;
    CODEHEAP_NON_NMETHODS_USAGE = 8;
    CODEHEAP_PROFILED_NMETHODS_USAGE = 9;
    CODEHEAP_NON_PROFILED_NMETHODS_USAGE = 10;
}

message GC {
    GCPhase phase = 1;
    int64 count = 2;
    int64 time = 3;
}

enum GCPhase {
    NEW = 0;
    OLD = 1;
    NORMAL = 2; // The type of GC doesn't have new and old phases, like Z Garbage Collector (ZGC)
}

// See: https://docs.oracle.com/javase/8/docs/api/java/lang/management/ThreadMXBean.html
message Thread {
    int64 liveCount = 1;
    int64 daemonCount = 2;
    int64 peakCount = 3;
    int64 runnableStateThreadCount = 4;
    int64 blockedStateThreadCount = 5;
    int64 waitingStateThreadCount = 6;
    int64 timedWaitingStateThreadCount = 7;
}

// See: https://docs.oracle.com/javase/8/docs/api/java/lang/management/ClassLoadingMXBean.html
message Class {
    int64 loadedClassCount = 1;
    int64 totalUnloadedClassCount = 2;
    int64 totalLoadedClassCount = 3;
}
```
