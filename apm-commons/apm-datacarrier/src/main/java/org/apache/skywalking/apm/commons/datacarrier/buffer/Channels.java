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

package org.apache.skywalking.apm.commons.datacarrier.buffer;

import org.apache.skywalking.apm.commons.datacarrier.partition.IDataPartitioner;

/**
 * Channels of Buffer It contains all buffer data which belongs to this channel. It supports several strategy when
 * buffer is full. The Default is BLOCKING <p> Created by wusheng on 2016/10/25.
 */
public class Channels<T> {
    private final QueueBuffer<T>[] bufferChannels;
    private IDataPartitioner<T> dataPartitioner;
    private final BufferStrategy strategy;
    private final long size;

    public Channels(int channelSize, int bufferSize, IDataPartitioner<T> partitioner, BufferStrategy strategy) {
        this.dataPartitioner = partitioner;
        this.strategy = strategy;
        bufferChannels = new QueueBuffer[channelSize];
        for (int i = 0; i < channelSize; i++) {
            if (BufferStrategy.BLOCKING.equals(strategy)) {
                bufferChannels[i] = new ArrayBlockingQueueBuffer<>(bufferSize, strategy);
            } else {
                bufferChannels[i] = new Buffer<>(bufferSize, strategy);
            }
        }
        // noinspection PointlessArithmeticExpression
        size = 1L * channelSize * bufferSize; // it's not pointless, it prevents numeric overflow before assigning an integer to a long
    }

    public boolean save(T data) {
        int index = dataPartitioner.partition(bufferChannels.length, data);
        int retryCountDown = 1;
        if (BufferStrategy.IF_POSSIBLE.equals(strategy)) {
            int maxRetryCount = dataPartitioner.maxRetryCount();
            if (maxRetryCount > 1) {
                retryCountDown = maxRetryCount;
            }
        }
        for (; retryCountDown > 0; retryCountDown--) {
            if (bufferChannels[index].save(data)) {
                return true;
            }
        }
        return false;
    }

    public void setPartitioner(IDataPartitioner<T> dataPartitioner) {
        this.dataPartitioner = dataPartitioner;
    }

    /**
     * override the strategy at runtime. Notice, this will override several channels one by one. So, when running
     * setStrategy, each channel may use different BufferStrategy
     */
    public void setStrategy(BufferStrategy strategy) {
        for (QueueBuffer<T> buffer : bufferChannels) {
            buffer.setStrategy(strategy);
        }
    }

    /**
     * get channelSize
     */
    public int getChannelSize() {
        return this.bufferChannels.length;
    }

    public long size() {
        return size;
    }

    public QueueBuffer<T> getBuffer(int index) {
        return this.bufferChannels[index];
    }
}
