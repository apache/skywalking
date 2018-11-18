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

package org.apache.skywalking.oap.server.core.remote.client;

import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamDataClassGetter;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.*;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteClient implements RemoteClient, Comparable<GRPCRemoteClient> {

    private static final Logger logger = LoggerFactory.getLogger(GRPCRemoteClient.class);

    private final GRPCClient client;
    private final DataCarrier<RemoteMessage> carrier;
    private final StreamDataClassGetter streamDataClassGetter;
    private final AtomicInteger concurrentStreamObserverNumber = new AtomicInteger(0);

    public GRPCRemoteClient(StreamDataClassGetter streamDataClassGetter, RemoteInstance remoteInstance, int channelSize,
        int bufferSize) {
        this.streamDataClassGetter = streamDataClassGetter;
        this.client = new GRPCClient(remoteInstance.getHost(), remoteInstance.getPort());
        this.client.initialize();
        this.carrier = new DataCarrier<>("GRPCRemoteClient", channelSize, bufferSize);
        this.carrier.setBufferStrategy(BufferStrategy.BLOCKING);
        this.carrier.consume(new RemoteMessageConsumer(), 1);
    }

    @Override public void push(int nextWorkerId, StreamData streamData) {
        int streamDataId = streamDataClassGetter.findIdByClass(streamData.getClass());
        RemoteMessage.Builder builder = RemoteMessage.newBuilder();
        builder.setNextWorkerId(nextWorkerId);
        builder.setStreamDataId(streamDataId);
        builder.setRemoteData(streamData.serialize());

        this.carrier.produce(builder.build());
    }

    class RemoteMessageConsumer implements IConsumer<RemoteMessage> {
        @Override public void init() {
        }

        @Override public void consume(List<RemoteMessage> remoteMessages) {
            StreamObserver<RemoteMessage> streamObserver = createStreamObserver();

            for (RemoteMessage remoteMessage : remoteMessages) {
                streamObserver.onNext(remoteMessage);
            }
            streamObserver.onCompleted();
        }

        @Override public void onError(List<RemoteMessage> remoteMessages, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }
    }

    private StreamObserver<RemoteMessage> createStreamObserver() {
        RemoteServiceGrpc.RemoteServiceStub stub = RemoteServiceGrpc.newStub(client.getChannel());

        int sleepTotalMillis = 0;
        int sleepMillis = 10;
        while (concurrentStreamObserverNumber.incrementAndGet() > 10) {
            concurrentStreamObserverNumber.addAndGet(-1);

            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }

            sleepTotalMillis += sleepMillis;

            if (sleepTotalMillis > 60000) {
                logger.warn("Remote client block times over 60 seconds.");
            }
        }

        return stub.call(new StreamObserver<Empty>() {
            @Override public void onNext(Empty empty) {
            }

            @Override public void onError(Throwable throwable) {
                concurrentStreamObserverNumber.addAndGet(-1);
                logger.error(throwable.getMessage(), throwable);
            }

            @Override public void onCompleted() {
                concurrentStreamObserverNumber.addAndGet(-1);
            }
        });
    }

    @Override public int compareTo(GRPCRemoteClient o) {
        return this.client.toString().compareTo(o.client.toString());
    }

    public String getHost() {
        return client.getHost();
    }

    public int getPort() {
        return client.getPort();
    }
}
