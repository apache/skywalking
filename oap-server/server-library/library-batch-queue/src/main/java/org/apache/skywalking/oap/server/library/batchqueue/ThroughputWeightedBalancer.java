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

import java.util.Arrays;

/**
 * LPT (Longest Processing Time) drain balancer.
 *
 * <p>Sorts partitions by observed throughput in descending order, then assigns each
 * partition to the thread with the least total load. This is the classic multiprocessor
 * scheduling heuristic — O(P log P) for sorting + O(P * T) for assignment.
 *
 * <p>Zero-throughput partitions keep their current owner to avoid unnecessary moves.
 * Returns {@code null} (skip) when load is already balanced (max/min thread load ratio &lt; 1.15)
 * or when there is no throughput. The 1.15 threshold is intentionally low because BLOCKING
 * backpressure compresses the observed throughput ratio — a true 2x imbalance may appear
 * as only ~1.3x in the counters.
 */
class ThroughputWeightedBalancer implements DrainBalancer {

    @Override
    public int[] assign(final long[] throughput, final int[] currentOwner, final int taskCount) {
        final int partitionCount = throughput.length;

        // Compute per-thread load under current assignment to check imbalance
        final long[] threadLoad = new long[taskCount];
        for (int p = 0; p < partitionCount; p++) {
            final int t = currentOwner[p];
            if (t >= 0 && t < taskCount) {
                threadLoad[t] += throughput[p];
            }
        }
        long maxLoad = 0;
        long minLoad = Long.MAX_VALUE;
        for (int t = 0; t < taskCount; t++) {
            if (threadLoad[t] > maxLoad) {
                maxLoad = threadLoad[t];
            }
            if (threadLoad[t] < minLoad) {
                minLoad = threadLoad[t];
            }
        }
        // Skip if load is already balanced (max/min ratio < 1.15, or no throughput)
        if (maxLoad == 0 || (minLoad > 0 && maxLoad * 100 / minLoad < 115)) {
            return null;
        }

        // Sort partitions by throughput descending
        final int[][] sorted = new int[partitionCount][2];
        for (int p = 0; p < partitionCount; p++) {
            sorted[p][0] = p;
            sorted[p][1] = (int) Math.min(throughput[p], Integer.MAX_VALUE);
        }
        Arrays.sort(sorted, (a, b) -> Integer.compare(b[1], a[1]));

        // Assign each partition to the least-loaded thread
        final long[] newThreadLoad = new long[taskCount];
        final int[] newOwner = new int[partitionCount];
        for (final int[] entry : sorted) {
            final int p = entry[0];
            final long tp = throughput[p];

            if (tp == 0) {
                // Zero-throughput partition keeps current owner (avoid unnecessary moves)
                newOwner[p] = currentOwner[p];
                if (newOwner[p] >= 0) {
                    newThreadLoad[newOwner[p]] += tp;
                }
                continue;
            }

            // Find thread with least total load (linear scan — taskCount is small)
            int bestThread = 0;
            for (int t = 1; t < taskCount; t++) {
                if (newThreadLoad[t] < newThreadLoad[bestThread]) {
                    bestThread = t;
                }
            }
            newOwner[p] = bestThread;
            newThreadLoad[bestThread] += tp;
        }

        return newOwner;
    }
}
