/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.library.batchqueue;

/**
 * Determines the number of partitions for a BatchQueue.
 *
 * <ul>
 *   <li>{@link #fixed(int)}: exactly N partitions, regardless of thread count.</li>
 *   <li>{@link #threadMultiply(int)}: N * resolved thread count.</li>
 *   <li>{@link #adaptive()}: recommended for metrics aggregation. The partition
 *       count grows as handlers are registered via {@link BatchQueue#addHandler}.
 *       Uses {@code threadCount * multiplier} (default 25) as a threshold.
 *       Below threshold, 1:1 mapping (one partition per handler). Above threshold,
 *       excess handlers share partitions at 1:2 ratio.</li>
 * </ul>
 *
 * <p>All policies are resolved via {@link #resolve(int, double)}. For non-adaptive
 * policies the weightedHandlerCount parameter is ignored. At queue creation time, if the
 * resolved partition count is less than the thread count, the thread count is
 * reduced to match and a warning is logged.
 */
public class PartitionPolicy {
    private static final int DEFAULT_ADAPTIVE_MULTIPLIER = 25;

    private final int fixedCount;
    private final int multiplier;
    private final boolean adaptive;

    private PartitionPolicy(final int fixedCount, final int multiplier,
                            final boolean adaptive) {
        this.fixedCount = fixedCount;
        this.multiplier = multiplier;
        this.adaptive = adaptive;
    }

    /**
     * Fixed number of partitions.
     *
     * @param count the exact number of partitions
     * @return a PartitionPolicy with a fixed partition count
     * @throws IllegalArgumentException if count &lt; 1
     */
    public static PartitionPolicy fixed(final int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Partition count must be >= 1, got: " + count);
        }
        return new PartitionPolicy(count, 0, false);
    }

    /**
     * Partitions = multiplier * resolved thread count.
     *
     * @param multiplier factor applied to thread count
     * @return a PartitionPolicy that scales with thread count
     * @throws IllegalArgumentException if multiplier &lt; 1
     */
    public static PartitionPolicy threadMultiply(final int multiplier) {
        if (multiplier < 1) {
            throw new IllegalArgumentException("Partition multiplier must be >= 1, got: " + multiplier);
        }
        return new PartitionPolicy(0, multiplier, false);
    }

    /**
     * Adaptive partition count with default threshold multiplier (25).
     *
     * <p>The partition count grows as handlers are registered via
     * {@link BatchQueue#addHandler}:
     * <ul>
     *   <li>Threshold = {@code threadCount * 25}</li>
     *   <li>handlerCount &lt;= threshold: one partition per handler (1:1)</li>
     *   <li>handlerCount &gt; threshold: {@code threshold + (handlerCount - threshold) / 2}</li>
     *   <li>handlerCount == 0: returns {@code threadCount} as initial count</li>
     * </ul>
     *
     * <p>Examples with 8 threads (threshold = 200):
     * <pre>
     *     0 handlers →   8 partitions  (initial = threadCount)
     *   100 handlers → 100 partitions  (1:1, below threshold)
     *   200 handlers → 200 partitions  (1:1, at threshold)
     *   500 handlers → 350 partitions  (200 + 300/2)
     *  1000 handlers → 600 partitions  (200 + 800/2)
     *  2000 handlers → 1100 partitions (200 + 1800/2)
     * </pre>
     *
     * @return an adaptive PartitionPolicy with default threshold multiplier
     */
    public static PartitionPolicy adaptive() {
        return new PartitionPolicy(0, DEFAULT_ADAPTIVE_MULTIPLIER, true);
    }

    /**
     * Adaptive partition count with custom threshold multiplier.
     *
     * <p>Threshold = {@code threadCount * multiplier}. Below threshold, one
     * partition per handler (1:1). Above threshold, excess handlers share
     * at 1:2 ratio: {@code threshold + (handlerCount - threshold) / 2}.
     *
     * @param multiplier threshold per thread (default 25)
     * @return a PartitionPolicy that grows with handler registrations
     * @throws IllegalArgumentException if multiplier &lt; 1
     */
    public static PartitionPolicy adaptive(final int multiplier) {
        if (multiplier < 1) {
            throw new IllegalArgumentException(
                "adaptive multiplier must be >= 1, got: " + multiplier);
        }
        return new PartitionPolicy(0, multiplier, true);
    }

    /**
     * Resolve the actual partition count.
     * <ul>
     *   <li>fixed: returns the pre-set count (both parameters ignored).</li>
     *   <li>threadMultiply: returns multiplier * resolvedThreadCount (handlerCount ignored).</li>
     *   <li>adaptive: when weightedHandlerCount is 0, returns resolvedThreadCount as a sensible
     *       initial count. Otherwise, threshold = threadCount * multiplier; if weightedHandlerCount
     *       &lt;= threshold, returns weightedHandlerCount (1:1). If above, returns
     *       threshold + (weightedHandlerCount - threshold) / 2.</li>
     * </ul>
     *
     * @param resolvedThreadCount the resolved number of drain threads
     * @param weightedHandlerCount the weighted sum of registered type handlers. Each handler
     *                             contributes its weight (default 1.0) to this sum.
     *                             High-weight handlers grow the partition count faster,
     *                             reducing the chance of hash collisions for those types.
     *                             Low-weight handlers grow the count slowly, so they are
     *                             more likely to share partitions with other types via
     *                             {@code typeHash()} routing. Note that partition assignment
     *                             is hash-based, not weight-based — there is no guarantee
     *                             that any type gets a dedicated partition.
     * @return the resolved partition count, always &gt;= 1
     */
    public int resolve(final int resolvedThreadCount, final double weightedHandlerCount) {
        if (fixedCount > 0) {
            return fixedCount;
        }
        if (adaptive) {
            final int effectiveCount = (int) Math.ceil(weightedHandlerCount);
            if (effectiveCount == 0) {
                return Math.max(1, resolvedThreadCount);
            }
            final int threshold = Math.max(1, multiplier * resolvedThreadCount);
            if (effectiveCount <= threshold) {
                return effectiveCount;
            }
            return threshold + (effectiveCount - threshold) / 2;
        }
        return Math.max(1, multiplier * resolvedThreadCount);
    }

    @Override
    public String toString() {
        if (fixedCount > 0) {
            return "fixed(" + fixedCount + ")";
        }
        if (adaptive) {
            return "adaptive(multiplier=" + multiplier + ")";
        }
        return "threadMultiply(" + multiplier + ")";
    }
}
