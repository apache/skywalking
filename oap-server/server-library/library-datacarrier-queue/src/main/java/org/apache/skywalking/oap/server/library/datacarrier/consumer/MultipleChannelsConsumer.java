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

package org.apache.skywalking.oap.server.library.datacarrier.consumer;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.Channels;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.QueueBuffer;

/**
 * MultipleChannelsConsumer represent a single consumer thread, but support multiple channels with their {@link
 * IConsumer}s
 */
public class MultipleChannelsConsumer extends Thread {
    private volatile boolean running;
    private volatile ArrayList<Group> consumeTargets;
    @SuppressWarnings("NonAtomicVolatileUpdate")
    private volatile long size;
    private final long consumeCycle;

    public MultipleChannelsConsumer(String threadName, long consumeCycle) {
        super(threadName);
        this.consumeTargets = new ArrayList<>();
        this.consumeCycle = consumeCycle;
    }

    @Override
    public void run() {
        running = true;

        final List consumeList = new ArrayList(2000);
        while (running) {
            boolean hasData = false;
            for (Group target : consumeTargets) {
                boolean consume = target.consume(consumeList);
                hasData = hasData || consume;
            }

            if (!hasData) {
                try {
                    Thread.sleep(consumeCycle);
                } catch (InterruptedException e) {
                }
            }
        }

        // consumer thread is going to stop
        // consume the last time
        for (Group target : consumeTargets) {
            target.consume(consumeList);
        }
    }

    /**
     * Add a new target channels.
     */
    public void addNewTarget(Channels channels, IConsumer consumer) {
        Group group = new Group(channels, consumer);
        // Recreate the new list to avoid change list while the list is used in consuming.
        ArrayList<Group> newList = new ArrayList<Group>();
        newList.addAll(consumeTargets);
        newList.add(group);
        consumeTargets = newList;
        size += channels.size();
    }

    public long size() {
        return size;
    }

    void shutdown() {
        running = false;
    }

    private static class Group {
        private Channels channels;
        private IConsumer consumer;
        /**
         * Priority determines the consuming strategy. On default every period consumer thread loops all groups trying
         * to fetch the data from queue, if the queue only contains few elements, it is too expensive to consume every
         * time.
         *
         * if 'size of last fetched data' > 0
         *
         * priority = 'size of last fetched data' * 100 / {@link Channels#size()} * {@link Channels#getChannelSize()}
         *
         * else
         *
         * priority = priority / 2
         *
         * Meaning, priority is the load factor of {@link #channels}
         *
         * After consuming loop, priority = (priority of current loop + priority of last loop) / 2.
         *
         * If priority > 50, consuming happens in next loop, otherwise, priority += 10, and wait until priority > 50. In
         * worth case, for a low traffic group, consuming happens in 1/10.
         *
         * Priority only exists in {@link MultipleChannelsConsumer}, because it has limited threads but has to consume
         * from a large set of queues.
         *
         * @since 9.0.0
         */
        private int priority;
        private short continuousNoDataCount;

        private Group(Channels channels, IConsumer consumer) {
            this.channels = channels;
            this.consumer = consumer;
            this.priority = 0;
            this.continuousNoDataCount = 0;
        }

        /**
         * @return false if there is no data to consume, or priority is too low. Read {@link #priority} for more
         * details.
         * @since 9.0.0
         */
        private boolean consume(List consumeList) {
            try {
                if (priority < 50) {
                    priority += 10;
                    return false;
                }

                for (int i = 0; i < channels.getChannelSize(); i++) {
                    QueueBuffer buffer = channels.getBuffer(i);
                    buffer.obtain(consumeList);
                }

                if (!consumeList.isEmpty()) {
                    priority = (priority + (int) (consumeList.size() * 100 / channels.getChannelSize() * channels.size())) / 2;
                    try {
                        consumer.consume(consumeList);
                    } catch (Throwable t) {
                        consumer.onError(consumeList, t);
                    } finally {
                        consumeList.clear();
                    }
                    continuousNoDataCount = 0;
                    return true;
                } else {
                    if (continuousNoDataCount < 5) {
                        continuousNoDataCount++;
                        // For low traffic queue (low traffic means occasionally no data
                        // cut priority to half to reduce consuming period.
                        priority /= 2;
                    } else {
                        // For cold queue, the consuming happens in 1/10;
                        priority = -50;
                    }
                }

                consumer.nothingToConsume();
                return false;
            } finally {
                consumer.onExit();
            }
        }
    }
}
