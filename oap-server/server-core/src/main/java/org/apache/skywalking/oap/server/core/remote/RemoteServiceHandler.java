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
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.define.StreamDataMappingGetter;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.*;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceGetter;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
import org.slf4j.*;

/**
 * This class is Server-side streaming RPC implementation. It's a common service for OAP servers to receive message from
 * each others. The stream data id is used to find the object to deserialize message. The next worker id is used to find
 * the worker to process message.
 *
 * @author peng-yongsheng
 */
public class RemoteServiceHandler extends RemoteServiceGrpc.RemoteServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(RemoteServiceHandler.class);

    private final ModuleDefineHolder moduleDefineHolder;
    private StreamDataMappingGetter streamDataMappingGetter;
    private IWorkerInstanceGetter workerInstanceGetter;
    private CounterMetrics remoteInCounter;
    private CounterMetrics remoteInErrorCounter;
    private CounterMetrics remoteInTargetNotFoundCounter;
    private HistogramMetrics remoteInHistogram;

    public RemoteServiceHandler(ModuleDefineHolder moduleDefineHolder) {
        this.moduleDefineHolder = moduleDefineHolder;

        remoteInCounter = moduleDefineHolder.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class)
            .createCounter("remote_in_count", "The number(server side) of inside remote inside aggregate rpc.",
                MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        remoteInErrorCounter = moduleDefineHolder.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class)
            .createCounter("remote_in_error_count", "The error number(server side) of inside remote inside aggregate rpc.",
                MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        remoteInTargetNotFoundCounter = moduleDefineHolder.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class)
            .createCounter("remote_in_target_not_found_count", "The error number(server side) of inside remote handler target worker not found. May be caused by unmatched OAL scrips.",
                MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        remoteInHistogram = moduleDefineHolder.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class)
            .createHistogramMetric("remote_in_latency", "The latency(server side) of inside remote inside aggregate rpc.",
                MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
    }

    @Override public StreamObserver<RemoteMessage> call(StreamObserver<Empty> responseObserver) {
        if (Objects.isNull(streamDataMappingGetter)) {
            synchronized (RemoteServiceHandler.class) {
                if (Objects.isNull(streamDataMappingGetter)) {
                    streamDataMappingGetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(StreamDataMappingGetter.class);
                }
            }
        }

        if (Objects.isNull(workerInstanceGetter)) {
            synchronized (RemoteServiceHandler.class) {
                if (Objects.isNull(workerInstanceGetter)) {
                    workerInstanceGetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(IWorkerInstanceGetter.class);
                }
            }
        }

        return new StreamObserver<RemoteMessage>() {
            @Override public void onNext(RemoteMessage message) {
                remoteInCounter.inc();
                HistogramMetrics.Timer timer = remoteInHistogram.createTimer();
                try {
                    int streamDataId = message.getStreamDataId();
                    String nextWorkerName = message.getNextWorkName();
                    RemoteData remoteData = message.getRemoteData();

                    Class<? extends StreamData> streamDataClass = streamDataMappingGetter.findClassById(streamDataId);
                    try {
                        StreamData streamData = streamDataClass.newInstance();
                        streamData.deserialize(remoteData);
                        AbstractWorker nextWorker = workerInstanceGetter.get(nextWorkerName);
                        if (nextWorker != null) {
                            nextWorker.in(streamData);
                        } else {
                            remoteInTargetNotFoundCounter.inc();
                            logger.warn("Work name [{}] not found. Check OAL script, make sure they are same in the whole cluster.", nextWorkerName);
                        }
                    } catch (Throwable t) {
                        remoteInErrorCounter.inc();
                        logger.error(t.getMessage(), t);
                    }
                } finally {
                    timer.finish();
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
