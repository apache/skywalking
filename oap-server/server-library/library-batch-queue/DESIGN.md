# library-batch-queue Design Proposal

## Goal

Replace `library-datacarrier-queue` with a unified, simpler batch queue library that reduces thread
usage while preserving all required capabilities.

## Problem Statement

DataCarrier has two consumption modes with separate code paths:

1. **Simple mode** (`ConsumeDriver`): Each DataCarrier gets dedicated consumer thread(s).
   Used by TopNWorker, GRPCRemoteClient, JDBCBatchDAO, exporters.
2. **Pool mode** (`BulkConsumePool`): Multiple DataCarriers share a thread pool.
   Used by MetricsAggregateWorker (L1) and MetricsPersistentMinWorker (L2).

On an 8-core production machine, this creates **47+ DataCarrier consumer threads**:

| Source                         | Threads | Mode   |
|--------------------------------|---------|--------|
| L1 OAL aggregation pool       | 24      | Pool   |
| L2 OAL persistent pool        | 2       | Pool   |
| L1 MAL aggregation pool       | 2       | Pool   |
| L2 MAL persistent pool        | 1       | Pool   |
| TopNWorker (per type)          | 5-10    | Simple |
| GRPCRemoteClient (per peer)   | 2-4     | Simple |
| JDBCBatchDAO                   | 2-4     | Simple |
| Exporters (gRPC/Kafka)         | 0-3     | Simple |
| **Total**                      | **~38-48** |     |

Key issues:
- Simple mode wastes threads: each queue gets a dedicated thread even though most are idle
  (sleeping in 200ms polling loop).
- Pool mode creates one DataCarrier (with Channels) per metric type. With 100+ metric types,
  pool threads must iterate through all assigned channels even when most are empty — wasted CPU.
- Two completely separate code paths for the same produce-consume pattern.
- Pool mode has static assignment — no rebalancing after initial allocation.

## Design

### Architecture Overview

```
 BatchQueueManager (global singleton registry + lazy shared schedulers)
   │
   │  Shared schedulers (created lazily on first queue reference):
   │  ├── "IO_POOL" ──> ScheduledExecutorService (cpuCores(0.5) → 4 threads on 8-core)
   │  │     Created when first queue calls sharedScheduler("IO_POOL", cpuCores(0.5)).
   │  │     Shared by all I/O queues:
   │  │     - GRPCRemoteClient.*       (gRPC to peer OAP nodes)
   │  │     - GRPCMetricsExporter      (gRPC metrics export)
   │  │     - KafkaLogExporter         (Kafka log export)
   │  │     - KafkaTraceExporter       (Kafka trace export)
   │  │     - JDBCBatchDAO             (JDBC batch writes)
   │  │
   │
   │  Queues:
   │
   ├── "METRICS_L1_AGGREGATION" ──> BatchQueue<Metrics>
   │       │  threads:     cpuCores(1.0) → 8 on 8-core
   │       │  partitions:  threadMultiply(2) → 16 on 8-core
   │       │  strategy:    IF_POSSIBLE
   │       │  handlerMap:  { ServiceRespTimeMetrics.class -> handler-A (OAL),
   │       │                  ServiceCpmMetrics.class     -> handler-B (OAL),
   │       │                  MeterMetrics_xxx.class      -> handler-C (MAL), ... }
   │       └── OAL and MAL metrics share the same L1 queue and thread pool.
   │
   ├── "METRICS_L2_PERSISTENT" ──> BatchQueue<Metrics>
   │       │  threads:     cpuCores(0.25) → 2 on 8-core
   │       │  partitions:  threadMultiply(2) → 4 on 8-core
   │       │  strategy:    BLOCKING
   │       │  handlerMap:  { ServiceRespTimeMetrics.class -> handler-D (OAL),
   │       │                  MeterMetrics_xxx.class      -> handler-E (MAL), ... }
   │       └── OAL and MAL metrics share the same L2 queue and thread pool.
   │
   ├── "TOPN_WORKER" ──> BatchQueue<TopN>
   │       │  threads:     fixed(1)
   │       │  partitions:  fixed(1)
   │       │  strategy:    BLOCKING
   │       │  handlerMap:  { DatabaseSlowStatement.class -> handler-F,
   │       │                  DatabaseSlowSql.class      -> handler-G, ... }
   │       └── drain: drainTo → groupBy(class) → dispatch to handler
   │       (TopN is in-memory ranking computation — all types share one thread)
   │
   ├── "GRPCRemoteClient.peer1" ──> BatchQueue<RemoteMessage> (shared="IO_POOL", partitions=1, BLOCKING)
   │       │  scheduler:   shared "IO_POOL"
   │       │  consumer:    direct consumer for RemoteMessage
   │       └── drain: drainTo → direct consumer
   │
   └── "JDBCBatchDAO" ──> BatchQueue<PrepareRequest> (shared="IO_POOL", partitions=1, BLOCKING)
           │  scheduler:   shared "IO_POOL"
           │  consumer:    direct consumer for PrepareRequest
           └── same
```

### Core Concept

**One queue type, one config, two scheduler modes.**

- **`BatchQueueManager`** is the global singleton registry. It also manages named shared
  schedulers for low-throughput queues. Users call `createIfAbsent(name, config)` to
  get a named `BatchQueue`.
- **`BatchQueue<T>`** has N partitions (configurable, default 1) and a handler map.
  Producers round-robin data into partitions. On drain, each batch is **grouped by
  message class** and dispatched to the handler registered for that class.
- **Handler registration** via `queue.addHandler(Class, HandlerConsumer)`.
  Each worker provides its own handler instance for its specific type.

The handler map pattern works the same way regardless of partition count:
- `threadMultiply(2)` with 100+ handlers → metrics aggregation (many types, shared partitions)
- `partitions=1` with N handlers → TopN (multiple types, low throughput, shared 1 thread)
- `partitions=1` with 1 consumer → I/O queue (gRPC client, exporter, JDBC)

No need for separate queue classes. The difference is just configuration.

### Why Shared Partitions + Handler Map

In the old BulkConsumePool model with 100+ metric types:

```
Pool Thread-0 assigned: [service_resp_time channels, service_cpm channels, ...]
Pool Thread-1 assigned: [endpoint_resp_time channels, endpoint_cpm channels, ...]
...
Each thread iterates ALL assigned channels per cycle, even if most are empty.
```

In the new model:

```
Partition-0: mixed data from all metric types (round-robin)
Partition-1: mixed data from all metric types
...
Partition-N: mixed data from all metric types

On drain of Partition-K:
  batch = drainTo(list)                          // all data, mixed types
  grouped = batch.groupBy(item.getClass())       // group by metric class
  for each (class, items) in grouped:
    handler = handlerMap.get(class)              // lookup registered handler
    handler.consume(items)                       // dispatch to the right worker
```

Benefits:
- Partitions are created based on parallelism needs, not metric count.
  16 partitions (8 threads * 2) serve 100+ metric types.
- No empty channel iteration — every partition gets data.
- Handlers are registered on-demand. Adding a new metric type is just
  `addHandler(NewMetrics.class, handlerInstance)`.
- Each handler still processes only its own metric type's data in isolation.
- I/O queues use the same structure with `partitions=1` and a direct consumer.

### API

```java
// ── Metrics aggregation (dedicated pool, many types, handler map dispatch) ──

BatchQueue<Metrics> l1Queue = BatchQueueManager.createIfAbsent(
    "METRICS_L1_AGGREGATION",
    BatchQueueConfig.<Metrics>builder()
        .threads(ThreadPolicy.cpuCores(1.0))              // 1x CPU cores (e.g. 8 on 8-core)
        .partitions(PartitionPolicy.threadMultiply(2))    // 2x resolved threads = 16 on 8-core
        .bufferSize(10_000)
        .strategy(BufferStrategy.IF_POSSIBLE)
        .errorHandler((data, t) -> log.error(t.getMessage(), t))
        .build()
);

// Each MetricsAggregateWorker registers its inner class handler for its metric class.
// Called per metric type in MetricsStreamProcessor.create() (100+ times):
l1Queue.addHandler(metricsClass, new L1Handler());  // L1Handler is worker's inner class

// Produce — data goes into a partition by round-robin
// Adaptive backoff ensures fast re-poll (minIdleMs) when data is flowing.
l1Queue.produce(metricsData);

// ── TopN (shared queue — all TopN types share one thread, handler map dispatch) ──

BatchQueue<TopN> topNQueue = BatchQueueManager.createIfAbsent(
    "TOPN_WORKER",
    BatchQueueConfig.<TopN>builder()
        .threads(ThreadPolicy.fixed(1))                   // all TopN types share 1 thread
        .partitions(PartitionPolicy.fixed(1))
        .bufferSize(1000)
        .strategy(BufferStrategy.BLOCKING)
        .errorHandler((data, t) -> log.error(t.getMessage(), t))
        .build()
);

// Each TopNWorker registers its handler for its specific TopN class (5-10 types):
topNQueue.addHandler(topNClass, new TopNHandler());  // TopNHandler is worker's inner class

topNQueue.produce(topNData);

// ── I/O queues (shared scheduler, single consumer) ──
// sharedScheduler() specifies both name and ThreadPolicy. BatchQueueManager creates the
// shared ScheduledExecutorService on first reference, reuses it for subsequent queues.
// No separate createSharedScheduler() call needed — no startup ordering dependency.
//
// All these queues share the same "IO_POOL" scheduler:
//   GRPCRemoteClient.*   — gRPC streaming to peer OAP nodes
//   GRPCMetricsExporter  — gRPC metrics export
//   KafkaLogExporter     — Kafka log export
//   KafkaTraceExporter   — Kafka trace export
//   JDBCBatchDAO         — JDBC batch writes to database

BatchQueue<RemoteMessage> grpcQueue = BatchQueueManager.createIfAbsent(
    "GRPCRemoteClient.peer1",
    BatchQueueConfig.<RemoteMessage>builder()
        .sharedScheduler("IO_POOL", ThreadPolicy.cpuCores(0.5))  // creates IO_POOL on first use
        .partitions(PartitionPolicy.fixed(1))
        .bufferSize(10_000)
        .strategy(BufferStrategy.BLOCKING)
        .consumer(new RemoteMessageHandler())
        .errorHandler((data, t) -> log.error(t.getMessage(), t))
        .build()
);

// Another queue referencing the same "IO_POOL" — reuses existing scheduler.
// If ThreadPolicy differs from the first creator, logs a warning (first one wins).
BatchQueue<PrepareRequest> jdbcQueue = BatchQueueManager.createIfAbsent(
    "JDBCBatchDAO",
    BatchQueueConfig.<PrepareRequest>builder()
        .sharedScheduler("IO_POOL", ThreadPolicy.cpuCores(0.5))  // reuses existing IO_POOL
        .partitions(PartitionPolicy.fixed(1))
        .bufferSize(10_000)
        .strategy(BufferStrategy.BLOCKING)
        .consumer(new JDBCBatchHandler())
        .errorHandler((data, t) -> log.error(t.getMessage(), t))
        .build()
);

// ── Lifecycle ──

BatchQueueManager.shutdown("METRICS_L1_AGGREGATION");
BatchQueueManager.shutdownAll();  // shuts down all queues + shared schedulers
```

### Classes

```
library-batch-queue/
  src/main/java/org/apache/skywalking/oap/server/library/batchqueue/
    BatchQueueManager.java       — Global singleton registry + named shared schedulers
    BatchQueue.java              — Partitioned queue with dedicated or shared scheduler + handler map dispatch
    BatchQueueConfig.java        — Builder: threads/sharedScheduler, partitions, bufferSize, strategy
    ThreadPolicy.java            — Fixed or CPU-relative thread count: fixed(N) / cpuCores(double)
    PartitionPolicy.java         — Fixed or thread-relative partition count: fixed(N) / threadMultiply(N)
    HandlerConsumer.java         — Interface: void consume(List<T>), default void onIdle()
    BufferStrategy.java          — BLOCKING / IF_POSSIBLE
    QueueErrorHandler.java       — Functional interface: void onError(List<T>, Throwable)
```

### BatchQueueManager

```java
/**
 * Global registry for batch queues and shared schedulers.
 * Thread-safe. Queues are created by name and shared across modules.
 *
 * Two scheduler modes:
 * - Dedicated: queue creates its own ScheduledExecutorService (for high-throughput queues).
 *   Configured via BatchQueueConfig.threads(ThreadPolicy).
 * - Shared: queue uses a named shared scheduler managed by this manager (for low-throughput queues).
 *   Configured via BatchQueueConfig.sharedScheduler(name, ThreadPolicy).
 *   Multiple queues referencing the same name share one ScheduledExecutorService.
 *
 * Shared schedulers are created lazily on first queue reference — no separate
 * setup step needed. This eliminates startup ordering dependencies.
 */
public class BatchQueueManager {
    private static final ConcurrentHashMap<String, BatchQueue<?>> queues = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ScheduledExecutorService> sharedSchedulers = new ConcurrentHashMap<>();

    /**
     * Get or create a shared scheduler. Called internally by BatchQueue constructor
     * when config specifies sharedScheduler(name, threads).
     *
     * - First call with a given name: creates the ScheduledExecutorService using
     *   threads.resolve() and caches it.
     * - Subsequent calls with the same name: returns the cached scheduler.
     *   If the ThreadPolicy differs, logs a warning (first one wins).
     *
     * Thread-safe (ConcurrentHashMap.computeIfAbsent).
     *
     * Shared schedulers are owned by BatchQueueManager, NOT by any individual queue.
     * They are destroyed only by shutdownAll() — never by individual queue shutdown.
     */
    static ScheduledExecutorService getOrCreateSharedScheduler(String name, ThreadPolicy threads);

    /**
     * Create a new named queue. Throws if name already exists.
     */
    public static <T> BatchQueue<T> create(String name, BatchQueueConfig<T> config);

    /**
     * Create if not present. Returns existing queue if name is taken.
     *
     * If the queue already exists, validates consistency:
     * - Consumption mode conflict: throws IllegalStateException if the existing queue
     *   uses direct consumer mode (config.consumer set) but the new config does not,
     *   or vice versa. These two modes are mutually exclusive per queue.
     * - Infrastructure settings: logs a warning if threads, partitions,
     *   bufferSize, or strategy differ between the existing and new config.
     */
    public static <T> BatchQueue<T> createIfAbsent(String name, BatchQueueConfig<T> config);

    /**
     * Get an existing queue by name. Returns null if not found.
     */
    public static <T> BatchQueue<T> get(String name);

    /**
     * Shutdown and remove a single queue by name.
     * - Dedicated scheduler: shut down together with the queue.
     * - Shared scheduler: NOT shut down. It is owned by BatchQueueManager
     *   and may still be used by other queues.
     */
    public static void shutdown(String name);

    /**
     * Shutdown all queues and all shared schedulers. Called during OAP server shutdown.
     *
     * Order:
     * 1. Set running=false on all queues (stops drain loops from rescheduling)
     * 2. Final drain of remaining data in each queue
     * 3. Shut down all dedicated schedulers
     * 4. Shut down all shared schedulers
     * 5. Clear both registries
     */
    public static void shutdownAll();
}
```

### BatchQueue

```java
/**
 * A partitioned queue with handler-map-based dispatch.
 *
 * The scheduler is either dedicated (owned by this queue) or shared
 * (managed by BatchQueueManager, shared with other queues).
 *
 * Partitions are created based on configured parallelism (default 1).
 * Producers round-robin data across partitions.
 * On drain, each batch is grouped by message class and dispatched to the
 * registered handler for that class.
 *
 * Works uniformly for all use cases:
 * - shared scheduler, partitions=1, one consumer    → I/O queue (gRPC, Kafka, JDBC)
 * - dedicated fixed(1), partitions=1, many handlers → TopN (all types share 1 thread)
 * - dedicated cpuCores(1.0), threadMultiply(2), many handlers → metrics aggregation
 */
public class BatchQueue<T> {
    private final String name;
    private final ScheduledExecutorService scheduler;
    private final boolean dedicatedScheduler;  // true = owned by this queue, false = shared
    private final ArrayBlockingQueue<T>[] partitions;
    private final ConcurrentHashMap<Class<? extends T>, HandlerConsumer<T>> handlerMap;
    private final BatchQueueConfig<T> config;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private volatile boolean running;

    /**
     * Partition assignment per drain task. Each drain task owns a set of partition indices.
     *
     * Dedicated mode: one drain task per thread, partitions assigned round-robin.
     *   threads=4, partitions=8: task[0]→[0,4], task[1]→[1,5], task[2]→[2,6], task[3]→[3,7]
     *
     * Shared mode: single drain task covering ALL partitions (partitions typically = 1).
     *   assignedPartitions = { [0] }  (one task, one partition)
     */
    private final int[][] assignedPartitions;

    /**
     * Per-task count of consecutive idle cycles (all assigned partitions empty).
     * Used to compute adaptive backoff sleep interval.
     */
    private final int[] consecutiveIdleCycles;

    BatchQueue(String name, BatchQueueConfig<T> config) {
        this.name = name;
        this.config = config;
        this.handlerMap = new ConcurrentHashMap<>();

        int taskCount;
        if (config.getSharedSchedulerName() != null) {
            // Shared scheduler mode: get-or-create shared scheduler from BatchQueueManager.
            ScheduledExecutorService sharedScheduler = BatchQueueManager.getOrCreateSharedScheduler(
                config.getSharedSchedulerName(), config.getSharedSchedulerThreads());

            int partitionCount = config.getPartitions().resolve(1, 0);
            this.partitions = new ArrayBlockingQueue[partitionCount];
            for (int i = 0; i < partitions.length; i++) {
                partitions[i] = new ArrayBlockingQueue<>(config.getBufferSize());
            }

            this.scheduler = sharedScheduler;
            this.dedicatedScheduler = false;
            taskCount = 1;
            this.assignedPartitions = new int[][] {
                java.util.stream.IntStream.range(0, partitions.length).toArray()
            };
        } else {
            // Dedicated scheduler mode: resolve threads and partitions.
            int threadCount = config.getThreads().resolve();  // cpuCores(1.0) → 8 on 8-core
            int partitionCount = config.getPartitions().resolve(threadCount, 0);

            // Validation: if partitions < threads, cut threads to match and warn.
            if (partitionCount < threadCount) {
                log.warn("BatchQueue[{}]: partitions({}) < threads({}), "
                    + "reducing threads to {}",
                    name, partitionCount, threadCount, partitionCount);
                threadCount = partitionCount;
            }

            this.partitions = new ArrayBlockingQueue[partitionCount];
            for (int i = 0; i < partitions.length; i++) {
                partitions[i] = new ArrayBlockingQueue<>(config.getBufferSize());
            }

            this.scheduler = Executors.newScheduledThreadPool(
                threadCount,
                new ThreadFactoryBuilder().setNameFormat("BatchQueue-" + name + "-%d").build()
            );
            this.dedicatedScheduler = true;
            taskCount = threadCount;

            // Assign partitions to threads by round-robin.
            //   threads=4, partitions=8: task[0]→[0,4], task[1]→[1,5], ...
            //   threads=4, threadMultiply(2)=8: same
            //   threads=8, partitions=8: task[0]→[0], task[1]→[1], ...
            this.assignedPartitions = new int[taskCount][];
            List<List<Integer>> assignment = new ArrayList<>();
            for (int t = 0; t < taskCount; t++) {
                assignment.add(new ArrayList<>());
            }
            for (int p = 0; p < partitions.length; p++) {
                assignment.get(p % taskCount).add(p);
            }
            for (int t = 0; t < taskCount; t++) {
                assignedPartitions[t] = assignment.get(t).stream().mapToInt(Integer::intValue).toArray();
            }
        }

        // Kick off one self-rescheduling drain task per assignment.
        this.consecutiveIdleCycles = new int[taskCount];
        this.running = true;
        for (int t = 0; t < taskCount; t++) {
            scheduleDrain(t);
        }
    }

    /**
     * Schedule the next drain with adaptive backoff.
     *
     * Idle count 0 (just had data):  sleep = minIdleMs       (e.g.  5ms)
     * Idle count 1:                  sleep = minIdleMs * 2    (e.g. 10ms)
     * Idle count 2:                  sleep = minIdleMs * 4    (e.g. 20ms)
     * ...
     * Idle count N:                  sleep = min(minIdleMs * 2^N, maxIdleMs)
     */
    private void scheduleDrain(int taskIndex) {
        int idleCount = consecutiveIdleCycles[taskIndex];
        long delay = Math.min(
            config.getMinIdleMs() * (1L << Math.min(idleCount, 20)),
            config.getMaxIdleMs()
        );
        scheduler.schedule(
            () -> drainLoop(taskIndex),
            delay,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Register a handler for a specific message class type.
     * Multiple metric types can each register their own handler instance.
     */
    public void addHandler(Class<? extends T> type, HandlerConsumer<T> handler);

    /**
     * Produce data into a partition (round-robin).
     * BLOCKING: waits if the selected partition is full (queue.put).
     * IF_POSSIBLE: returns false if full (queue.offer).
     */
    public boolean produce(T data) {
        int index = Math.abs(roundRobinIndex.getAndIncrement() % partitions.length);
        if (config.getStrategy() == BufferStrategy.BLOCKING) {
            partitions[index].put(data);  // blocks until space available
            return true;
        } else {
            return partitions[index].offer(data);  // returns false if full
        }
    }

    /**
     * Drain loop for one thread: iterates ALL assigned partitions in a round-robin loop.
     * Only stops when ALL assigned partitions are empty in a full cycle.
     *
     * Each cycle:
     *   1. Drain ALL assigned partitions into one combined batch
     *   2. If combined batch is empty → all partitions empty → onIdle, break
     *   3. dispatch(combined batch) → handlers get ALL data of their type as one list
     *   4. Loop back to step 1 (more data may have arrived during dispatch)
     *
     * Example: Thread-0 owns partitions [0, 4]
     *   cycle 1: drain(0)→[A1,B1,A2]  drain(4)→[C1,A3]
     *            combined = [A1,B1,A2,C1,A3]
     *            dispatch → groupBy class:
     *              handlerA.consume([A1,A2,A3])   ← all A's in one call
     *              handlerB.consume([B1])
     *              handlerC.consume([C1])
     *   cycle 2: drain(0)→[A4]  drain(4)→[]
     *            combined = [A4] (not empty → dispatch)
     *   cycle 3: drain(0)→[]  drain(4)→[]
     *            combined = [] → onIdle, reschedule
     */
    void drainLoop(int taskIndex) {
        int[] myPartitions = assignedPartitions[taskIndex];
        try {
            while (running) {
                // Step 1: drain ALL assigned partitions into one batch
                List<T> combined = new ArrayList<>();
                for (int partitionIndex : myPartitions) {
                    partitions[partitionIndex].drainTo(combined);
                }

                // Step 2: if nothing across all partitions, we are idle
                if (combined.isEmpty()) {
                    consecutiveIdleCycles[taskIndex]++;
                    notifyIdle();
                    break;  // reschedule with backoff
                }

                // Had data → reset backoff so next reschedule is fast
                consecutiveIdleCycles[taskIndex] = 0;

                // Step 3: dispatch the combined batch
                dispatch(combined);
                // Step 4: loop immediately — more data may have arrived
            }
        } finally {
            if (running) {
                scheduleDrain(taskIndex);
            }
        }
    }

    void shutdown() {
        running = false;
        // Final drain of remaining data across all partitions
        List<T> combined = new ArrayList<>();
        for (int i = 0; i < partitions.length; i++) {
            partitions[i].drainTo(combined);
        }
        if (!combined.isEmpty()) {
            dispatch(combined);
        }
        // Only shut down the scheduler if this queue owns it.
        // Shared schedulers are shut down by BatchQueueManager.shutdownAll().
        if (dedicatedScheduler) {
            scheduler.shutdown();
        }
    }
}
```

**Dispatch and idle notification:**

```java
private void dispatch(List<T> batch) {
    // Direct consumer mode: pass the whole batch, no groupBy overhead.
    if (config.getConsumer() != null) {
        try {
            config.getConsumer().consume(batch);
        } catch (Throwable t) {
            config.getErrorHandler().onError(batch, t);
        }
        return;
    }

    // Handler map mode: group by class type and dispatch to registered handlers.
    Map<Class<?>, List<T>> grouped = new HashMap<>();
    for (T item : batch) {
        grouped.computeIfAbsent(item.getClass(), k -> new ArrayList<>()).add(item);
    }

    for (Map.Entry<Class<?>, List<T>> entry : grouped.entrySet()) {
        HandlerConsumer<T> handler = handlerMap.get(entry.getKey());
        if (handler != null) {
            try {
                handler.consume(entry.getValue());
            } catch (Throwable t) {
                config.getErrorHandler().onError(entry.getValue(), t);
            }
        }
    }
}

private void notifyIdle() {
    if (config.getConsumer() != null) {
        config.getConsumer().onIdle();
    } else {
        handlerMap.values().forEach(HandlerConsumer::onIdle);
    }
}
```

**Consumer workflow (end-to-end):**

```
Producer threads                     Consumer thread (Thread-0, owns partitions [0, 4])
──────────────                       ─────────────────────────────────────────────────

produce(A1) ──offer/put──►  Partition-0: [A1, C1, B1]
produce(B1) ──offer/put──►  Partition-4: [A2, A3]
produce(C1) ──┘  (round-robin)

                                 ┌─── scheduleDrain(0) after adaptive backoff delay
                                 │
                                 ▼
                              drainLoop(taskIndex=0)
                                 │
                        ┌────────┴───────────────────────────────────────────┐
                        │  while (running):                                  │
                        │                                                    │
                        │  ── Step 1: drain ALL assigned partitions ──       │
                        │    combined = []                                   │
                        │    Partition-0.drainTo(combined) → [A1, C1, B1]    │
                        │    Partition-4.drainTo(combined) → [A1,C1,B1,A2,A3]│
                        │                                                    │
                        │  ── Step 2: check if empty ──                      │
                        │    combined not empty → dispatch                   │
                        │                                                    │
                        │  ── Step 3: dispatch(combined) ──                  │
                        │    ┌─ config.consumer set?                         │
                        │    │  YES → consumer.consume(combined) ─── done    │
                        │    │  NO  → groupBy class:                         │
                        │    │        MetricA.class → [A1, A2, A3]           │
                        │    │        MetricB.class → [B1]                   │
                        │    │        MetricC.class → [C1]                   │
                        │    │        for each group:                        │
                        │    │          handler = handlerMap.get(class)      │
                        │    │          handler.consume(group)               │
                        │    │          ↓                                    │
                        │    │     L1Handler_A.consume([A1, A2, A3])         │
                        │    │       → workerA.onWork([A1, A2, A3])          │
                        │    │         → mergeDataCache.accept(A1)           │
                        │    │         → mergeDataCache.accept(A2)           │
                        │    │         → mergeDataCache.accept(A3)           │
                        │    │         → flush() if period elapsed           │
                        │    │     L1Handler_B.consume([B1])                 │
                        │    │       → workerB.onWork([B1])                  │
                        │    │     L1Handler_C.consume([C1])                 │
                        │    │       → workerC.onWork([C1])                  │
                        │    └───────────────────────────────────────────── │
                        │                                                    │
                        │  ── Step 4: loop immediately (more may have come)──│
                        │                                                    │
                        │  ── next cycle ──                                  │
                        │    combined = []                                   │
                        │    Partition-0.drainTo(combined) → []              │
                        │    Partition-4.drainTo(combined) → []              │
                        │    combined is empty                               │
                        │      → notifyIdle()                                │
                        │          handlerMap.values().forEach(::onIdle)     │
                        │          ↓                                         │
                        │        L1Handler_A.onIdle()                        │
                        │          → workerA.flush() (force flush cache)     │
                        │        L1Handler_B.onIdle()                        │
                        │          → workerB.flush()                         │
                        │        ...                                         │
                        │      consecutiveIdleCycles[0]++  (e.g. now = 1)    │
                        │      break                                         │
                        └────────────────────────────────────────────────────┘
                                 │
                                 ▼  (finally block)
                              scheduleDrain(0)
                                 │
                                 ├─ idleCount=0 (just had data): wait  5ms  ← fast re-poll
                                 ├─ idleCount=1:                  wait 10ms
                                 ├─ idleCount=2:                  wait 20ms
                                 ├─ idleCount=3:                  wait 40ms
                                 ├─ idleCount=4:                  wait 80ms
                                 ├─ idleCount=5:                  wait 160ms
                                 ├─ idleCount=6+:                 wait 200ms ← capped at maxIdleMs
                                 │
                                 ▼
                              drainLoop again
                                 │
                              (if data found → idleCount resets to 0 → back to fast polling)
```

**Key points:**
- Each cycle drains ALL assigned partitions into one combined batch before dispatching.
- `dispatch()` is called once per cycle with all data from all partitions combined.
- In handler map mode, `groupBy(class)` collects all items of the same type across all
  partitions into one list. The handler receives ALL available data of its type in a single
  `consume()` call — e.g., `[A1, A2, A3]` not three separate calls.
- The handler's `consume()` runs **synchronously** inside the drain thread. The handler
  (an inner class of the worker) directly accesses the worker's fields — merge cache,
  counters, flush logic — with no extra threading.
- If any partition had data, loop immediately to check all partitions again.
- `onIdle()` fires only when ALL assigned partitions are empty in a full cycle, giving
  handlers a chance to flush periodic caches (e.g., L1 aggregation merge cache → nextWorker).
- **Adaptive backoff**: after data, re-poll in `minIdleMs` (5ms). Each consecutive empty
  cycle doubles the sleep, capping at `maxIdleMs` (200ms). Data resets to fast polling.

**Two consumption modes, same queue class:**
- **Direct consumer** (`config.consumer` set) — whole batch goes to one handler, no groupBy.
  Use for I/O queues where all data is the same type (gRPC, Kafka, JDBC).
- **Handler map** (`addHandler` called) — batch grouped by class, dispatched per type.
  Use for metrics aggregation (L1/L2) and TopN with many types sharing partitions.

If both are set, direct consumer takes priority (handler map is ignored).
If neither is set, data is drained but silently dropped.

### BatchQueueConfig

```java
@Builder
public class BatchQueueConfig<T> {
    /**
     * Thread policy for a dedicated ScheduledExecutorService.
     * Resolved at queue construction time. Examples:
     *   ThreadPolicy.fixed(8)       → always 8 threads
     *   ThreadPolicy.cpuCores(1.0)  → 1x available CPU cores (8 on 8-core)
     *   ThreadPolicy.cpuCores(0.25) → 0.25x CPU cores (2 on 8-core, min 1)
     *
     * When set, the queue creates its own scheduler.
     * When null, sharedScheduler must be set — the queue uses a shared scheduler.
     *
     * Use dedicated pools for high-throughput queues (metrics L1/L2 aggregation)
     * where you need guaranteed thread capacity.
     */
    private ThreadPolicy threads;

    /**
     * Shared scheduler name and its ThreadPolicy. Set via the builder method
     * sharedScheduler(name, threads) which populates both fields together.
     * Mutually exclusive with the threads field above.
     *
     * When set, the queue registers its drain tasks on a shared
     * ScheduledExecutorService managed by BatchQueueManager.
     * The shared scheduler is created lazily on first queue reference —
     * no separate setup step needed. Subsequent queues with the same name
     * reuse the existing scheduler (ThreadPolicy mismatch logs a warning).
     *
     * Use for low-throughput I/O queues (gRPC, Kafka, JDBC) to reduce OS thread count.
     * Multiple queues sharing the same scheduler name share the same thread pool.
     *
     * Exactly one of threads or sharedScheduler must be set.
     */
    private String sharedSchedulerName;
    private ThreadPolicy sharedSchedulerThreads;

    /**
     * Number of partitions, or a policy to derive it from resolved thread count.
     *
     * Can be set as:
     * - Absolute: fixed(8) → exactly 8 partitions.
     * - Relative: threadMultiply(2) → 2x resolved thread count.
     *   e.g. cpuCores(0.5) on 8-core = 4 threads, threadMultiply(2) → 8 partitions.
     *
     * Default: fixed(1).
     *
     * Validation (applied at construction time for dedicated scheduler mode):
     * - If partitions < resolved thread count, thread count is reduced to match
     *   partitions and a warning is logged. No point having more threads than partitions.
     */
    @Builder.Default
    private PartitionPolicy partitions = PartitionPolicy.fixed(1);

    /**
     * Buffer size per partition.
     */
    @Builder.Default
    private int bufferSize = 10_000;

    /**
     * BLOCKING: producer waits when buffer full.
     * IF_POSSIBLE: producer gets false when buffer full.
     */
    @Builder.Default
    private BufferStrategy strategy = BufferStrategy.BLOCKING;

    /**
     * Direct consumer for the whole batch. When set, all drained data goes to this
     * handler without class-based grouping. Takes priority over handler map.
     *
     * Use this for I/O queues where all data is the same type (gRPC, Kafka, JDBC).
     * Leave null to use handler map dispatch via addHandler().
     */
    private HandlerConsumer<T> consumer;

    /**
     * Called when a handler throws during consume.
     */
    private QueueErrorHandler<T> errorHandler;

    /**
     * Minimum idle sleep in milliseconds. Default 5ms.
     * Used as the base interval when data was recently consumed.
     * The thread re-polls quickly to catch new data with low latency.
     */
    @Builder.Default
    private long minIdleMs = 5;

    /**
     * Maximum idle sleep in milliseconds. Default 200ms.
     * After several consecutive empty cycles, the sleep interval backs off
     * exponentially from minIdleMs up to this cap.
     *
     * Backoff: sleep = min(minIdleMs * 2^consecutiveIdleCycles, maxIdleMs)
     *   idle 0: 5ms → idle 1: 10ms → idle 2: 20ms → idle 3: 40ms
     *   → idle 4: 80ms → idle 5: 160ms → idle 6+: 200ms (capped)
     */
    @Builder.Default
    private long maxIdleMs = 200;
}
```

### HandlerConsumer

```java
/**
 * Handler for processing a batch of data for a specific type.
 * Each metric type (or I/O queue user) provides its own handler instance.
 */
public interface HandlerConsumer<T> {
    /**
     * Process a batch of data belonging to this handler's type.
     */
    void consume(List<T> data);

    /**
     * Called when there is nothing to consume. Can be used as a timer trigger
     * (e.g. flush L1 aggregation cache periodically).
     */
    default void onIdle() {
    }
}
```

### ThreadPolicy

```java
/**
 * Determines the number of threads for a BatchQueue's dedicated scheduler
 * or for a shared scheduler created via BatchQueueManager.
 *
 * Two modes:
 * - fixed(N): exactly N threads, regardless of hardware.
 * - cpuCores(multiplier): multiplier * Runtime.availableProcessors(), rounded.
 *
 * Resolved value is always >= 1 — every pool must have at least one thread.
 * fixed() requires count >= 1 at construction. cpuCores() applies max(1, ...) at resolution.
 */
public class ThreadPolicy {
    private final int fixedCount;        // > 0 for fixed mode, 0 for cpuCores mode
    private final double cpuMultiplier;  // > 0 for cpuCores mode, 0 for fixed mode

    /**
     * Fixed number of threads. Count must be >= 1.
     * Example: fixed(1) → always 1 thread.
     *          fixed(8) → always 8 threads.
     * Throws IllegalArgumentException if count < 1.
     */
    public static ThreadPolicy fixed(int count);

    /**
     * Threads = multiplier * available CPU cores, rounded, min 1.
     * Multiplier must be > 0.
     * Example on 8-core machine:
     *   cpuCores(1.0)  → 8 threads
     *   cpuCores(0.5)  → 4 threads
     *   cpuCores(0.25) → 2 threads
     *   cpuCores(2.0)  → 16 threads
     * Example on 2-core machine:
     *   cpuCores(0.25) → 1 thread (min 1, never 0)
     */
    public static ThreadPolicy cpuCores(double multiplier);

    /**
     * Resolve the actual thread count. Always returns >= 1.
     * For fixed mode, returns fixedCount.
     * For cpuCores mode, returns max(1, round(cpuMultiplier * availableProcessors())).
     */
    public int resolve();
}
```

### PartitionPolicy

```java
/**
 * Determines the number of partitions for a BatchQueue.
 *
 * Two modes:
 * - fixed(N): exactly N partitions, regardless of thread count.
 * - threadMultiply(N): N * resolved thread count.
 * - adaptive(): partition count grows with registered handlers.
 *   Threshold = threadCount * multiplier (default 25).
 *   Below threshold: 1:1 (one partition per handler).
 *   Above threshold: excess at 1:2 ratio.
 *
 * All policies resolved via resolve(threadCount, handlerCount).
 * At queue construction time, if partitions < resolved thread count,
 * thread count is reduced to match and a warning is logged.
 */
public class PartitionPolicy {
    private final int fixedCount;     // > 0 for fixed mode
    private final int multiplier;     // > 0 for threadMultiply/adaptive
    private final boolean adaptive;   // true for adaptive mode

    public static PartitionPolicy fixed(int count);
    public static PartitionPolicy threadMultiply(int multiplier);
    public static PartitionPolicy adaptive();
    public static PartitionPolicy adaptive(int multiplier);

    /**
     * Resolve the actual partition count.
     * - fixed: returns fixedCount (both params ignored).
     * - threadMultiply: returns multiplier * resolvedThreadCount.
     * - adaptive: handlerCount == 0 → resolvedThreadCount;
     *   handlerCount <= threshold → handlerCount (1:1);
     *   handlerCount > threshold → threshold + (excess / 2).
     */
    public int resolve(int resolvedThreadCount, int handlerCount);
}
```

### Implementing HandlerConsumer

Each worker creates a handler as an **inner class** that directly accesses the worker's
fields (merge cache, telemetry counters, flush logic, etc.). The handler instance is
registered per metric class — one handler per worker, one worker per metric type.

#### L1 Aggregation (MetricsAggregateWorker)

Current code uses an inner `AggregatorConsumer` that calls `onWork()` and `flush()`:

```java
// Current: inner class IConsumer accesses outer worker fields
private class AggregatorConsumer implements IConsumer<Metrics> {
    public void consume(List<Metrics> data) {
        MetricsAggregateWorker.this.onWork(data);   // accesses mergeDataCache
    }
    public void nothingToConsume() {
        flush();                                      // accesses lastSendTime, nextWorker
    }
}
```

New code — same pattern, just implements `HandlerConsumer` instead of `IConsumer`.
Each `MetricsAggregateWorker` instance creates its own handler and registers it for
its specific metric class:

```java
public class MetricsAggregateWorker extends AbstractWorker<Metrics> {
    private final MergableBufferedData<Metrics> mergeDataCache;
    private final AbstractWorker<Metrics> nextWorker;
    private final BatchQueue<Metrics> l1Queue;

    MetricsAggregateWorker(ModuleDefineHolder moduleDefineHolder,
                           AbstractWorker<Metrics> nextWorker,
                           String modelName,
                           Class<? extends Metrics> metricsClass,
                           ...) {
        this.nextWorker = nextWorker;
        this.mergeDataCache = new MergableBufferedData<>();

        // Get or create the shared L1 queue (idempotent)
        this.l1Queue = BatchQueueManager.createIfAbsent(
            "METRICS_L1_AGGREGATION",
            BatchQueueConfig.<Metrics>builder()
                .threads(ThreadPolicy.cpuCores(1.0))          // 1x CPU cores
                .partitions(PartitionPolicy.threadMultiply(2))  // 2x resolved threads
                .bufferSize(10_000)
                .strategy(BufferStrategy.IF_POSSIBLE)
                .errorHandler((data, t) -> log.error(t.getMessage(), t))
                .build()
        );

        // Register this worker's handler for its specific metric class.
        // The inner class directly accesses mergeDataCache, nextWorker, etc.
        l1Queue.addHandler(metricsClass, new L1Handler());
    }

    @Override
    public void in(Metrics metrics) {
        l1Queue.produce(metrics);
    }

    private void onWork(List<Metrics> data) {
        data.forEach(mergeDataCache::accept);
        flush();
    }

    private void flush() {
        if (System.currentTimeMillis() - lastSendTime > l1FlushPeriod) {
            mergeDataCache.read().forEach(nextWorker::in);
            lastSendTime = System.currentTimeMillis();
        }
    }

    // Inner class handler — accesses worker fields directly
    private class L1Handler implements HandlerConsumer<Metrics> {
        @Override
        public void consume(List<Metrics> data) {
            MetricsAggregateWorker.this.onWork(data);
        }

        @Override
        public void onIdle() {
            MetricsAggregateWorker.this.flush();
        }
    }
}
```

Key point: 100+ `MetricsAggregateWorker` instances are created (one per metric type,
both OAL and MAL), each registers its own `L1Handler` inner class instance on the
same shared queue. Handler map dispatch routes each metric class to its own worker's
`mergeDataCache` — OAL and MAL handlers coexist in the same queue without interference.

#### L2 Persistent (MetricsPersistentMinWorker)

Same pattern — inner class handler accesses the worker's `onWork()` method:

```java
public class MetricsPersistentMinWorker extends MetricsPersistentWorker {
    private final BatchQueue<Metrics> l2Queue;

    MetricsPersistentMinWorker(..., Class<? extends Metrics> metricsClass, ...) {
        super(...);

        this.l2Queue = BatchQueueManager.createIfAbsent(
            "METRICS_L2_PERSISTENT",
            BatchQueueConfig.<Metrics>builder()
                .threads(ThreadPolicy.cpuCores(0.25))         // 0.25x CPU cores (2 on 8-core)
                .partitions(PartitionPolicy.threadMultiply(2))  // 2x resolved threads
                .bufferSize(10_000)
                .strategy(BufferStrategy.BLOCKING)
                .errorHandler((data, t) -> log.error(t.getMessage(), t))
                .build()
        );

        l2Queue.addHandler(metricsClass, new L2Handler());
    }

    @Override
    public void in(Metrics metrics) {
        l2Queue.produce(metrics);
    }

    // Inner class handler — accesses worker's onWork, queuePercentageGauge, etc.
    private class L2Handler implements HandlerConsumer<Metrics> {
        @Override
        public void consume(List<Metrics> data) {
            queuePercentageGauge.setValue(...);
            MetricsPersistentMinWorker.this.onWork(data);
        }
    }
}
```

#### TopN (TopNWorker — shared queue, handler map dispatch)

All TopN types share one queue. Same handler map pattern as L1/L2 — each TopNWorker
registers its inner class handler for its specific TopN class:

```java
public class TopNWorker extends PersistenceWorker<TopN> {
    private final BatchQueue<TopN> topNQueue;

    TopNWorker(..., Class<? extends TopN> topNClass, ...) {
        // Get or create the shared TopN queue (idempotent)
        this.topNQueue = BatchQueueManager.createIfAbsent(
            "TOPN_WORKER",
            BatchQueueConfig.<TopN>builder()
                .threads(ThreadPolicy.fixed(1))           // all TopN types share 1 thread
                .partitions(PartitionPolicy.fixed(1))
                .bufferSize(1000)
                .strategy(BufferStrategy.BLOCKING)
                .errorHandler((data, t) -> log.error(t.getMessage(), t))
                .build()
        );

        // Register this worker's handler for its specific TopN class
        topNQueue.addHandler(topNClass, new TopNHandler());
    }

    @Override
    public void in(TopN topN) {
        topNQueue.produce(topN);
    }

    // Inner class — accesses worker's limitedSizeBufferedData, reportPeriod, etc.
    private class TopNHandler implements HandlerConsumer<TopN> {
        @Override
        public void consume(List<TopN> data) {
            TopNWorker.this.onWork(data);
        }

        @Override
        public void onIdle() {
            TopNWorker.this.flushIfNeeded();
        }
    }
}
```

### Thread Reduction

Thread counts scale with CPU cores via ThreadPolicy. OAL and MAL share the same L1/L2 pools.
Example on 8-core machine:

| Before (DataCarrier)            | Threads | After (BatchQueue)                              | Threads (8-core) |
|---------------------------------|---------|-------------------------------------------------|------------------|
| L1 OAL pool                    | 24      | METRICS_L1_AGGREGATION (cpuCores(1.0))          | 8                |
| L1 MAL pool                    | 2       | *(shared with L1 above)*                        | *(shared)*       |
| L2 OAL pool                    | 2       | METRICS_L2_PERSISTENT (cpuCores(0.25))          | 2                |
| L2 MAL pool                    | 1       | *(shared with L2 above)*                        | *(shared)*       |
| TopNWorker (5-10 types)         | 5-10    | TOPN_WORKER (fixed(1), handler map)             | 1                |
| GRPCRemoteClient (2-4 peers)   | 2-4     | GRPCRemoteClient.* (shared IO_POOL)             |                  |
| JDBCBatchDAO                    | 2-4     | JDBCBatchDAO (shared IO_POOL)                   |                  |
| Exporters (gRPC/Kafka)          | 0-3     | Exporter.* (shared IO_POOL)                     |                  |
|                                 |         | **IO_POOL shared scheduler (cpuCores(0.5))**    | **4**            |
| **Total**                       | **~38-48** | **Total**                                    | **~15**          |

On different hardware:

| Machine   | L1 (OAL+MAL) | L2 (OAL+MAL) | TopN | IO_POOL | Total |
|-----------|---------------|---------------|------|---------|-------|
| 2-core    | 2             | 1             | 1    | 1       | 5     |
| 4-core    | 4             | 1             | 1    | 2       | 8     |
| 8-core    | 8             | 2             | 1    | 4       | 15    |
| 16-core   | 16            | 4             | 1    | 8       | 29    |

Savings (8-core):
- L1: 24+2 → 8 threads (OAL+MAL share one pool, CPU-relative, no empty channel iteration)
- L2: 2+1 → 2 threads (OAL+MAL share one pool)
- TopN: 5-10 → 1 thread (all types share one queue with handler map dispatch)
- I/O queues: 4-11 → 4 threads (shared IO_POOL for gRPC, Kafka, JDBC)
- Total: from ~38-48 OS threads down to ~15

IO_POOL queues (all do network or database I/O, low-throughput, bursty):

| Queue Name              | Current Source         | I/O Type      | Threads Before |
|-------------------------|------------------------|---------------|----------------|
| GRPCRemoteClient.*      | GRPCRemoteClient.java  | gRPC network  | 2-4 (per peer) |
| GRPCMetricsExporter     | GRPCMetricsExporter.java| gRPC network | 1              |
| KafkaLogExporter        | KafkaLogExporter.java  | Kafka network | 1              |
| KafkaTraceExporter      | KafkaTraceExporter.java| Kafka network | 1              |
| JDBCBatchDAO            | JDBCBatchDAO.java      | JDBC database | 2-4            |
| **Subtotal**            |                        |               | **7-14**       |
| **After (IO_POOL)**     |                        |               | **cpuCores(0.5)** |

Number of partitions/buffers:

| Before                         | Count    | After                              | Count |
|--------------------------------|----------|------------------------------------|-------|
| L1 OAL channels (100+ * 2ch)  | 200+     | L1 partitions (8 threads * 2)      | 16    |
| L1 MAL channels (N * 1ch)     | N        | *(shared with L1 above)*           | *(0)* |
| L2 OAL channels (100+ * 1ch)  | 100+     | L2 partitions (2 threads * 2)      | 4     |
| L2 MAL channels (N * 1ch)     | N        | *(shared with L2 above)*           | *(0)* |
| TopN buffers (5-10 types)      | 5-10     | TOPN_WORKER partitions             | 1     |
| I/O buffers (gRPC, JDBC, etc.) | 5-8      | I/O queue partitions               | 5-8   |
| **Total buffers**              | **300+** | **Total buffers**                  | **~28** |

### What Gets Dropped

| DataCarrier Feature                 | Status    | Reason                                                |
|-------------------------------------|-----------|-------------------------------------------------------|
| One queue per metric type           | Dropped   | Shared partitions + handler map instead               |
| Separate OAL / MAL pools           | Dropped   | OAL and MAL share L1/L2 queues (handler map dispatch) |
| One thread per TopN type            | Dropped   | All TopN types share one TOPN_WORKER queue             |
| Multi-channel per DataCarrier       | Dropped   | Single partition array replaces multi-channel          |
| IDataPartitioner                    | Dropped   | Simple round-robin on partition array                  |
| Consumer instantiation by class     | Dropped   | All callers use instance-based handlers                |
| Consumer init(Properties)           | Dropped   | Not used by any production consumer                    |
| EnvUtil override                    | Dropped   | Configuration via application.yml                      |
| Two separate queue classes          | Dropped   | One `BatchQueue` with configurable scheduler modes     |
| BulkConsumePool / ConsumerPoolFactory | Dropped | Dedicated/shared ScheduledExecutorService replaces pool|
| Fixed thread counts                 | Dropped   | ThreadPolicy: CPU-relative (cpuCores) or fixed         |
| Signal-driven consumption           | Dropped   | Adaptive backoff replaces explicit notify              |
| Separate createSharedScheduler step | Dropped   | Shared schedulers created lazily on first queue ref    |

### What Gets Preserved

| Feature               | How                                                          |
|-----------------------|--------------------------------------------------------------|
| Named queue management| `BatchQueueManager.create/createIfAbsent/get` by name        |
| Per-type isolation    | `handlerMap` dispatches each class to its own handler         |
| Bounded buffer        | ArrayBlockingQueue per partition                              |
| BLOCKING strategy     | `queue.put()` — producer blocks when full                    |
| IF_POSSIBLE strategy  | `queue.offer()` — returns false when full, data dropped      |
| Batch consumption     | `drainTo(list)` — same as current                            |
| Error handling        | `errorHandler.onError(batch, throwable)`                     |
| Nothing-to-consume    | `handler.onIdle()` — called when all partitions empty        |
| Fast data response    | Adaptive backoff (minIdleMs=5ms) replaces signal-driven mode |
| Drain on shutdown     | Manager shutdown drains all queues, then schedulers           |
| Produce-gate          | `produce()` returns false if queue is shut down              |
| Hardware scaling      | ThreadPolicy.cpuCores() scales threads with available cores  |
