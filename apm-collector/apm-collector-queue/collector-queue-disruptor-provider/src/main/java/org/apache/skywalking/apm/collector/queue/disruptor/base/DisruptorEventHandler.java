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


package org.apache.skywalking.apm.collector.queue.disruptor.base;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import org.apache.skywalking.apm.collector.core.CollectorException;
import org.apache.skywalking.apm.collector.core.data.EndOfBatchQueueMessage;
import org.apache.skywalking.apm.collector.queue.base.QueueExecutor;
import org.apache.skywalking.apm.collector.queue.base.MessageHolder;
import org.apache.skywalking.apm.collector.queue.base.QueueEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class DisruptorEventHandler<MESSAGE extends EndOfBatchQueueMessage> implements EventHandler<MessageHolder<MESSAGE>>, QueueEventHandler<MESSAGE> {

    private final Logger logger = LoggerFactory.getLogger(DisruptorEventHandler.class);

    private RingBuffer<MessageHolder<MESSAGE>> ringBuffer;
    private QueueExecutor<MESSAGE> executor;

    DisruptorEventHandler(RingBuffer<MessageHolder<MESSAGE>> ringBuffer, QueueExecutor<MESSAGE> executor) {
        this.ringBuffer = ringBuffer;
        this.executor = executor;
    }

    /**
     * Receive the message from disruptor, when message in disruptor is empty, then send the cached data
     * to the next workers.
     *
     * @param event published to the {@link RingBuffer}
     * @param sequence of the event being processed
     * @param endOfBatch flag to indicate if this is the last event in a batch from the {@link RingBuffer}
     */
    public void onEvent(MessageHolder<MESSAGE> event, long sequence, boolean endOfBatch) throws CollectorException {
        MESSAGE message = event.getMessage();
        event.reset();

        message.setEndOfBatch(endOfBatch);
        executor.execute(message);
    }

    /**
     * Push the message into disruptor ring buffer.
     *
     * @param message of the data to process.
     */
    public void tell(MESSAGE message) {
        long sequence = ringBuffer.next();
        try {
            ringBuffer.get(sequence).setMessage(message);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
