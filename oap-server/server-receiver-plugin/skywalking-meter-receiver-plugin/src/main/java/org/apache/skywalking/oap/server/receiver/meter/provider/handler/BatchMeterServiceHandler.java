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
public class BatchMeterServiceHandler extends MeterReportServiceGrpc.MeterReportServiceImplBase implements GRPCHandler {

    private final IMeterProcessService processService;
    private final HistogramMetrics histogram;
    private final CounterMetrics errorCounter;

    public BatchMeterServiceHandler(ModuleManager manager, IMeterProcessService processService) {
        this.processService = processService;
        MetricsCreator metricsCreator = manager.find(TelemetryModule.NAME)
                                               .provider()
                                               .getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric(
            "batch_meter_in_latency", "The process latency of batch meter",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("grpc")
        );
        errorCounter = metricsCreator.createCounter("batch_meter_analysis_error_count", "The error number of batch meter analysis",
                                                    new MetricsTag.Keys("protocol"),
                                                    new MetricsTag.Values("grpc")
        );
    }

    @Override
    public StreamObserver<MeterDataCollection> collectBatch(StreamObserver<Commands> responseObserver) {
        return new StreamObserver<MeterDataCollection>() {
            @Override
            public void onNext(MeterDataCollection meterDataCollection) {
                final MeterProcessor processor = processService.createProcessor();
                try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
                    for (MeterData meterData : meterDataCollection.getMeterDataList()) {
                        processor.read(meterData);
                    }
                } catch (Exception e) {
                    errorCounter.inc();
                    log.error(e.getMessage(), e);
                }
                processor.process();
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
