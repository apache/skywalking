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

package org.skywalking.apm.collector.remote.grpc.service;

import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.client.ClientException;
import org.skywalking.apm.collector.client.grpc.GRPCClient;
import org.skywalking.apm.collector.remote.RemoteDataMappingContainer;
import org.skywalking.apm.collector.remote.grpc.proto.Empty;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteCommonServiceGrpc;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteMessage;
import org.skywalking.apm.collector.remote.service.RemoteClient;
import org.skywalking.apm.collector.remote.service.RemoteClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteClientService implements RemoteClientService {

    private final Logger logger = LoggerFactory.getLogger(GRPCRemoteClientService.class);

    private final RemoteDataMappingContainer container;

    public GRPCRemoteClientService(RemoteDataMappingContainer container) {
        this.container = container;
    }

    @Override public RemoteClient create(String host, int port) {
        GRPCClient client = new GRPCClient(host, port);
        try {
            client.initialize();
        } catch (ClientException e) {
            e.printStackTrace();
        }
        RemoteCommonServiceGrpc.RemoteCommonServiceStub stub = RemoteCommonServiceGrpc.newStub(client.getChannel());
        StreamObserver<RemoteMessage> streamObserver = createStreamObserver(stub);
        return new GRPCRemoteClient(container, streamObserver);
    }

    private StreamObserver<RemoteMessage> createStreamObserver(RemoteCommonServiceGrpc.RemoteCommonServiceStub stub) {
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
        private volatile boolean status;

        public StreamStatus(boolean status) {
            this.status = status;
        }

        public boolean isFinish() {
            return status;
        }

        public void finished() {
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

            }
        }
    }
}
