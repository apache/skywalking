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
 * Determines the number of threads for a BatchQueue's dedicated scheduler
 * or for a shared scheduler.
 *
 * Three modes:
 * - fixed(N): exactly N threads, regardless of hardware.
 * - cpuCores(multiplier): multiplier * Runtime.availableProcessors(), rounded.
 * - cpuCoresWithBase(base, multiplier): base + multiplier * Runtime.availableProcessors(), rounded.
 *
 * Resolved value is always &gt;= 1 — every pool must have at least one thread.
 * fixed() requires count &gt;= 1 at construction. cpuCores() applies max(1, ...) at resolution.
 */
public class ThreadPolicy {
    private final int fixedCount;
    private final int base;
    private final double cpuMultiplier;

    private ThreadPolicy(final int fixedCount, final int base, final double cpuMultiplier) {
        this.fixedCount = fixedCount;
        this.base = base;
        this.cpuMultiplier = cpuMultiplier;
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
        return new ThreadPolicy(count, 0, 0);
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
        return new ThreadPolicy(0, 0, multiplier);
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
        return new ThreadPolicy(0, base, multiplier);
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
            && Double.compare(that.cpuMultiplier, cpuMultiplier) == 0;
    }

    @Override
    public int hashCode() {
        int result = fixedCount;
        result = 31 * result + base;
        final long temp = Double.doubleToLongBits(cpuMultiplier);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        if (fixedCount > 0) {
            return "fixed(" + fixedCount + ")";
        }
        if (base > 0) {
            return "cpuCoresWithBase(" + base + ", " + cpuMultiplier + ")";
        }
        return "cpuCores(" + cpuMultiplier + ")";
    }
}
