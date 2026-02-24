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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;

/**
 * Immutable snapshot of a {@link BatchQueue}'s partition usage at a point in time.
 *
 * <p>Provides three levels of detail:
 * <ul>
 *   <li><b>Total usage</b> — aggregate across all partitions</li>
 *   <li><b>Per-partition usage</b> — individual partition sizes</li>
 *   <li><b>Top N</b> — the most-loaded partitions, sorted by usage descending</li>
 * </ul>
 */
public class BatchQueueStats {
    @Getter
    private final int partitionCount;
    @Getter
    private final int bufferSize;
    private final int[] partitionUsed;

    BatchQueueStats(final int partitionCount, final int bufferSize, final int[] partitionUsed) {
        this.partitionCount = partitionCount;
        this.bufferSize = bufferSize;
        this.partitionUsed = Arrays.copyOf(partitionUsed, partitionUsed.length);
    }

    /**
     * Total capacity across all partitions: {@code partitionCount * bufferSize}.
     *
     * @return total capacity in item slots
     */
    public long totalCapacity() {
        return (long) partitionCount * bufferSize;
    }

    /**
     * Total number of items currently queued across all partitions.
     *
     * @return sum of items across all partitions
     */
    public int totalUsed() {
        int sum = 0;
        for (final int used : partitionUsed) {
            sum += used;
        }
        return sum;
    }

    /**
     * Overall queue usage as a percentage (0.0–100.0).
     *
     * @return usage percentage across all partitions
     */
    public double totalUsedPercentage() {
        final long capacity = totalCapacity();
        if (capacity == 0) {
            return 0.0;
        }
        return 100.0 * totalUsed() / capacity;
    }

    /**
     * Number of items currently queued in the given partition.
     *
     * @param index the partition index
     * @return number of items in the partition
     */
    public int partitionUsed(final int index) {
        return partitionUsed[index];
    }

    /**
     * Usage of the given partition as a percentage (0.0–100.0).
     *
     * @param index the partition index
     * @return usage percentage for the partition
     */
    public double partitionUsedPercentage(final int index) {
        if (bufferSize == 0) {
            return 0.0;
        }
        return 100.0 * partitionUsed[index] / bufferSize;
    }

    /**
     * Return the top {@code n} most-loaded partitions, sorted by usage descending.
     * If {@code n &gt;= partitionCount}, all partitions are returned.
     *
     * @param n the maximum number of partitions to return
     * @return list of partition usage snapshots sorted by usage descending
     */
    public List<PartitionUsage> topN(final int n) {
        final Integer[] indices = new Integer[partitionCount];
        for (int i = 0; i < partitionCount; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> Integer.compare(partitionUsed[b], partitionUsed[a]));

        final int limit = Math.min(n, partitionCount);
        final List<PartitionUsage> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            final int idx = indices[i];
            result.add(new PartitionUsage(
                idx, partitionUsed[idx],
                bufferSize == 0 ? 0.0 : 100.0 * partitionUsed[idx] / bufferSize
            ));
        }
        return result;
    }

    /**
     * Usage snapshot for a single partition.
     */
    @Getter
    public static class PartitionUsage {
        private final int partitionIndex;
        private final int used;
        private final double usedPercentage;

        PartitionUsage(final int partitionIndex, final int used, final double usedPercentage) {
            this.partitionIndex = partitionIndex;
            this.used = used;
            this.usedPercentage = usedPercentage;
        }
    }
}
