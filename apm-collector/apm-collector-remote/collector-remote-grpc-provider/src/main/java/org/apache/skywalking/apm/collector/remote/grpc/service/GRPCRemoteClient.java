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

package org.apache.skywalking.apm.collector.remote.grpc.service;

import io.grpc.stub.StreamObserver;
import java.util.List;
import org.apache.skywalking.apm.collector.client.grpc.GRPCClient;
import org.apache.skywalking.apm.collector.remote.grpc.proto.Empty;
import org.apache.skywalking.apm.collector.remote.grpc.proto.RemoteCommonServiceGrpc;
import org.apache.skywalking.apm.collector.remote.grpc.proto.RemoteMessage;
import org.apache.skywalking.apm.collector.remote.service.RemoteClient;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataIDGetter;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataMappingIdNotFoundException;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteClient implements RemoteClient {

    private static final Logger logger = LoggerFactory.getLogger(GRPCRemoteClient.class);

    private final GRPCRemoteSerializeService service;
    private final GRPCClient client;
    private final DataCarrier<RemoteMessage> carrier;
    private final String address;
    private final RemoteDataIDGetter remoteDataIDGetter;

    GRPCRemoteClient(GRPCClient client, RemoteDataIDGetter remoteDataIDGetter, int channelSize, int bufferSize) {
        this.address = client.toString();
        this.client = client;
        this.service = new GRPCRemoteSerializeService();
        this.remoteDataIDGetter = remoteDataIDGetter;
        this.carrier = new DataCarrier<>(channelSize, bufferSize);
        this.carrier.setBufferStrategy(BufferStrategy.BLOCKING);
        this.carrier.consume(new RemoteMessageConsumer(), 1);
    }

    @Override public final String getAddress() {
        return this.address;
    }

    @Override public void push(int graphId, int nodeId, org.apache.skywalking.apm.collector.core.data.RemoteData data) {
        try {
            Integer remoteDataId = remoteDataIDGetter.getRemoteDataId(data.getClass());
            RemoteMessage.Builder builder = RemoteMessage.newBuilder();
            builder.setGraphId(graphId);
            builder.setNodeId(nodeId);
            builder.setRemoteDataId(remoteDataId);
            builder.setRemoteData(service.serialize(data));

            this.carrier.produce(builder.build());
        } catch (RemoteDataMappingIdNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
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
        RemoteCommonServiceGrpc.RemoteCommonServiceStub stub = RemoteCommonServiceGrpc.newStub(client.getChannel());

        StreamStatus status = new StreamStatus(false);
        return stub.call(new StreamObserver<Empty>() {
            @Override public void onNext(Empty empty) {
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
            }

            @Override public void onCompleted() {
                status.finished();
            }
        });
    }

    class StreamStatus {

        private final Logger logger = LoggerFactory.getLogger(StreamStatus.class);

        private volatile boolean status;

        StreamStatus(boolean status) {
            this.status = status;
        }

        public boolean isFinish() {
            return status;
        }

        void finished() {
            this.status = true;
        }

        /**
         * @param maxTimeout max wait time, milliseconds.
         */
        public void wait4Finish(long maxTimeout) {
            long time = 0;
            while (!status) {
                if (time > maxTimeout) {
                    break;
                }
                try2Sleep(5);
                time += 5;
            }
        }

        /**
         * Try to sleep, and ignore the {@link InterruptedException}
         *
         * @param millis the length of time to sleep in milliseconds
         */
        private void try2Sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    @Override public boolean equals(String address) {
        return this.address.equals(address);
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        GRPCRemoteClient client = (GRPCRemoteClient)o;

        return address != null ? address.equals(client.address) : client.address == null;
    }

    @Override public int hashCode() {
        return address != null ? address.hashCode() : 0;
    }

    @Override public int compareTo(RemoteClient o) {
        return this.address.compareTo(o.getAddress());
    }
}
