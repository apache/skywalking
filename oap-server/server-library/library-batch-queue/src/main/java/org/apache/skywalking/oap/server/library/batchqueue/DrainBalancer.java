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
 * Strategy for rebalancing partition-to-thread assignments based on observed throughput.
 *
 * <p>The balancer is invoked periodically by the queue's rebalance task. It receives
 * a throughput snapshot and current ownership, and returns a new assignment. The queue
 * infrastructure handles the two-phase handoff protocol to ensure safe reassignment.
 *
 * <p>Implementations only need to solve the assignment problem — they do not need to
 * worry about concurrent handler invocations or data loss, which are handled by
 * {@link BatchQueue}.
 *
 * @see ThroughputWeightedBalancer
 */
public interface DrainBalancer {

    /**
     * Compute new partition-to-task assignments.
     *
     * @param throughput per-partition throughput since last rebalance (snapshot, already reset)
     * @param currentOwner current partition-to-task mapping ({@code currentOwner[p] = taskIndex})
     * @param taskCount number of drain tasks/threads
     * @return new owner for each partition ({@code result[p] = taskIndex}),
     *         or {@code null} to skip rebalancing this cycle
     */
    int[] assign(long[] throughput, int[] currentOwner, int taskCount);

    /**
     * Throughput-weighted balancer using the LPT (Longest Processing Time) heuristic.
     * Sorts partitions by throughput descending, assigns each to the least-loaded thread.
     * Zero-throughput partitions keep their current owner to avoid unnecessary moves.
     * Skips rebalancing when load is already balanced (max/min ratio &lt; 1.15).
     *
     * @return a throughput-weighted drain balancer
     */
    static DrainBalancer throughputWeighted() {
        return new ThroughputWeightedBalancer();
    }
}
