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


package org.apache.skywalking.apm.commons.datacarrier.consumer;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.commons.datacarrier.buffer.Buffer;

/**
 * Created by wusheng on 2016/10/25.
 */
public class ConsumerThread<T> extends Thread {
    private volatile boolean running;
    private IConsumer<T> consumer;
    private List<DataSource> dataSources;
    private long consumeCycle;

    ConsumerThread(String threadName, IConsumer<T> consumer, long consumeCycle) {
        super(threadName);
        this.consumer = consumer;
        running = false;
        dataSources = new LinkedList<DataSource>();
        this.consumeCycle = consumeCycle;
    }

    /**
     * add partition of buffer to consume
     *
     * @param sourceBuffer
     * @param start
     * @param end
     */
    void addDataSource(Buffer<T> sourceBuffer, int start, int end) {
        this.dataSources.add(new DataSource(sourceBuffer, start, end));
    }

    /**
     * add whole buffer to consume
     *
     * @param sourceBuffer
     */
    void addDataSource(Buffer<T> sourceBuffer) {
        this.dataSources.add(new DataSource(sourceBuffer, 0, sourceBuffer.getBufferSize()));
    }

    @Override
    public void run() {
        running = true;

        while (running) {
            boolean hasData = consume();

            if (!hasData) {
                try {
                    Thread.sleep(consumeCycle);
                } catch (InterruptedException e) {
                }
            }
        }

        // consumer thread is going to stop
        // consume the last time
        consume();

        consumer.onExit();
    }

    private boolean consume() {
        boolean hasData = false;
        LinkedList<T> consumeList = new LinkedList<T>();
        for (DataSource dataSource : dataSources) {
            LinkedList<T> data = dataSource.obtain();
            if (data.size() == 0) {
                continue;
            }
            for (T element : data) {
                consumeList.add(element);
            }
            hasData = true;
        }

        if (consumeList.size() > 0) {
            try {
                consumer.consume(consumeList);
            } catch (Throwable t) {
                consumer.onError(consumeList, t);
            }
        }
        return hasData;
    }

    void shutdown() {
        running = false;
    }

    /**
     * DataSource is a refer to {@link Buffer}.
     */
    class DataSource {
        private Buffer<T> sourceBuffer;
        private int start;
        private int end;

        DataSource(Buffer<T> sourceBuffer, int start, int end) {
            this.sourceBuffer = sourceBuffer;
            this.start = start;
            this.end = end;
        }

        LinkedList<T> obtain() {
            return sourceBuffer.obtain(start, end);
        }
    }
}
