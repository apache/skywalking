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

package org.apache.skywalking.oap.server.receiver.trace.provider.handler.v6.grpc;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;
import org.apache.skywalking.apm.network.language.agent.v2.TraceSegmentReportServiceGrpc;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.receiver.trace.provider.handler.v5.grpc.TraceSegmentServiceHandler;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentParseV2;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentSource;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceSegmentReportServiceHandler extends TraceSegmentReportServiceGrpc.TraceSegmentReportServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(TraceSegmentServiceHandler.class);

    private final SegmentParseV2.Producer segmentProducer;
    private HistogramMetric histogram;

    public TraceSegmentReportServiceHandler(SegmentParseV2.Producer segmentProducer, ModuleManager moduleManager) {
        this.segmentProducer = segmentProducer;
        MetricCreator metricCreator = moduleManager.find(TelemetryModule.NAME).provider().getService(MetricCreator.class);
        histogram = metricCreator.createHistogramMetric("trace_grpc_v6_in_latency", "The process latency of service mesh telemetry",
            MetricTag.EMPTY_KEY, MetricTag.EMPTY_VALUE);
    }

    @Override public StreamObserver<UpstreamSegment> collect(StreamObserver<Commands> responseObserver) {
        return new StreamObserver<UpstreamSegment>() {
            @Override public void onNext(UpstreamSegment segment) {
                if (logger.isDebugEnabled()) {
                    logger.debug("receive segment");
                }

                HistogramMetric.Timer timer = histogram.createTimer();
                try {
                    segmentProducer.send(segment, SegmentSource.Agent);
                } finally {
                    timer.finish();
                }
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
                responseObserver.onCompleted();
            }

            @Override public void onCompleted() {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
