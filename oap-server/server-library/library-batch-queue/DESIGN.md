# Throughput-Weighted Partition Rebalancing

## Problem

`BatchQueue` assigns partitions to drain threads with a static round-robin mapping
(`buildAssignments`). Combined with `typeHash()` partition selection, each metric
class is pinned to exactly one partition and one drain thread.

In a typical SkyWalking deployment, OAL metrics generate far more data than MAL
metrics. With hundreds of metric types of varying throughput, the static assignment
creates **unbalanced drain threads**: some threads are overloaded with hot OAL
partitions while others are nearly idle draining cold MAL partitions.

The imbalance is invisible for low-throughput queues (exporters, TopN, JDBC) but
significant for **L1 aggregation** and **L2 persistence** queues, which have
`cpuCores(1.0)` or more threads and `adaptive()` partitions scaling to hundreds.

## Design: Periodic Throughput-Weighted Reassignment

A background rebalancer periodically measures per-partition throughput, then
reassigns partitions to threads to equalize total load per thread.

### Data flow overview

```
Producer threads           Drain threads              Rebalancer (periodic)
  |                          |                          |
  |  produce(data)           |  drainLoop(taskIndex)    |  every rebalanceIntervalMs:
  |    |                     |    |                     |    1. snapshot throughput counters
  |    +-- typeHash()        |    +-- read partitionOwner    2. reset counters
  |    +-- put/offer         |    |   skip if != me     |    3. LPT assign partitions
  |    +-- bump counter      |    +-- drainTo + dispatch |    4. two-phase handoff
  |                          |    +-- bump cycleCount   |
```

### Throughput counters

Each partition has an `AtomicLong` counter, incremented on every `produce()` call.
The rebalancer snapshots and resets all counters each interval.

```java
AtomicLongArray partitionThroughput;  // one slot per partition

// In produce(), after the put/offer:
partitionThroughput.incrementAndGet(index);
```

The counter is on the produce path, which is already doing an `ArrayBlockingQueue.put/offer`.
A single `incrementAndGet` adds negligible overhead (no contention — each metric type
hashes to a fixed partition, so each partition's counter is written by a predictable
set of producer threads).

### Rebalance algorithm (LPT — Longest Processing Time)

The rebalancer runs on the queue's scheduler (one extra scheduled task). It uses the
classic **LPT multiprocessor scheduling** heuristic:

```
1. snapshot = partitionThroughput[0..N-1]
2. reset all counters to 0
3. sort partitions by snapshot[p] descending
4. threadLoad = long[taskCount], all zeros
5. newAssignment = List<Integer>[taskCount]
6. for each partition p in sorted order:
       t = argmin(threadLoad)           // thread with least total load
       newAssignment[t].add(p)
       threadLoad[t] += snapshot[p]
7. two-phase handoff (see below)
```

LPT is O(P log P) for sorting + O(P log T) for assignment (with a min-heap for
threadLoad). For 500 partitions and 8 threads, this is sub-millisecond.

If a partition has zero throughput in the last interval, it keeps its previous
assignment (no unnecessary moves).

### Two-phase handoff protocol

Reassigning a partition from Thread-A to Thread-B while Thread-A is mid-dispatch
creates a **concurrent handler invocation** — two threads calling the same
`HandlerConsumer.consume()` on different batches simultaneously. For L1 aggregation,
`MergableBufferedData` is not thread-safe, so this corrupts state.

The race condition:

```
Thread-A                          Rebalancer               Thread-B
────────                          ──────────               ────────
drainTo(P3) → 500 items
dispatch(batch):
  handler_X.consume(500)          owner[P3] = B
  ← still running                                          drainTo(P3) → 200 new items
                                                           dispatch(batch):
                                                             handler_X.consume(200)
                                                             ← CONCURRENT! handler_X corrupted
```

A simple ownership gate (`if partitionOwner[p] != me: skip`) prevents the new owner
from **draining** the partition. But the old owner already drained the data and is
still **dispatching** to the handler. The new owner would drain new items and call
the same handler concurrently.

The fix is a two-phase handoff with a cycle-count fence:

```
Phase 1 — Revoke:
  partitionOwner[p] = UNOWNED (-1)
  // Thread-A sees UNOWNED on next drainTo check, skips P3.
  // But Thread-A may be mid-dispatch right now — handler still in use.

  Wait: spin until cycleCount[oldTask] > snapshot
  // Once the counter increments, Thread-A has finished its current
  // drain+dispatch cycle. The handler is no longer being called.

Phase 2 — Assign:
  partitionOwner[p] = newTaskIndex
  // Thread-B picks up P3 on its next drain cycle. Safe — no concurrent handler call.
```

During the gap between phases, new items accumulate in partition P3 but are not lost.
Thread-B drains them once Phase 2 completes.

### Cycle counter

Each drain task increments its cycle counter at the end of every drain cycle (after
dispatch completes, before re-scheduling). The rebalancer reads this counter to know
when a task has finished any in-flight work.

```java
AtomicLongArray cycleCount;  // one slot per drain task

void drainLoop(int taskIndex) {
    // ... drain assigned partitions, dispatch ...
    cycleCount.incrementAndGet(taskIndex);
    // re-schedule
}
```

The rebalancer uses it in the revoke phase:

```java
void movePartition(int p, int oldTask, int newTask) {
    partitionOwner.set(p, UNOWNED);

    long snapshot = cycleCount.get(oldTask);
    while (cycleCount.get(oldTask) <= snapshot) {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
    }

    partitionOwner.set(p, newTask);
}
```

**Worst-case wait:** one `maxIdleMs` (the drain task may be sleeping in backoff).
For L1 aggregation this is 50ms, for L2 persistence 50ms. Since rebalancing runs
every few minutes, this latency is negligible.

### Partition ownership array

```java
AtomicIntegerArray partitionOwner;  // partitionOwner[p] = taskIndex that owns it, or -1
```

The drain loop checks ownership before draining each partition:

```java
void drainLoop(int taskIndex) {
    for (int p : assignedPartitions[taskIndex]) {
        if (partitionOwner.get(p) != taskIndex) {
            continue;  // revoked or not yet assigned
        }
        partitions[p].drainTo(combined);
    }
    dispatch(combined);
    cycleCount.incrementAndGet(taskIndex);
}
```

The `partitionOwner` check is one `AtomicIntegerArray.get()` per partition per drain
cycle — a volatile read with no CAS. This is the only overhead on the hot path.

### Configuration

Rebalancing is opt-in via `BatchQueueConfig`:

```java
BatchQueueConfig.<Metrics>builder()
    .threads(ThreadPolicy.cpuCores(1.0))
    .partitions(PartitionPolicy.adaptive())
    .rebalanceIntervalMs(300_000)  // every 5 minutes, 0 = disabled (default)
    .build();
```

Only meaningful for dedicated-scheduler queues with multiple threads and partitions.
Silently ignored for single-thread or shared-scheduler queues.

### Full rebalance cycle

```
1. rebalanceTask fires (scheduled every rebalanceIntervalMs)
   |
2. snapshot all partitionThroughput[p], reset to 0
   |
3. skip rebalance if throughput is uniform (max/min ratio < 1.5)
   |
4. LPT assignment: sort partitions by throughput desc,
   assign each to the least-loaded thread
   |
5. diff against current assignedPartitions
   |  only partitions that changed owner need handoff
   |
6. for each moved partition:
   |  Phase 1: partitionOwner[p] = UNOWNED
   |
7. for each moved partition:
   |  wait: cycleCount[oldTask] > snapshot_before_revoke
   |
8. for each moved partition:
   |  Phase 2: partitionOwner[p] = newTask
   |
9. update assignedPartitions (volatile write)
   |
10. log summary: "rebalanced N partitions, max thread load delta: X%"
```

Steps 6 and 7 batch all revocations first, then wait for all old owners in parallel.
This bounds the total handoff latency to one drain cycle (the slowest old owner),
rather than one cycle per moved partition sequentially.

### Safety guarantees

| Property | Mechanism |
|----------|-----------|
| No concurrent handler calls | Two-phase handoff: revoke + cycle-count fence + assign |
| No data loss | Items stay in `ArrayBlockingQueue` during the UNOWNED gap |
| No data duplication | `drainTo` atomically moves items out of the queue |
| Lock-free hot path | Only `AtomicIntegerArray.get()` added to drain loop |
| Lock-free produce path | Only `AtomicLongArray.incrementAndGet()` added |
| Bounded handoff latency | At most one `maxIdleMs` wait per rebalance |

### Scope

| Queue | Rebalance? | Reason |
|-------|------------|--------|
| L1 Aggregation (`METRICS_L1_AGGREGATION`) | Yes | Hundreds of metric types, cpuCores threads, high throughput variance |
| L2 Persistence (`METRICS_L2_PERSISTENCE`) | Yes | Same type distribution, fewer threads but still benefits |
| TopN (`TOPN_PERSISTENCE`) | No | Single thread, nothing to rebalance |
| Exporters / gRPC Remote / JDBC | No | Single thread or fixed(1) partition, nothing to rebalance |

### Complexity budget

| Component | Lines (est.) | Hot-path cost |
|-----------|-------------|---------------|
| `partitionThroughput` counter | ~5 | 1 `AtomicLong.incrementAndGet` per produce |
| `partitionOwner` check in drain loop | ~5 | 1 `AtomicInteger.get` per partition per cycle |
| `cycleCount` bump | ~2 | 1 `AtomicLong.incrementAndGet` per drain cycle |
| Rebalance task (LPT + handoff) | ~80 | 0 (runs on scheduler, not on hot path) |
| Config field + validation | ~10 | 0 |
| **Total** | **~100** | **2 atomic ops per produce+drain** |
