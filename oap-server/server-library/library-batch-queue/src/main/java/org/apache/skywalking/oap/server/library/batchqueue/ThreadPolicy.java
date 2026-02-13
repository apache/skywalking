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
 * Two modes:
 * - fixed(N): exactly N threads, regardless of hardware.
 * - cpuCores(multiplier): multiplier * Runtime.availableProcessors(), rounded.
 *
 * Resolved value is always >= 1 — every pool must have at least one thread.
 * fixed() requires count >= 1 at construction. cpuCores() applies max(1, ...) at resolution.
 */
public class ThreadPolicy {
    private final int fixedCount;
    private final double cpuMultiplier;

    private ThreadPolicy(final int fixedCount, final double cpuMultiplier) {
        this.fixedCount = fixedCount;
        this.cpuMultiplier = cpuMultiplier;
    }

    /**
     * Fixed number of threads. Count must be >= 1.
     *
     * @throws IllegalArgumentException if count < 1
     */
    public static ThreadPolicy fixed(final int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Thread count must be >= 1, got: " + count);
        }
        return new ThreadPolicy(count, 0);
    }

    /**
     * Threads = multiplier * available CPU cores, rounded, min 1.
     * Multiplier must be > 0.
     *
     * @throws IllegalArgumentException if multiplier <= 0
     */
    public static ThreadPolicy cpuCores(final double multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("CPU multiplier must be > 0, got: " + multiplier);
        }
        return new ThreadPolicy(0, multiplier);
    }

    /**
     * Resolve the actual thread count. Always returns >= 1.
     */
    public int resolve() {
        if (fixedCount > 0) {
            return fixedCount;
        }
        return Math.max(1, (int) Math.round(cpuMultiplier * Runtime.getRuntime().availableProcessors()));
    }

    @Override
    public String toString() {
        if (fixedCount > 0) {
            return "fixed(" + fixedCount + ")";
        }
        return "cpuCores(" + cpuMultiplier + ")";
    }
}
