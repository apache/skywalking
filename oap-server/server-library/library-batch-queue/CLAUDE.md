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

## Queue Sharing

Multiple workers of the same concern share a single queue via `BatchQueueManager.getOrCreate(name, config)`.
The first caller creates the queue; subsequent callers with the same name get the existing instance.
Each worker registers its type handler via `addHandler()`. For strict unique-name enforcement,
use `BatchQueueManager.create(name, config)` which throws on duplicate names.

## Key Classes

| Class | Role |
|-------|------|
| `BatchQueue<T>` | The queue itself. Holds partitions, runs drain loops, dispatches to consumers/handlers. |
| `BatchQueueManager` | Global registry. Creates/retrieves queues by name. `create()` for unique, `getOrCreate()` for shared. |
| `BatchQueueConfig<T>` | Builder for queue configuration (threads, partitions, buffer, strategy, consumer, balancer). |
| `ThreadPolicy` | Resolves thread count: `fixed(N)`, `cpuCores(mult)`, `cpuCoresWithBase(base, mult)`, `ioBound(N)`. |
| `PartitionPolicy` | Resolves partition count: `fixed(N)`, `threadMultiply(N)`, `adaptive()`. |
| `PartitionSelector<T>` | Routes items to partitions. Default `typeHash()` groups by class. |
| `HandlerConsumer<T>` | Callback for processing a batch. Has optional `onIdle()` for flush-on-idle. |
| `BufferStrategy` | `BLOCKING` (put, waits) or `IF_POSSIBLE` (offer, drops if full). |
| `BatchQueueStats` | Point-in-time snapshot of queue usage. `totalUsed()`, `topN(n)`, per-partition stats. |
| `QueueErrorHandler<T>` | Optional error callback. If absent, errors are logged. |
| `DrainBalancer` | Strategy for periodic partition-to-thread rebalancing. Default `throughputWeighted()`. |

## ThreadPolicy

```java
ThreadPolicy.fixed(4)                   // exactly 4 threads
ThreadPolicy.cpuCores(1.0)              // 1 thread per CPU core
ThreadPolicy.cpuCoresWithBase(1, 0.25)  // 1 + 0.25 * cores (e.g., 3 on 8-core)
ThreadPolicy.ioBound(50)                // 50 threads whose consumers block; virtual when available
```

Always resolves to >= 1.

### ioBound — blocking consumers

`ioBound(n)` declares that this queue's consumers spend most of their time blocked, typically on
I/O. The scheduler then uses virtual threads where the runtime provides them (JDK 25+, via
`VirtualThreads.createScheduledExecutor`) and falls back to `n` platform threads otherwise.

**The count is identical on both paths.** Concurrency, batching, back-pressure, drop semantics and
per-partition ordering are unaffected by the fallback. The queue logs once at creation when it
asked for virtual threads and got platform ones.

One behaviour does differ: **shutdown latency**. On the platform path the scheduler drops drain
tasks still waiting out their idle backoff, so termination is immediate. The virtual-thread adapter
implements a delay by sleeping *inside* the submitted task, which `shutdown()` cannot cancel, so
termination waits out the longest outstanding backoff — bounded by `maxIdleMs`. Keep
`maxIdleMs <= shutdownTimeoutMs` on an `ioBound` queue, or `shutdown()` will log a spurious
"drain loops did not finish" warning on every teardown. Nothing is lost either way: a late-waking
drain task sees `running == false` and exits without draining, and the final drain flushes the data.

Sizing: the drain loop **is** the task processor — a consumer blocking for seconds occupies its
loop for that whole time, and nothing is handed off. So consumer concurrency is
`min(threadCount, partitionCount)`, and an IO-bound queue wants **threads and partitions 1:1**
with a small per-partition buffer:

```java
.threads(ThreadPolicy.ioBound(concurrency))
.partitions(PartitionPolicy.fixed(concurrency))   // 1:1, or the count is clamped away
.bufferSize(4)                                    // per partition
.strategy(BufferStrategy.IF_POSSIBLE)
.minIdleMs(50).maxIdleMs(500)                     // seconds-long work; 1ms polling is waste
```

The count expresses **how many concurrent blocking calls the downstream service tolerates**, which
does not follow core count — hence no CPU-proportional variant, and hence the count is mandatory.

**Never use `ioBound` for CPU-bound work.** Virtual threads are not preemptive: a CPU-only task
holds its carrier to completion, and the carrier pool is shared process-wide with the executors
`GRPCServer` and `HTTPServer` already create. L1 (`MetricsAggregateWorker`), L2
(`MetricsPersistentMinWorker`) and TopN do in-memory merges across hundreds of partitions and must
stay on `cpuCores`/`fixed`. The API has no `cpuCores(...).ioBound()` form so this combination
cannot be expressed.

`ioBound` with a `DrainBalancer` logs a warning: rebalancing exists to redistribute skewed
CPU-bound partitions, which is not this shape. It is not rejected — it remains safe — but it is
usually a configuration mistake.

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

### Weighted handlers

`addHandler(type, handler, weight)` allows different handler types to contribute different
amounts to the partition count. The adaptive formula uses the weighted sum instead of raw
handler count. Partition assignment remains hash-based (`typeHash()`) — weight only affects
how many partitions exist, not which partition a type lands on.

L1 uses weight 0.05 for MAL metrics (vs 1.0 for OAL). Rationale: MAL emits ~500 items/type
per scrape interval. With 20,000-slot buffers, ~40 MAL types can safely share one partition
(20,000 / 500 = 40). Weight 0.05 ≈ 1/20 gives 2x headroom.

Example (8 threads, 642 OAL + 1,247 MAL):
- Without weight: 1,889 handlers -> 1,045 partitions (167 MB array overhead at L1)
- With weight: 642*1.0 + 1,247*0.05 = 705 effective -> 452 partitions (72 MB)

L2 uses default weight 1.0 for all types because after L1 pre-aggregation both OAL and MAL
have similar per-minute burst patterns.

## Drain Rebalancing

Static round-robin partition assignment creates thread imbalance when metric types have varying
throughput (e.g., endpoint-scoped OAL >> service-scoped OAL). The `DrainBalancer` periodically
reassigns partitions to equalize per-thread load.

### Configuration

Opt-in via the builder's `.balancer(strategy, intervalMs)` method:

```java
BatchQueueConfig.builder()
    .threads(ThreadPolicy.cpuCores(1.0))
    .partitions(PartitionPolicy.adaptive())
    .balancer(DrainBalancer.throughputWeighted(), 300_000)  // rebalance every 5 min
    ...
```

Silently ignored for single-thread queues (nothing to rebalance).

### How it works

1. **Throughput counters** — `produce()` increments a per-partition `AtomicLong` counter before `put/offer`.
2. **LPT assignment** — The rebalancer snapshots and resets counters, sorts partitions by throughput descending, assigns each to the least-loaded thread (Longest Processing Time heuristic).
3. **Two-phase handoff** — Moved partitions go through revoke (UNOWNED) → wait for old owner's drain cycle to finish (cycleCount fence) → assign to new owner. This prevents concurrent handler invocations.
4. **Skip threshold** — Rebalancing is skipped when max/min thread load ratio < 1.15 (BLOCKING backpressure compresses observed ratios).

### Safety guarantees

| Property | Mechanism |
|----------|-----------|
| No concurrent handler calls | Two-phase handoff: revoke + cycle-count fence + assign |
| No data loss | Items stay in `ArrayBlockingQueue` during the UNOWNED gap |
| No data duplication | `drainTo` atomically moves items out of the queue |
| Lock-free hot path | Only `AtomicIntegerArray.get()` added to drain loop |
| Lock-free produce path | Only `AtomicLongArray.incrementAndGet()` added |

### Benchmark results (4 drain threads, 16 producers, 100 types, skewed load)

```
                    Static          Rebalanced
  Throughput:    7,211,794         8,729,310  items/sec
  Load ratio:       1.30x             1.04x  (max/min thread)
  Improvement:                       +21.0%
```

## Usage in the Codebase

### L1 Metrics Aggregation (`MetricsAggregateWorker`)
```
queue:      getOrCreate("METRICS_L1_AGGREGATION", ...)
threads:    cpuCores(1.0)        -- 8 threads on 8-core
partitions: adaptive()           -- grows with metric types (~330 for typical OAL+MAL on 8 threads)
balancer:   throughputWeighted(), 10s
bufferSize: 20,000 per partition
strategy:   IF_POSSIBLE
idleMs:     1..50
mode:       handler map (one handler per metric class)
```

### L2 Metrics Persistence (`MetricsPersistentMinWorker`)
```
queue:      getOrCreate("METRICS_L2_PERSISTENCE", ...)
threads:    cpuCoresWithBase(1, 0.25)  -- 3 threads on 8-core
partitions: adaptive()                 -- grows with metric types
balancer:   throughputWeighted(), 10s
bufferSize: 2,000 per partition
strategy:   BLOCKING
idleMs:     1..50
mode:       handler map (one handler per metric class)
```

### TopN Persistence (`TopNWorker`)
```
queue:      getOrCreate("TOPN_PERSISTENCE", ...)
threads:    fixed(1)
partitions: adaptive()         -- grows with TopN types
bufferSize: 1,000 per partition
strategy:   BLOCKING
idleMs:     10..100
mode:       handler map (one handler per TopN class)
```

### gRPC Remote Client (`GRPCRemoteClient`)
```
queue:      create(unique name per client, ...)
threads:    fixed(1)
partitions: fixed(1)
bufferSize: configurable (channelSize * bufferSize)
strategy:   BLOCKING
idleMs:     1..50
mode:       single consumer (sends over gRPC stream)
```

### Exporters (gRPC metrics, Kafka trace, Kafka log)
```
queue:      create(unique name per exporter, ...)
threads:    fixed(1) each
partitions: fixed(1) each
bufferSize: configurable (default 20,000)
strategy:   BLOCKING (gRPC) / IF_POSSIBLE (Kafka)
idleMs:     1..200
mode:       single consumer
```

### JDBC Batch DAO (`JDBCBatchDAO`)
```
queue:      create("JDBC_BATCH_PERSISTENCE", ...)
threads:    fixed(N) where N = asyncBatchPersistentPoolSize (default 4)
partitions: fixed(N) (1 partition per thread)
bufferSize: 10,000 per partition
strategy:   BLOCKING
idleMs:     1..20
mode:       single consumer (JDBC batch flush)
```

## Lifecycle

1. `BatchQueueManager.getOrCreate(name, config)` -- gets existing or creates new queue, starts drain loops
2. `BatchQueueManager.create(name, config)` -- creates queue (throws if name already exists)
3. `queue.addHandler(type, handler)` -- registers type handler (adaptive: may grow partitions)
4. `queue.produce(data)` -- routes to partition, blocks or drops per strategy
5. Drain loops run continuously, dispatching batches to consumers/handlers
6. `BatchQueueManager.shutdown(name)` -- stops drain, waits for in-flight consumers, final flush
7. `BatchQueueManager.shutdownAll()` -- available, but currently has no production callers

### Shutdown order

```
CAS shutdownStarted          winner runs the sequence; losers await its completion latch
running = false              reject produce(); drain chains stop re-queueing
rebalanceFuture.cancel(true) the periodic task does not read `running`
scheduler.shutdown()
awaitTermination(config)     COURTESY wait for orderly exit; may time out or be interrupted
dispatchLock.writeLock()     THIS is the guarantee — waits out any in-flight consume()
final drain + dispatch
restore interrupt            only if interrupted, and only after dispatch
```

The order is load-bearing:

- **cancel before awaitTermination** -- on a virtual-thread scheduler the periodic task is one
  submission whose loop exits only on interrupt, and `shutdown()` does not interrupt. Without the
  cancel, termination can never be observed. (A `ScheduledThreadPoolExecutor` cancels periodic
  tasks itself, so the platform path worked by relying on that default.)
- **`running = false` before cancel** -- the rebalance fence spins on
  `cycleCount.get(t) <= snap && running`, and `LockSupport.parkNanos` *returns* on interrupt
  without throwing, leaving the flag set. The fence exits via `running`, not the interrupt.
- **wait before the final drain** -- draining on the caller's thread while a drain loop is still
  inside `consume()` invokes the same handler concurrently, breaking the single-drain-thread
  invariant workers such as `MetricsAggregateWorker` depend on.

The final drain deliberately ignores partition ownership, so partitions left `UNOWNED` by an
interrupted rebalance are still flushed.

**`awaitTermination` is not the safety mechanism -- the dispatch lock is.** The wait is a courtesy
for orderly exit and can time out or be interrupted, and on either path a drain loop may still be
running. `shutdown()` takes the WRITE lock, so its final dispatch waits the in-flight consumer out
instead of racing it.

Drain loops hold the READ lock for the **whole cycle** -- the `running` recheck, the partition
dequeue, `notifyIdle()` and the dispatch -- not merely around `dispatch()`. All four are required:
a dequeue outside the lock lets shutdown drain and dispatch a *newer* batch while a task holds an
older one, which is then dispatched out of order after `shutdown()` has returned; `notifyIdle()`
outside it runs `onIdle()` concurrently with the final dispatch, and implementations such as
`MetricsAggregateWorker` flush the same worker state from `onIdle()`; and the loop's `running` test
can pass just as shutdown completes. Read locks are shared, so drain loops still run concurrently
with one another -- correct, since the partition selector routes a type to one partition and
therefore one task. Nothing sleeps inside the body (backoff is applied by `scheduleDrain` after the
loop exits), so the lock is held only for as long as the consumer runs. Blocking there is bounded by that consumer's
own work and is preferable to interrupting it: no consumer is written to tolerate interruption
mid-batch, and a truncated `executeBatch` or stream send is worse than the race being fixed, which
is why `shutdownNow()` is never called.

`shutdownTimeoutMs` is per-queue (default 500ms) and bounds only the courtesy wait; `0` skips it
entirely, which is safe now that the lock -- not the wait -- provides the guarantee.

Two further properties, both for the same reason -- a consumer must never be entered twice at once,
nor with the interrupt flag set:

- **Once-only, and shared completion.** Only the caller that wins a CAS runs the sequence; the
  losers block on a completion latch rather than returning early, so `shutdownAll()` racing a
  `shutdown(name)` cannot report completion while the winner is still draining. That wait is
  deliberately **unbounded**: the winner may spend all of `shutdownTimeoutMs` in `awaitTermination`
  and then block on the write lock for as long as a consumer runs, so any fixed bound would let a
  loser return early. Interruption is deferred and restored on the way out.
- **Interrupt is deferred.** If `awaitTermination` is interrupted, the queue logs, still runs the
  final drain (skipping it would lose data), and re-asserts the thread's interrupt flag only after
  `dispatch()` returns.

The platform scheduler additionally sets `setExecuteExistingDelayedTasksAfterShutdownPolicy(false)`
so drain tasks parked on their idle backoff do not hold up termination. Dropping them is safe: such
a task, had it run, would find `running == false` and exit at the top of its loop without draining.
