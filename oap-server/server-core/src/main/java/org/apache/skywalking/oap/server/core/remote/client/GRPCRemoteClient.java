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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.Empty;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteMessage;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteServiceGrpc;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a wrapper of the gRPC client for sending message to each other OAP server. It contains a block queue to
 * buffering the message and sending the message by batch.
 *
 * @author peng-yongsheng
 */
public class GRPCRemoteClient implements RemoteClient {

    private static final Logger logger = LoggerFactory.getLogger(GRPCRemoteClient.class);

    private final int channelSize;
    private final int bufferSize;
    private final Address address;
    private final AtomicInteger concurrentStreamObserverNumber = new AtomicInteger(0);
    private GRPCClient client;
    private DataCarrier<RemoteMessage> carrier;
    private boolean isConnect;
    private CounterMetrics remoteOutCounter;
    private CounterMetrics remoteOutErrorCounter;
    private int remoteTimeout;

    public GRPCRemoteClient(ModuleDefineHolder moduleDefineHolder, Address address, int channelSize,
        int bufferSize, int remoteTimeout) {
        this.address = address;
        this.channelSize = channelSize;
        this.bufferSize = bufferSize;
        this.remoteTimeout = remoteTimeout;

        remoteOutCounter = moduleDefineHolder.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class)
            .createCounter("remote_out_count", "The number(client side) of inside remote inside aggregate rpc.",
                new MetricsTag.Keys("dest", "self"), new MetricsTag.Values(address.toString(), "N"));
        remoteOutErrorCounter = moduleDefineHolder.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class)
            .createCounter("remote_out_error_count", "The error number(client side) of inside remote inside aggregate rpc.",
                new MetricsTag.Keys("dest", "self"), new MetricsTag.Values(address.toString(), "N"));
    }

    @Override public void connect() {
        if (!isConnect) {
            this.getClient().connect();
            this.getDataCarrier().consume(new RemoteMessageConsumer(), 1);
            this.isConnect = true;
        }
    }

    /**
     * Get channel state by the true value of request connection.
     *
     * @return a channel when the state to be ready
     */
    ManagedChannel getChannel() {
        return getClient().getChannel();
    }

    GRPCClient getClient() {
        if (Objects.isNull(client)) {
            synchronized (GRPCRemoteClient.class) {
                if (Objects.isNull(client)) {
                    this.client = new GRPCClient(address.getHost(), address.getPort());
                }
            }
        }
        return client;
    }

    RemoteServiceGrpc.RemoteServiceStub getStub() {
        return RemoteServiceGrpc.newStub(getChannel());
    }

    DataCarrier<RemoteMessage> getDataCarrier() {
        if (Objects.isNull(this.carrier)) {
            synchronized (GRPCRemoteClient.class) {
                if (Objects.isNull(this.carrier)) {
                    this.carrier = new DataCarrier<>("GRPCRemoteClient", channelSize, bufferSize);
                }
            }
        }
        return this.carrier;
    }

    /**
     * Push stream data which need to send to another OAP server.
     *
     * @param nextWorkerName the name of a worker which will process this stream data.
     * @param streamData the entity contains the values.
     */
    @Override public void push(String nextWorkerName, StreamData streamData) {
        RemoteMessage.Builder builder = RemoteMessage.newBuilder();
        builder.setNextWorkerName(nextWorkerName);
        builder.setRemoteData(streamData.serialize());

        this.getDataCarrier().produce(builder.build());
    }

    class RemoteMessageConsumer implements IConsumer<RemoteMessage> {
        @Override public void init() {
        }

        @Override public void consume(List<RemoteMessage> remoteMessages) {
            try {
                StreamObserver<RemoteMessage> streamObserver = createStreamObserver();
                for (RemoteMessage remoteMessage : remoteMessages) {
                    remoteOutCounter.inc();
                    streamObserver.onNext(remoteMessage);
                }
                streamObserver.onCompleted();
            } catch (Throwable t) {
                remoteOutErrorCounter.inc();
                logger.error(t.getMessage(), t);
            }
        }

        @Override public void onError(List<RemoteMessage> remoteMessages, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }
    }

    /**
     * Create a gRPC stream observer to sending stream data, one stream observer could send multiple stream data by a
     * single consume. The max number of concurrency allowed at the same time is 10.
     *
     * @return stream observer
     */
    private StreamObserver<RemoteMessage> createStreamObserver() {
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

        return getStub().withDeadlineAfter(remoteTimeout, TimeUnit.SECONDS).call(new StreamObserver<Empty>() {
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
        return address.compareTo(o.getAddress());
    }
}
