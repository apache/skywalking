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
 * Determines the number of threads for a BatchQueue's scheduler, and whether that queue's
 * work is blocking-dominated.
 *
 * Four modes:
 * - fixed(N): exactly N threads, regardless of hardware.
 * - cpuCores(multiplier): multiplier * Runtime.availableProcessors(), rounded.
 * - cpuCoresWithBase(base, multiplier): base + multiplier * Runtime.availableProcessors(), rounded.
 * - ioBound(N): exactly N threads whose consumers spend most of their time blocked.
 *
 * Resolved value is always &gt;= 1 — every pool must have at least one thread.
 * fixed() requires count &gt;= 1 at construction. cpuCores() applies max(1, ...) at resolution.
 *
 * There is deliberately no CPU-proportional {@code ioBound} variant: sizing by core count is
 * meaningless for work that blocks, and virtual threads must never carry CPU-bound work — they
 * are not preemptive, so a CPU-only task holds its carrier to completion and starves every other
 * virtual thread in the process.
 */
public class ThreadPolicy {
    private final int fixedCount;
    private final int base;
    private final double cpuMultiplier;
    private final boolean ioBound;

    private ThreadPolicy(final int fixedCount, final int base, final double cpuMultiplier,
                         final boolean ioBound) {
        this.fixedCount = fixedCount;
        this.base = base;
        this.cpuMultiplier = cpuMultiplier;
        this.ioBound = ioBound;
    }

    /**
     * Fixed number of threads. Count must be &gt;= 1.
     *
     * @param count the exact number of threads
     * @return a ThreadPolicy with a fixed thread count
     * @throws IllegalArgumentException if count &lt; 1
     */
    public static ThreadPolicy fixed(final int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Thread count must be >= 1, got: " + count);
        }
        return new ThreadPolicy(count, 0, 0, false);
    }

    /**
     * Threads = multiplier * available CPU cores, rounded, min 1.
     * Multiplier must be &gt; 0.
     *
     * @param multiplier factor applied to available CPU core count
     * @return a ThreadPolicy proportional to CPU cores
     * @throws IllegalArgumentException if multiplier &lt;= 0
     */
    public static ThreadPolicy cpuCores(final double multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("CPU multiplier must be > 0, got: " + multiplier);
        }
        return new ThreadPolicy(0, 0, multiplier, false);
    }

    /**
     * Threads = base + round(multiplier * available CPU cores), min 1.
     * Base must be &gt;= 0, multiplier must be &gt; 0.
     *
     * Example: cpuCoresWithBase(2, 0.25) on 8-core = 2 + 2 = 4, on 16-core = 2 + 4 = 6, on 24-core = 2 + 6 = 8.
     *
     * @param base fixed base thread count added to the CPU-proportional portion
     * @param multiplier factor applied to available CPU core count
     * @return a ThreadPolicy that combines a fixed base with a CPU-proportional count
     * @throws IllegalArgumentException if base &lt; 0 or multiplier &lt;= 0
     */
    public static ThreadPolicy cpuCoresWithBase(final int base, final double multiplier) {
        if (base < 0) {
            throw new IllegalArgumentException("Base must be >= 0, got: " + base);
        }
        if (multiplier <= 0) {
            throw new IllegalArgumentException("CPU multiplier must be > 0, got: " + multiplier);
        }
        return new ThreadPolicy(0, base, multiplier, false);
    }

    /**
     * Exactly {@code count} threads for a queue whose consumers spend most of their time blocked —
     * typically on I/O.
     *
     * Virtual threads are used when the runtime supports them (JDK 25+, see
     * {@code VirtualThreads}); otherwise the queue falls back to {@code count} platform threads.
     * The count is identical either way — only the thread substrate changes, so concurrency,
     * batching, back-pressure and per-partition ordering are unaffected by the fallback.
     *
     * The count is mandatory and has no CPU-proportional form: it expresses how many concurrent
     * blocking calls the downstream service tolerates, which does not follow core count.
     *
     * @param count the exact number of threads
     * @return a ThreadPolicy with a fixed thread count, marked as blocking-dominated
     * @throws IllegalArgumentException if count &lt; 1
     */
    public static ThreadPolicy ioBound(final int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Thread count must be >= 1, got: " + count);
        }
        return new ThreadPolicy(count, 0, 0, true);
    }

    /**
     * Resolve the actual thread count. Always returns &gt;= 1.
     *
     * @return the resolved thread count, at least 1
     */
    public int resolve() {
        if (fixedCount > 0) {
            return fixedCount;
        }
        return Math.max(1, base + (int) Math.round(cpuMultiplier * Runtime.getRuntime().availableProcessors()));
    }

    /**
     * @return true when this queue's consumers are expected to block, making virtual threads
     * appropriate where the runtime provides them
     */
    boolean isIoBound() {
        return ioBound;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ThreadPolicy that = (ThreadPolicy) o;
        return fixedCount == that.fixedCount
            && base == that.base
            && Double.compare(that.cpuMultiplier, cpuMultiplier) == 0
            && ioBound == that.ioBound;
    }

    @Override
    public int hashCode() {
        int result = fixedCount;
        result = 31 * result + base;
        final long temp = Double.doubleToLongBits(cpuMultiplier);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (ioBound ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        if (fixedCount > 0) {
            return ioBound ? "ioBound(" + fixedCount + ")" : "fixed(" + fixedCount + ")";
        }
        if (base > 0) {
            return "cpuCoresWithBase(" + base + ", " + cpuMultiplier + ")";
        }
        return "cpuCores(" + cpuMultiplier + ")";
    }
}
