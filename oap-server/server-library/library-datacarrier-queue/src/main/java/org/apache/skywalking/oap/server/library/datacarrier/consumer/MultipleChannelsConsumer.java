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
import lombok.Setter;
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
    // The flag to indicate whether the consumer thread should consume data.
    @Setter
    private volatile boolean consumeFlag = false;
    // Consumer has two modes to drive consumption. 1. Polling mode. 2. Signal-Driven mode.
    private final boolean isSignalDrivenMode;

    public MultipleChannelsConsumer(String threadName, long consumeCycle, boolean isSignalDrivenMode) {
        super(threadName);
        this.consumeTargets = new ArrayList<>();
        this.consumeCycle = consumeCycle;
        this.isSignalDrivenMode = isSignalDrivenMode;
    }

    @Override
    public void run() {
        running = true;

        final List consumeList = new ArrayList(2000);
        while (running) {
            boolean hasData = false;
            if (!isSignalDrivenMode) {
                for (Group target : consumeTargets) {
                    boolean consumed = consume(target, consumeList);
                    hasData = hasData || consumed;
                }

                if (!hasData) {
                    try {
                        Thread.sleep(consumeCycle);
                    } catch (InterruptedException e) {
                    }
                }
            } else {
                if (consumeFlag) {
                    consumeFlag = false;
                    for (Group target : consumeTargets) {
                        consume(target, consumeList);
                    }
                } else {
                    try {
                        Thread.sleep(consumeCycle);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        // consumer thread is going to stop
        // consume the last time
        for (Group target : consumeTargets) {
            consume(target, consumeList);

            target.consumer.onExit();
        }
    }

    private boolean consume(Group target, List consumeList) {
        for (int i = 0; i < target.channels.getChannelSize(); i++) {
            QueueBuffer buffer = target.channels.getBuffer(i);
            buffer.obtain(consumeList);
        }

        if (!consumeList.isEmpty()) {
            try {
                target.consumer.consume(consumeList);
            } catch (Throwable t) {
                target.consumer.onError(consumeList, t);
            } finally {
                consumeList.clear();
            }
            return true;
        }
        target.consumer.nothingToConsume();
        return false;
    }

    /**
     * Add a new target channels.
     */
    public void addNewTarget(Channels channels, IConsumer consumer) {
        Group group = new Group(channels, consumer);
        // Recreate the new list to avoid change list while the list is used in consuming.
        ArrayList<Group> newList = new ArrayList<>();
        for (Group target : consumeTargets) {
            newList.add(target);
        }
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

        public Group(Channels channels, IConsumer consumer) {
            this.channels = channels;
            this.consumer = consumer;
        }
    }
}
