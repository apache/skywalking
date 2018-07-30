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
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.indicator.define.IndicatorMapper;
import org.apache.skywalking.oap.server.core.analysis.worker.define.WorkerMapper;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class RemoteServiceHandler extends RemoteServiceGrpc.RemoteServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(RemoteServiceHandler.class);

    private final IndicatorMapper indicatorMapper;
    private final WorkerMapper workerMapper;

    public RemoteServiceHandler(ModuleManager moduleManager) {
        this.indicatorMapper = moduleManager.find(CoreModule.NAME).getService(IndicatorMapper.class);
        this.workerMapper = moduleManager.find(CoreModule.NAME).getService(WorkerMapper.class);
    }

    @Override public StreamObserver<RemoteMessage> call(StreamObserver<Empty> responseObserver) {
        return new StreamObserver<RemoteMessage>() {
            @Override public void onNext(RemoteMessage message) {
                int indicatorId = message.getIndicatorId();
                int nextWorkerId = message.getNextWorkerId();
                RemoteData remoteData = message.getRemoteData();

                Class<Indicator> indicatorClass = indicatorMapper.findClassById(indicatorId);
                try {
                    indicatorClass.newInstance().deserialize(remoteData);
                } catch (InstantiationException | IllegalAccessException e) {
                    logger.warn(e.getMessage());
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
