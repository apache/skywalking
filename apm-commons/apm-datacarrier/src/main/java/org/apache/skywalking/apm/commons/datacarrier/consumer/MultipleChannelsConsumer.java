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

import java.util.*;
import org.apache.skywalking.apm.commons.datacarrier.buffer.*;

/**
 * MultipleChannelsConsumer represent a single consumer thread, but support multiple channels with their {@link
 * IConsumer}s
 *
 * @author wusheng
 */
public class MultipleChannelsConsumer extends Thread {
    private volatile boolean running;
    private volatile ArrayList<Group> consumeTargets;
    private volatile long size;
    private final long consumeCycle;

    public MultipleChannelsConsumer(String threadName, long consumeCycle) {
        super(threadName);
        this.consumeTargets = new ArrayList<Group>();
        this.consumeCycle = consumeCycle;
    }

    @Override
    public void run() {
        running = true;

        while (running) {
            boolean hasData = false;
            for (Group target : consumeTargets) {
                hasData = hasData || consume(target);
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
            consume(target);

            target.consumer.onExit();
        }
    }

    private boolean consume(Group target) {
        boolean hasData;
        LinkedList consumeList = new LinkedList();
        for (int i = 0; i < target.channels.getChannelSize(); i++) {
            Buffer buffer = target.channels.getBuffer(i);
            consumeList.addAll(buffer.obtain());
        }

        if (hasData = consumeList.size() > 0) {
            try {
                target.consumer.consume(consumeList);
            } catch (Throwable t) {
                target.consumer.onError(consumeList, t);
            }
        }
        return hasData;
    }

    /**
     * Add a new target channels.
     *
     * @param channels
     * @param consumer
     */
    public void addNewTarget(Channels channels, IConsumer consumer) {
        Group group = new Group(channels, consumer);
        // Recreate the new list to avoid change list while the list is used in consuming.
        ArrayList<Group> newList = new ArrayList<Group>();
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

    private class Group {
        private Channels channels;
        private IConsumer consumer;

        public Group(Channels channels, IConsumer consumer) {
            this.channels = channels;
            this.consumer = consumer;
        }
    }
}
