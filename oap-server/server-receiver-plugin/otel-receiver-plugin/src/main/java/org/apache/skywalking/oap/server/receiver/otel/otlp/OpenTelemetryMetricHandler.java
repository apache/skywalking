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

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.otel.Handler;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

@Slf4j
@RequiredArgsConstructor
public class OpenTelemetryMetricHandler
    extends MetricsServiceGrpc.MetricsServiceImplBase
    implements Handler {
    private final ModuleManager manager;

    private final OpenTelemetryMetricRequestProcessor metricRequestProcessor;

    @Override
    public String type() {
        return "otlp";
    }

    @Override
    public void active() throws ModuleStartException {
        GRPCHandlerRegister grpcHandlerRegister = manager.find(SharingServerModule.NAME)
                                                         .provider()
                                                         .getService(GRPCHandlerRegister.class);
        grpcHandlerRegister.addHandler(this);
    }

    @Override
    public void export(
        final ExportMetricsServiceRequest requests,
        final StreamObserver<ExportMetricsServiceResponse> responseObserver) {
        metricRequestProcessor.processMetricsRequest(requests);
        responseObserver.onNext(ExportMetricsServiceResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

}
