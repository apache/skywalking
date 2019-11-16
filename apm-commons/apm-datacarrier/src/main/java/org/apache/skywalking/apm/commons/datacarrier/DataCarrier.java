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

package org.apache.skywalking.apm.commons.datacarrier;

import org.apache.skywalking.apm.commons.datacarrier.buffer.*;
import org.apache.skywalking.apm.commons.datacarrier.consumer.*;
import org.apache.skywalking.apm.commons.datacarrier.partition.*;

/**
 * DataCarrier main class. use this instance to set Producer/Consumer Model.
 */
public class DataCarrier<T> {
    private final int bufferSize;
    private final int channelSize;
    private Channels<T> channels;
    private IDriver driver;
    private String name;

    public DataCarrier(int channelSize, int bufferSize) {
        this("DEFAULT", channelSize, bufferSize);
    }

    public DataCarrier(String name, int channelSize, int bufferSize) {
        this(name, name, channelSize, bufferSize);
    }

    public DataCarrier(String name, String envPrefix, int channelSize, int bufferSize) {
        this.name = name;
        this.bufferSize = EnvUtil.getInt(envPrefix + "_BUFFER_SIZE", bufferSize);
        this.channelSize = EnvUtil.getInt(envPrefix + "_CHANNEL_SIZE", channelSize);
        channels = new Channels<T>(channelSize, bufferSize, new SimpleRollingPartitioner<T>(), BufferStrategy.BLOCKING);
    }

    /**
     * set a new IDataPartitioner. It will cover the current one or default one.(Default is {@link
     * SimpleRollingPartitioner}
     *
     * @param dataPartitioner to partition data into different channel by some rules.
     * @return DataCarrier instance for chain
     */
    public DataCarrier setPartitioner(IDataPartitioner<T> dataPartitioner) {
        this.channels.setPartitioner(dataPartitioner);
        return this;
    }

    /**
     * override the strategy at runtime. Notice, {@link Channels} will override several channels one by one.
     *
     * @param strategy
     */
    public DataCarrier setBufferStrategy(BufferStrategy strategy) {
        this.channels.setStrategy(strategy);
        return this;
    }

    /**
     * produce data to buffer, using the givven {@link BufferStrategy}.
     *
     * @param data
     * @return false means produce data failure. The data will not be consumed.
     */
    public boolean produce(T data) {
        if (driver != null) {
            if (!driver.isRunning(channels)) {
                return false;
            }
        }

        return this.channels.save(data);
    }

    /**
     * set consumeDriver to this Carrier. consumer begin to run when {@link DataCarrier#produce} begin to work.
     *
     * @param consumerClass class of consumer
     * @param num number of consumer threads
     */
    public DataCarrier consume(Class<? extends IConsumer<T>> consumerClass, int num, long consumeCycle) {
        if (driver != null) {
            driver.close(channels);
        }
        driver = new ConsumeDriver<T>(this.name, this.channels, consumerClass, num, consumeCycle);
        driver.begin(channels);
        return this;
    }

    /**
     * set consumeDriver to this Carrier. consumer begin to run when {@link DataCarrier#produce} begin to work with 20
     * millis consume cycle.
     *
     * @param consumerClass class of consumer
     * @param num number of consumer threads
     */
    public DataCarrier consume(Class<? extends IConsumer<T>> consumerClass, int num) {
        return this.consume(consumerClass, num, 20);
    }

    /**
     * set consumeDriver to this Carrier. consumer begin to run when {@link DataCarrier#produce} begin to work.
     *
     * @param consumer single instance of consumer, all consumer threads will all use this instance.
     * @param num number of consumer threads
     * @return
     */
    public DataCarrier consume(IConsumer<T> consumer, int num, long consumeCycle) {
        if (driver != null) {
            driver.close(channels);
        }
        driver = new ConsumeDriver<T>(this.name, this.channels, consumer, num, consumeCycle);
        driver.begin(channels);
        return this;
    }

    /**
     * set consumeDriver to this Carrier. consumer begin to run when {@link DataCarrier#produce} begin to work with 20
     * millis consume cycle.
     *
     * @param consumer single instance of consumer, all consumer threads will all use this instance.
     * @param num number of consumer threads
     * @return
     */
    public DataCarrier consume(IConsumer<T> consumer, int num) {
        return this.consume(consumer, num, 20);
    }

    /**
     * Set a consumer pool to manage the channels of this DataCarrier. Then consumerPool could use its own consuming
     * model to adjust the consumer thread and throughput.
     *
     * @param consumerPool
     * @return
     */
    public DataCarrier consume(ConsumerPool consumerPool, IConsumer<T> consumer) {
        driver = consumerPool;
        consumerPool.add(this.name, channels, consumer);
        driver.begin(channels);
        return this;
    }

    /**
     * shutdown all consumer threads, if consumer threads are running. Notice {@link BufferStrategy}: if {@link
     * BufferStrategy} == {@link BufferStrategy#BLOCKING}, shutdown consumeDriver maybe cause blocking when producing.
     * Better way to change consumeDriver are use {@link DataCarrier#consume}
     */
    public void shutdownConsumers() {
        if (driver != null) {
            driver.close(channels);
        }
    }
}
