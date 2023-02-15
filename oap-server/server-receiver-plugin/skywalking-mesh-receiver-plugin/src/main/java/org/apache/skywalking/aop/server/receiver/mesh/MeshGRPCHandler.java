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

package org.apache.skywalking.aop.server.receiver.mesh;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.servicemesh.v3.MeshProbeDownstream;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetrics;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetricServiceGrpc;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeshGRPCHandler extends ServiceMeshMetricServiceGrpc.ServiceMeshMetricServiceImplBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(MeshGRPCHandler.class);

    public MeshGRPCHandler(ModuleManager moduleManager) {

    }

    @Override
    public StreamObserver<ServiceMeshMetrics> collect(StreamObserver<MeshProbeDownstream> responseObserver) {
        return new StreamObserver<ServiceMeshMetrics>() {
            @Override
            public void onNext(ServiceMeshMetrics metrics) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Received mesh metrics: {}", metrics);
                }

                TelemetryDataDispatcher.process(metrics);
            }

            @Override
            public void onError(Throwable throwable) {
                Status status = Status.fromThrowable(throwable);
                if (Status.CANCELLED.getCode() == status.getCode()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(throwable.getMessage(), throwable);
                    }
                    return;
                }
                LOGGER.error(throwable.getMessage(), throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(MeshProbeDownstream.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
