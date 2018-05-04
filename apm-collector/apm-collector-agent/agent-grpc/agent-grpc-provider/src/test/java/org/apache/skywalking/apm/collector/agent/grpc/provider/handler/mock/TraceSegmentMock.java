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

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler.mock;

import io.grpc.stub.StreamObserver;
import java.util.List;
import org.apache.skywalking.apm.network.proto.UniqueId;
import org.apache.skywalking.apm.network.proto.UpstreamSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
class TraceSegmentMock {

    private static final Logger logger = LoggerFactory.getLogger(TraceSegmentMock.class);

    void mock(List<StreamObserver<UpstreamSegment>> upstreamSegments, Long[] times, boolean isPrepare) {
        long startTime = System.currentTimeMillis();
        long lastTime = System.currentTimeMillis();
        for (int i = 0; i < times.length; i++) {
            long startTimestamp = times[i];

            UniqueId.Builder globalTraceId = UniqueIdBuilder.INSTANCE.create();

            ConsumerMock consumerMock = new ConsumerMock();
            UniqueId.Builder consumerSegmentId = UniqueIdBuilder.INSTANCE.create();
            consumerMock.mock(upstreamSegments.get(i % 4), globalTraceId, consumerSegmentId, startTimestamp, isPrepare);

            ProviderMock providerMock = new ProviderMock();
            UniqueId.Builder providerSegmentId = UniqueIdBuilder.INSTANCE.create();
            providerMock.mock(upstreamSegments.get(i % 4), globalTraceId, providerSegmentId, consumerSegmentId, startTimestamp, isPrepare);

            if (i % 10000 == 0) {
                logger.info("sending segment number: {}", i);
            }

            if (i % 10000 == 0) {
                long endTime = System.currentTimeMillis();
                if (endTime - startTime > 0) {
                    logger.info("tps: {}", ((long)i * 2 * 1000) / (endTime - startTime));
                }
            }

            if (i % 5000 == 0) {
                long endTime = System.currentTimeMillis();
                long duration = endTime - lastTime;
                if (duration > 0 && duration < 1000) {
                    try {
                        logger.info("begin sleeping...");
                        Thread.sleep(1000 - duration + 100);
                        logger.info("sleep: {}", 1000 - duration + 100);
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage(), e);
                    }
                } else {
                    try {
                        logger.info("begin sleeping...");
                        Thread.sleep(duration);
                        logger.info("sleep: {}", duration);
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
                lastTime = System.currentTimeMillis();
            }
        }
        logger.info("sending segment number: {}", times.length);
    }
}
