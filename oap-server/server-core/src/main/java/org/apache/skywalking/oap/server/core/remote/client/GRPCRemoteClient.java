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

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamDataClassGetter;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.*;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteClient implements RemoteClient {

    private static final Logger logger = LoggerFactory.getLogger(GRPCRemoteClient.class);

    private final Address address;
    private final DataCarrier<RemoteMessage> carrier;
    private final StreamDataClassGetter streamDataClassGetter;
    private final AtomicInteger concurrentStreamObserverNumber = new AtomicInteger(0);
    private GRPCClient client;
    private boolean isConnect;

    public GRPCRemoteClient(StreamDataClassGetter streamDataClassGetter, Address address, int channelSize,
        int bufferSize) {
        this.streamDataClassGetter = streamDataClassGetter;
        this.address = address;
        this.carrier = new DataCarrier<>("GRPCRemoteClient", channelSize, bufferSize);
        this.carrier.setBufferStrategy(BufferStrategy.BLOCKING);
    }

    @Override public void connect() {
        if (Objects.isNull(client)) {
            synchronized (GRPCRemoteClient.class) {
                if (Objects.isNull(client)) {
                    this.client = new GRPCClient(address.getHost(), address.getPort());
                }
            }
        }

        if (!isConnect) {
            this.client.connect();
            this.carrier.consume(new RemoteMessageConsumer(), 1);
            this.isConnect = true;
        }
    }

    public ManagedChannel getChannel() {
        return this.client.getChannel();
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
        RemoteServiceGrpc.RemoteServiceStub stub = RemoteServiceGrpc.newStub(getChannel());

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

    @Override public void close() {
        if (Objects.nonNull(this.carrier)) {
            this.carrier.shutdownConsumers();
        }
        if (Objects.nonNull(this.client)) {
            this.client.shutdown();
        }
    }

    @Override public Address getAddress() {
        return address;
    }

    @Override public int compareTo(RemoteClient o) {
        return address.toString().compareTo(o.getAddress().toString());
    }
}
