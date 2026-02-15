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
 * Strategy for selecting which partition a produced item should be placed into.
 *
 * <p>The default implementation ({@link #typeHash()}) uses the item's class hash,
 * ensuring all items of the same type land on the same partition. This eliminates
 * the need for HashMap grouping during dispatch and avoids contention on shared
 * counters with many concurrent producers.
 *
 * <p>Only used when the queue has multiple partitions. Single-partition queues
 * bypass the selector entirely.
 *
 * @param <T> the queue element type
 */
@FunctionalInterface
public interface PartitionSelector<T> {

    /**
     * Select a partition index for the given data item.
     *
     * @param data the item being produced
     * @param partitionCount total number of partitions (always &gt; 1)
     * @return a partition index in [0, partitionCount)
     */
    int select(T data, int partitionCount);

    /**
     * Default selector: routes by {@code data.getClass().hashCode()}.
     * Same type always hits the same partition, so each consumer thread
     * drains pre-grouped batches — dispatch grouping is effectively a no-op.
     *
     * @param <T> the queue element type
     * @return a selector that partitions by item class hash
     */
    static <T> PartitionSelector<T> typeHash() {
        return (data, count) -> (data.getClass().hashCode() & 0x7FFFFFFF) % count;
    }
}
