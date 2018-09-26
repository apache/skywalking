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

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.servicemesh.MeshProbeDownstream;
import org.apache.skywalking.apm.network.servicemesh.ServiceMeshMetric;
import org.apache.skywalking.apm.network.servicemesh.ServiceMeshMetricServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeshGRPCHandler extends ServiceMeshMetricServiceGrpc.ServiceMeshMetricServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(MeshGRPCHandler.class);

    @Override
    public StreamObserver<ServiceMeshMetric> collect(StreamObserver<MeshProbeDownstream> responseObserver) {
        return new StreamObserver<ServiceMeshMetric>() {
            @Override public void onNext(ServiceMeshMetric metric) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Received mesh metric: {}", metric);
                }
                TelemetryDataDispatcher.preProcess(metric);
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
                responseObserver.onCompleted();
            }

            @Override public void onCompleted() {
                responseObserver.onNext(MeshProbeDownstream.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
