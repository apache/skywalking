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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.skywalking.apm.commons.datacarrier.EnvUtil;
import org.apache.skywalking.apm.commons.datacarrier.buffer.Channels;

/**
 * BulkConsumePool works for consuming data from multiple channels(DataCarrier instances), with multiple {@link
 * MultipleChannelsConsumer}s.
 * <p>
 * In typical case, the number of {@link MultipleChannelsConsumer} should be less than the number of channels.
 */
public class BulkConsumePool implements ConsumerPool {
    private List<MultipleChannelsConsumer> allConsumers;
    private volatile boolean isStarted = false;

    public BulkConsumePool(String name, int size, long consumeCycle) {
        size = EnvUtil.getInt(name + "_THREAD", size);
        allConsumers = new ArrayList<MultipleChannelsConsumer>(size);
        for (int i = 0; i < size; i++) {
            MultipleChannelsConsumer multipleChannelsConsumer = new MultipleChannelsConsumer("DataCarrier." + name + ".BulkConsumePool." + i + ".Thread", consumeCycle);
            multipleChannelsConsumer.setDaemon(true);
            allConsumers.add(multipleChannelsConsumer);
        }
    }

    @Override
    synchronized public void add(String name, Channels channels, IConsumer consumer) {
        MultipleChannelsConsumer multipleChannelsConsumer = getLowestPayload();
        multipleChannelsConsumer.addNewTarget(channels, consumer);
    }

    /**
     * Get the lowest payload consumer thread based on current allocate status.
     *
     * @return the lowest consumer.
     */
    private MultipleChannelsConsumer getLowestPayload() {
        MultipleChannelsConsumer winner = allConsumers.get(0);
        for (int i = 1; i < allConsumers.size(); i++) {
            MultipleChannelsConsumer option = allConsumers.get(i);
            if (option.size() < winner.size()) {
                winner = option;
            }
        }
        return winner;
    }

    /**
     *
     */
    @Override
    public boolean isRunning(Channels channels) {
        return isStarted;
    }

    @Override
    public void close(Channels channels) {
        for (MultipleChannelsConsumer consumer : allConsumers) {
            consumer.shutdown();
        }
    }

    @Override
    public void begin(Channels channels) {
        if (isStarted) {
            return;
        }
        for (MultipleChannelsConsumer consumer : allConsumers) {
            consumer.start();
        }
        isStarted = true;
    }

    /**
     * The creator for {@link BulkConsumePool}.
     */
    public static class Creator implements Callable<ConsumerPool> {
        private String name;
        private int size;
        private long consumeCycle;

        public Creator(String name, int poolSize, long consumeCycle) {
            this.name = name;
            this.size = poolSize;
            this.consumeCycle = consumeCycle;
        }

        @Override
        public ConsumerPool call() {
            return new BulkConsumePool(name, size, consumeCycle);
        }

        public static int recommendMaxSize() {
            return Runtime.getRuntime().availableProcessors() * 2;
        }
    }
}
