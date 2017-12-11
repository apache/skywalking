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

import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.apache.skywalking.apm.collector.queue.base.DaemonThreadFactory;
import org.apache.skywalking.apm.collector.queue.base.MessageHolder;
import org.apache.skywalking.apm.collector.queue.base.QueueCreator;
import org.apache.skywalking.apm.collector.queue.base.QueueEventHandler;
import org.apache.skywalking.apm.collector.queue.base.QueueExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class DisruptorQueueCreator implements QueueCreator {

    private final Logger logger = LoggerFactory.getLogger(DisruptorQueueCreator.class);

    @Override public QueueEventHandler create(int queueSize, QueueExecutor executor) {
        // Specify the size of the ring buffer, must be power of 2.
        if (!((((queueSize - 1) & queueSize) == 0) && queueSize != 0)) {
            throw new IllegalArgumentException("queue size must be power of 2");
        }

        // Construct the Disruptor
        Disruptor<MessageHolder> disruptor = new Disruptor<>(MessageHolderFactory.INSTANCE, queueSize, DaemonThreadFactory.INSTANCE);
        disruptor.setDefaultExceptionHandler(new ExceptionHandler<MessageHolder>() {
            @Override public void handleEventException(Throwable ex, long sequence, MessageHolder event) {
                logger.error("Handle disruptor error event! message: {}.", event.getMessage(), ex);
            }

            @Override public void handleOnStartException(Throwable ex) {
                logger.error("create disruptor queue failed!", ex);
            }

            @Override public void handleOnShutdownException(Throwable ex) {
                logger.error("shutdown disruptor queue failed!", ex);
            }
        });

        RingBuffer<MessageHolder> ringBuffer = disruptor.getRingBuffer();
        DisruptorEventHandler eventHandler = new DisruptorEventHandler(ringBuffer, executor);

        // Connect the handler
        disruptor.handleEventsWith(eventHandler);

        // Start the Disruptor, starts all threads running
        disruptor.start();
        return eventHandler;
    }
}
