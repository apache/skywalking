# library-batch-queue

A partitioned, self-draining queue with type-based dispatch. Replaces the legacy `DataCarrier` across the OAP server.

## Core Design Principles

1. **Describe workload, not threads.** Callers declare intent (`cpuCores(1.0)`, `adaptive()`) and the queue resolves concrete thread/partition counts at runtime.
2. **One queue per concern, many types per queue.** Metrics aggregation, persistence, and export each get one shared queue. Individual metric types register handlers and share the queue's thread pool.
3. **Partition-level isolation.** Each partition is an independent `ArrayBlockingQueue`. The default `typeHash` selector routes all items of the same class to the same partition, so drain-loop dispatch grouping is effectively free.
4. **Adaptive backoff.** Idle drain loops double their sleep interval (`minIdleMs * 2^idleCount`, capped at `maxIdleMs`), resetting on first non-empty drain. No busy-waiting.

## Architecture

```
Producer threads                          Drain threads (scheduler)
  |                                         |
  |  produce(data)                          |  drainLoop(taskIndex)
  |    |                                    |    |
  |    +-- select partition (typeHash)      |    +-- drainTo(combined) from assigned partitions
  |    +-- put/offer into partition         |    +-- dispatch(combined)
  |                                         |         |
  |                                         |         +-- single consumer? -> consumer.consume(batch)
  |                                         |         +-- handler map?     -> group by class, handler.consume(subBatch)
  |                                         |    +-- loop until empty, then re-schedule with backoff
```

## Two Dispatch Modes

### Single consumer mode
Set `config.consumer(handler)`. The entire drained batch goes to one callback. No class-based grouping.
Use for: homogeneous queues where all items are the same type (JDBC batch, single exporter).

### Handler map mode
Call `queue.addHandler(TypeA.class, handlerA)` per type. Drained items are grouped by `getClass()` and dispatched to matching handlers. Unregistered types are logged and dropped.
Use for: shared queues where many metric types coexist (L1 aggregation, L2 persistence, TopN).

## Scheduler Modes

### Dedicated scheduler
The queue owns a `ScheduledThreadPool`. Each thread is assigned a fixed subset of partitions (round-robin). Multiple threads drain concurrently.

```java
BatchQueueConfig.builder()
    .threads(ThreadPolicy.cpuCores(1.0))   // own thread pool
    .partitions(PartitionPolicy.adaptive())
    ...
```

### Shared scheduler
Multiple queues share one `ScheduledThreadPool` (reference-counted, auto-shutdown). Each queue gets 1 drain task on the shared pool. Useful for low-throughput I/O queues.

```java
BatchQueueConfig.builder()
    .sharedScheduler("exporter", ThreadPolicy.fixed(1))  // shared pool
    .partitions(PartitionPolicy.fixed(1))
    ...
```

## Key Classes

| Class | Role |
|-------|------|
| `BatchQueue<T>` | The queue itself. Holds partitions, runs drain loops, dispatches to consumers/handlers. |
| `BatchQueueManager` | Global registry. Creates queues by name, manages shared schedulers with ref-counting. |
| `BatchQueueConfig<T>` | Builder for queue configuration (threads, partitions, buffer, strategy, consumer). |
| `ThreadPolicy` | Resolves thread count: `fixed(N)`, `cpuCores(mult)`, `cpuCoresWithBase(base, mult)`. |
| `PartitionPolicy` | Resolves partition count: `fixed(N)`, `threadMultiply(N)`, `adaptive()`. |
| `PartitionSelector<T>` | Routes items to partitions. Default `typeHash()` groups by class. |
| `HandlerConsumer<T>` | Callback for processing a batch. Has optional `onIdle()` for flush-on-idle. |
| `BufferStrategy` | `BLOCKING` (put, waits) or `IF_POSSIBLE` (offer, drops if full). |
| `BatchQueueStats` | Point-in-time snapshot of queue usage. `totalUsed()`, `topN(n)`, per-partition stats. |
| `QueueErrorHandler<T>` | Optional error callback. If absent, errors are logged. |

## ThreadPolicy

```java
ThreadPolicy.fixed(4)              // exactly 4 threads
ThreadPolicy.cpuCores(1.0)         // 1 thread per CPU core
ThreadPolicy.cpuCoresWithBase(1, 0.25)  // 1 + 0.25 * cores (e.g., 3 on 8-core)
```

Always resolves to >= 1.

## PartitionPolicy

```java
PartitionPolicy.fixed(4)           // exactly 4 partitions
PartitionPolicy.threadMultiply(2)  // 2 * thread count
PartitionPolicy.adaptive()         // grows with addHandler() calls
```

Adaptive growth (default multiplier 25, with 8 threads -> threshold 200):
- 0 handlers -> 8 partitions (= thread count)
- 100 handlers -> 100 partitions (1:1)
- 500 handlers -> 350 partitions (200 + 300/2)

## Usage in the Codebase

### L1 Metrics Aggregation (`MetricsAggregateWorker`)
```
threads:    cpuCores(1.0)        -- 8 threads on 8-core
partitions: adaptive()           -- grows with metric types (~460 for typical OAL+MAL)
bufferSize: 20,000 per partition
strategy:   IF_POSSIBLE
idleMs:     1..50
mode:       handler map (one handler per metric class)
```

### L2 Metrics Persistence (`MetricsPersistentMinWorker`)
```
threads:    cpuCoresWithBase(1, 0.25)  -- 3 threads on 8-core
partitions: adaptive()                 -- grows with metric types
bufferSize: 2,000 per partition
strategy:   IF_POSSIBLE
idleMs:     1..50
mode:       handler map (one handler per metric class)
```

### TopN Persistence (`TopNWorker`)
```
threads:    fixed(1)
partitions: adaptive()         -- grows with TopN types
bufferSize: 1,000 per partition
strategy:   IF_POSSIBLE
idleMs:     10..100
mode:       handler map (one handler per TopN class)
```

### gRPC Remote Client (`GRPCRemoteClient`)
```
threads:    fixed(1)
partitions: fixed(1)
bufferSize: configurable (channelSize * bufferSize)
strategy:   BLOCKING
idleMs:     1..50
mode:       single consumer (sends over gRPC stream)
```

### Exporters (gRPC metrics, Kafka trace, Kafka log)
```
threads:    fixed(1) each
partitions: fixed(1) each
bufferSize: configurable (default 20,000)
strategy:   BLOCKING (gRPC) / IF_POSSIBLE (Kafka)
idleMs:     1..200
mode:       single consumer
```

### JDBC Batch DAO (`JDBCBatchDAO`)
```
threads:    fixed(N) where N = asyncBatchPersistentPoolSize (default 4)
partitions: fixed(N) (1 partition per thread)
bufferSize: 10,000 per partition
strategy:   BLOCKING
idleMs:     1..20
mode:       single consumer (JDBC batch flush)
```

## Lifecycle

1. `BatchQueueManager.create(name, config)` -- creates and starts drain loops immediately
2. `queue.addHandler(type, handler)` -- registers type handler (adaptive: may grow partitions)
3. `queue.produce(data)` -- routes to partition, blocks or drops per strategy
4. Drain loops run continuously, dispatching batches to consumers/handlers
5. `BatchQueueManager.shutdown(name)` -- stops drain, final flush, releases scheduler
6. `BatchQueueManager.shutdownAll()` -- called during OAP server shutdown
