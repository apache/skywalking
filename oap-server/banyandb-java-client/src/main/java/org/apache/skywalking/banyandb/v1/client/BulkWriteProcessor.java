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

package org.apache.skywalking.banyandb.v1.client;

import java.util.List;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;

/**
 * BulkWriteProcessor is a timeline and size dual driven processor.
 *
 * It includes an internal queue and timer, and accept the data sequentially. With the given thresholds of time and
 * size, it could activate {@link #flush()} to continue the process to the next step.
 */
public abstract class BulkWriteProcessor<T> {
    private DataCarrier queue;

    /**
     * Create the processor.
     *
     * @param maxBulkSize   the max bulk size for the flush operation
     * @param flushInterval if given maxBulkSize is not reached in this period, the flush would be trigger
     *                      automatically
     * @param concurrency   the number of concurrency would run for the flush max.
     */
    protected BulkWriteProcessor(String processorName, int maxBulkSize, int flushInterval, int concurrency) {
        this.queue = new DataCarrier(processorName, maxBulkSize * 2, 2);
        queue.consume(QueueWatcher.class, concurrency);
    }

    /**
     * The internal queue consumer for buld process.
     */
    private static class QueueWatcher implements IConsumer {
        @Override
        public void init() {

        }

        @Override
        public void consume(final List data) {

        }

        @Override
        public void onError(final List data, final Throwable t) {

        }

        @Override
        public void onExit() {

        }

        @Override
        public void nothingToConsume() {

        }
    }

    protected abstract void flush();
}
