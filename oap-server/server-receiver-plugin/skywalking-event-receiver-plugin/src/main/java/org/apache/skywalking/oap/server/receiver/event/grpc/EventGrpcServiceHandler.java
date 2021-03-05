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

package org.apache.skywalking.oap.server.receiver.event.grpc;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.event.v3.Event;
import org.apache.skywalking.apm.network.event.v3.EventServiceGrpc;
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerModule;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerService;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class EventGrpcServiceHandler extends EventServiceGrpc.EventServiceImplBase implements GRPCHandler {
    private final HistogramMetrics histogram;

    private final CounterMetrics errorCounter;

    private final EventAnalyzerService eventAnalyzerService;

    public EventGrpcServiceHandler(ModuleManager moduleManager) {
        final MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                           .provider()
                                                           .getService(MetricsCreator.class);

        eventAnalyzerService = moduleManager.find(EventAnalyzerModule.NAME)
                                            .provider()
                                            .getService(EventAnalyzerService.class);

        histogram = metricsCreator.createHistogramMetric(
            "event_in_latency", "The process latency of event data",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("grpc")
        );
        errorCounter = metricsCreator.createCounter(
            "event_error_count", "The error number of event analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("grpc")
        );
    }

    @Override
    public StreamObserver<Event> collect(StreamObserver<Commands> responseObserver) {
        return new StreamObserver<Event>() {
            @Override
            public void onNext(final Event event) {
                try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
                    eventAnalyzerService.analyze(event);
                } catch (Exception e) {
                    errorCounter.inc();
                    log.error(e.getMessage(), e);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error(throwable.getMessage(), throwable);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
