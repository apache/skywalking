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

package org.apache.skywalking.oap.server.receiver.envoy;

import io.envoyproxy.envoy.service.metrics.v3.MetricsServiceGrpc;
import io.envoyproxy.envoy.service.metrics.v3.StreamMetricsMessage;
import io.envoyproxy.envoy.service.metrics.v3.StreamMetricsResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
@RequiredArgsConstructor
public class MetricServiceV3GRPCHandler extends MetricsServiceGrpc.MetricsServiceImplBase {
    private final ModuleManager manager;

    @Override
    @SneakyThrows
    public StreamObserver<StreamMetricsMessage> streamMetrics(StreamObserver<StreamMetricsResponse> responseObserver) {
        final MetricServiceGRPCHandler handler = new MetricServiceGRPCHandler(manager);

        return new StreamObserver<StreamMetricsMessage>() {
            @Override
            public void onNext(StreamMetricsMessage message) {
                handler.handle(message);
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Error in receiving metrics from envoy", throwable);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(StreamMetricsResponse.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
