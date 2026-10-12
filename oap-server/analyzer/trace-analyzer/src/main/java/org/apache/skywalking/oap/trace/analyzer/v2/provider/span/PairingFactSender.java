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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueue;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueConfig;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueManager;
import org.apache.skywalking.oap.server.library.batchqueue.BufferStrategy;
import org.apache.skywalking.oap.server.library.batchqueue.HandlerConsumer;
import org.apache.skywalking.oap.server.library.batchqueue.ThreadPolicy;

/**
 * Hands pairing facts from the receiver threads to the cluster without ever blocking them. The remote client's
 * queue to a peer blocks its producer when full, which is the right backpressure for the aggregation workers that
 * normally feed it, but a receiver thread waiting there would stall OTLP ingestion behind a slow peer. A fact is a
 * sample anyway: dropping one only delays learning an address by a call, so a full buffer drops instead.
 */
@Slf4j
public class PairingFactSender {
    private final String queueName;
    private final BatchQueue<PairingFact> queue;
    private final AtomicLong dropped = new AtomicLong();

    /**
     * @param queueName  the queue's unique name
     * @param bufferSize the facts held while the sending thread catches up
     * @param send       what sends one fact on, run on the queue's own thread
     */
    public PairingFactSender(final String queueName, final int bufferSize, final Consumer<PairingFact> send) {
        this.queueName = queueName;
        this.queue = BatchQueueManager.create(queueName, BatchQueueConfig.<PairingFact>builder()
            .threads(ThreadPolicy.fixed(1))
            .bufferSize(bufferSize)
            .strategy(BufferStrategy.IF_POSSIBLE)
            .consumer(new HandlerConsumer<PairingFact>() {
                @Override
                public void consume(final List<PairingFact> facts) {
                    for (final PairingFact fact : facts) {
                        try {
                            send.accept(fact);
                        } catch (final RuntimeException e) {
                            log.debug("A pairing fact could not be sent", e);
                        }
                    }
                }
            })
            .minIdleMs(10)
            .maxIdleMs(100)
            .build());
    }

    /**
     * @param fact a fact to route to its trace's owner
     * @return whether it was queued; false when the buffer is full and the fact was dropped
     */
    public boolean offer(final PairingFact fact) {
        if (queue.produce(fact)) {
            return true;
        }
        if (dropped.getAndIncrement() == 0) {
            log.warn("The pairing fact buffer is full, so a peer node is slow to take them; facts are dropped "
                + "rather than holding up the OTLP receiver, which only delays learning an address");
        }
        return false;
    }

    /**
     * @return the facts dropped since start
     */
    public long dropped() {
        return dropped.get();
    }

    public void shutdown() {
        BatchQueueManager.shutdown(queueName);
    }
}
