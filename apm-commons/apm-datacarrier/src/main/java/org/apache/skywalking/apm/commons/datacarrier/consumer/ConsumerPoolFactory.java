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
import java.util.concurrent.Callable;
import org.apache.skywalking.apm.commons.datacarrier.buffer.Channels;

/**
 * Consumer Pool Factory provides global management for all Consumer Pool.
 *
 * @author wusheng
 */
public enum ConsumerPoolFactory {
    INSTANCE;

    private Map<String, ConsumerPool> pools;

    ConsumerPoolFactory() {
        pools = new HashMap<String, ConsumerPool>();
    }

    public synchronized boolean createIfAbsent(String poolName, Callable<ConsumerPool> creator) throws Exception {
        if (pools.containsKey(poolName)) {
            return false;
        } else {
            pools.put(poolName, creator.call());
            return true;
        }
    }

    public ConsumerPool get(String poolName) {
        return pools.get(poolName);
    }

    /**
     * Default pool provides the same capabilities as DataCarrier#consume(IConsumer, 1), which alloc one thread for one
     * DataCarrier.
     */
    public static final ConsumerPool DEFAULT_POOL = new ConsumerPool() {
        private Map<Channels, ConsumeDriver> allDrivers = new HashMap<Channels, ConsumeDriver>();

        @Override synchronized public void add(String name, Channels channels, IConsumer consumer) {
            if (!allDrivers.containsKey(channels)) {
                ConsumeDriver consumeDriver = new ConsumeDriver(name, channels, consumer, 1, 20);
                allDrivers.put(channels, consumeDriver);
            }
        }

        @Override public boolean isRunning(Channels channels) {
            ConsumeDriver driver = allDrivers.get(channels);
            return driver == null ? false : driver.isRunning(channels);
        }

        @Override public void close(Channels channels) {
            ConsumeDriver driver = allDrivers.get(channels);
            if (driver != null) {
                driver.close(channels);
            }
        }

        @Override public void begin(Channels channels) {
            ConsumeDriver driver = allDrivers.get(channels);
            if (driver != null) {
                driver.begin(channels);
            }
        }
    };
}
