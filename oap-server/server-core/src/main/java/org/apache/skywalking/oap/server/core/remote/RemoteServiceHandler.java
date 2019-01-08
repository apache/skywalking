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

package org.apache.skywalking.oap.server.core.remote;

import io.grpc.stub.StreamObserver;
import java.util.Objects;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamDataClassGetter;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.*;
import org.apache.skywalking.oap.server.core.worker.WorkerInstances;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.slf4j.*;

/**
 * This class is Server-side streaming RPC implementation. It's a common service for OAP servers
 * to receive message from each others.
 * The stream data id is used to find the object to deserialize message.
 * The next worker id is used to find the worker to process message.
 *
 * @author peng-yongsheng
 */
public class RemoteServiceHandler extends RemoteServiceGrpc.RemoteServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(RemoteServiceHandler.class);

    private final ModuleDefineHolder moduleDefineHolder;
    private StreamDataClassGetter streamDataClassGetter;

    public RemoteServiceHandler(ModuleDefineHolder moduleDefineHolder) {
        this.moduleDefineHolder = moduleDefineHolder;
    }

    @Override public StreamObserver<RemoteMessage> call(StreamObserver<Empty> responseObserver) {
        if (Objects.isNull(streamDataClassGetter)) {
            synchronized (RemoteServiceHandler.class) {
                if (Objects.isNull(streamDataClassGetter)) {
                    streamDataClassGetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(StreamDataClassGetter.class);
                }
            }
        }

        return new StreamObserver<RemoteMessage>() {
            @Override public void onNext(RemoteMessage message) {
                int streamDataId = message.getStreamDataId();
                int nextWorkerId = message.getNextWorkerId();
                RemoteData remoteData = message.getRemoteData();

                Class<StreamData> streamDataClass = streamDataClassGetter.findClassById(streamDataId);
                try {
                    StreamData streamData = streamDataClass.newInstance();
                    streamData.deserialize(remoteData);
                    WorkerInstances.INSTANCES.get(nextWorkerId).in(streamData);
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
            }

            @Override public void onCompleted() {
                responseObserver.onNext(Empty.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
