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

package org.apache.skywalking.oap.server.receiver.meter.provider.handler;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterDataCollection;
import org.apache.skywalking.apm.network.language.agent.v3.MeterReportServiceGrpc;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.IMeterProcessService;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.MeterProcessor;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * Meter protocol receiver, collect and process the meters.
 */
@Slf4j
public class MeterServiceHandler extends MeterReportServiceGrpc.MeterReportServiceImplBase implements GRPCHandler {

    private final IMeterProcessService processService;
    private final HistogramMetrics histogram;
    private final CounterMetrics errorCounter;

    public MeterServiceHandler(ModuleManager manager, IMeterProcessService processService) {
        this.processService = processService;
        MetricsCreator metricsCreator = manager.find(TelemetryModule.NAME)
                .provider()
                .getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric(
                "meter_in_latency_seconds", "The process latency of meter",
                new MetricsTag.Keys("protocol"), new MetricsTag.Values("grpc")
        );
        errorCounter = metricsCreator.createCounter("meter_analysis_error_count", "The error number of meter analysis",
                new MetricsTag.Keys("protocol"),
                new MetricsTag.Values("grpc")
        );
    }

    @Override
    public StreamObserver<MeterData> collect(StreamObserver<Commands> responseObserver) {
        final MeterProcessor processor = processService.createProcessor();
        return new StreamObserver<MeterData>() {
            @Override
            public void onNext(MeterData meterData) {
                try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
                    processor.read(meterData);
                } catch (Exception e) {
                    errorCounter.inc();
                    log.error(e.getMessage(), e);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                processor.process();
                Status status = Status.fromThrowable(throwable);
                if (Status.CANCELLED.getCode() == status.getCode()) {
                    if (log.isDebugEnabled()) {
                        log.debug(throwable.getMessage(), throwable);
                    }
                    return;
                }
                log.error(throwable.getMessage(), throwable);
            }

            @Override
            public void onCompleted() {
                processor.process();
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<MeterDataCollection> collectBatch(StreamObserver<Commands> responseObserver) {
        return new StreamObserver<MeterDataCollection>() {
            @Override
            public void onNext(MeterDataCollection meterDataCollection) {
                final MeterProcessor processor = processService.createProcessor();
                try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
                    meterDataCollection.getMeterDataList().forEach(processor::read);
                    processor.process();
                } catch (Exception e) {
                    errorCounter.inc();
                    log.error(e.getMessage(), e);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                Status status = Status.fromThrowable(throwable);
                if (Status.CANCELLED.getCode() == status.getCode()) {
                    if (log.isDebugEnabled()) {
                        log.debug(throwable.getMessage(), throwable);
                    }
                    return;
                }
                log.error(throwable.getMessage(), throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
