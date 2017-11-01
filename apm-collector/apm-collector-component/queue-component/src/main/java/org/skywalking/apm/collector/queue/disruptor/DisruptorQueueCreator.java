/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.queue.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.skywalking.apm.collector.queue.DaemonThreadFactory;
import org.skywalking.apm.collector.queue.MessageHolder;
import org.skywalking.apm.collector.queue.QueueCreator;
import org.skywalking.apm.collector.queue.QueueEventHandler;
import org.skywalking.apm.collector.queue.QueueExecutor;

/**
 * @author peng-yongsheng
 */
public class DisruptorQueueCreator implements QueueCreator {

    @Override public QueueEventHandler create(int queueSize, QueueExecutor executor) {
        // Specify the size of the ring buffer, must be power of 2.
        if (!((((queueSize - 1) & queueSize) == 0) && queueSize != 0)) {
            throw new IllegalArgumentException("queue size must be power of 2");
        }

        // Construct the Disruptor
        Disruptor<MessageHolder> disruptor = new Disruptor(MessageHolderFactory.INSTANCE, queueSize, DaemonThreadFactory.INSTANCE);

        RingBuffer<MessageHolder> ringBuffer = disruptor.getRingBuffer();
        DisruptorEventHandler eventHandler = new DisruptorEventHandler(ringBuffer, executor);

        // Connect the handler
        disruptor.handleEventsWith(eventHandler);

        // Start the Disruptor, starts all threads running
        disruptor.start();
        return eventHandler;
    }
}
