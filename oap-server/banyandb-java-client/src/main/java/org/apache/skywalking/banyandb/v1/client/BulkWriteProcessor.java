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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;

/**
 * BulkWriteProcessor is a timeline and size dual driven processor.
 * <p>
 * It includes an internal queue and timer, and accept the data sequentially. With the given thresholds of time and
 * size, it could activate flush to continue the process to the next step.
 */
public abstract class BulkWriteProcessor implements Closeable {
    protected final int flushInterval;
    protected DataCarrier buffer;

    /**
     * Create the processor.
     *
     * @param maxBulkSize   the max bulk size for the flush operation
     * @param flushInterval if given maxBulkSize is not reached in this period, the flush would be trigger
     *                      automatically. Unit is second.
     * @param concurrency   the number of concurrency would run for the flush max.
     */
    protected BulkWriteProcessor(String processorName, int maxBulkSize, int flushInterval, int concurrency) {
        this.flushInterval = flushInterval;
        this.buffer = new DataCarrier(processorName, maxBulkSize, concurrency);
        Properties properties = new Properties();
        properties.put("maxBulkSize", maxBulkSize);
        properties.put("flushInterval", flushInterval);
        properties.put("BulkWriteProcessor", this);
        buffer.consume(QueueWatcher.class, concurrency, 20, properties);
    }

    @Override
    public void close() throws IOException {
        this.buffer.shutdownConsumers();
    }

    /**
     * The internal queue consumer for build process.
     */
    public static class QueueWatcher implements IConsumer {
        private long lastFlushTimestamp;
        private int maxBulkSize;
        private int flushInterval;
        private List cachedData;
        private BulkWriteProcessor bulkWriteProcessor;

        public QueueWatcher() {
        }

        @Override
        public void init(Properties properties) {
            lastFlushTimestamp = System.currentTimeMillis();
            maxBulkSize = (Integer) properties.get("maxBulkSize");
            flushInterval = (Integer) properties.get("flushInterval") * 1000;
            cachedData = new ArrayList(maxBulkSize);
            bulkWriteProcessor = (BulkWriteProcessor) properties.get("BulkWriteProcessor");
        }

        @Override
        public void consume(final List data) {
            if (data.size() >= maxBulkSize) {
                // The data#size actually wouldn't over the maxBulkSize due to the DataCarrier channel's max size.
                // This is just to preventing unexpected case and avoid confusion about dropping into else section.
                bulkWriteProcessor.flush(data);
                lastFlushTimestamp = System.currentTimeMillis();
            } else {
                data.forEach(element -> {
                    cachedData.add(element);
                    if (cachedData.size() >= maxBulkSize) {
                        // Flush and re-init.
                        bulkWriteProcessor.flush(cachedData);
                        cachedData = new ArrayList(maxBulkSize);
                        lastFlushTimestamp = System.currentTimeMillis();
                    }
                });
            }
        }

        @Override
        public void onError(final List data, final Throwable t) {

        }

        @Override
        public void onExit() {

        }

        @Override
        public void nothingToConsume() {
            if (System.currentTimeMillis() - lastFlushTimestamp > flushInterval) {
                bulkWriteProcessor.flush(cachedData);
                cachedData = new ArrayList(maxBulkSize);
                lastFlushTimestamp = System.currentTimeMillis();
            }
        }
    }

    /**
     * @param data to be flush.
     */
    protected abstract void flush(List data);
}
