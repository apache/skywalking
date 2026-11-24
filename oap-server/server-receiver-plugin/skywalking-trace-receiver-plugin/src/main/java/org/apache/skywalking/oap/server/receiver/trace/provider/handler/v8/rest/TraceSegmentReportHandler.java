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

package org.apache.skywalking.oap.server.receiver.trace.provider.handler.v8.rest;

import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.Blocking;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.core.trace.TraceAnalysisError;
import org.apache.skywalking.oap.server.core.trace.TraceInLatency;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.ISegmentParserService;

@Slf4j
@Blocking
public class TraceSegmentReportHandler {
    private final ISegmentParserService segmentParserService;
    private final HistogramMetrics histogram;
    private final CounterMetrics errorCounter;

    public TraceSegmentReportHandler(ModuleManager moduleManager) {
        this.segmentParserService = moduleManager.find(TraceAnalyzerModule.NAME)
                                                 .provider()
                                                 .getService(ISegmentParserService.class);
        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric(
            TraceInLatency.NAME, TraceInLatency.HELP,
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
        errorCounter = metricsCreator.createCounter(
            TraceAnalysisError.NAME, TraceAnalysisError.HELP,
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
    }

    @Post("/v3/segment")
    public Commands collectSegment(final SegmentObject segment) {
        send(segment);
        return Commands.newBuilder().build();
    }

    @Post("/v3/segments")
    public Commands collectSegments(final List<SegmentObject> segments) {
        segments.forEach(this::send);
        return Commands.newBuilder().build();
    }

    private void send(final SegmentObject segment) {
        final long startNanos = System.nanoTime();
        try {
            segmentParserService.send(segment);
        } catch (Exception e) {
            errorCounter.inc();
            throw e;
        } finally {
            TraceInLatency.observeSpans(histogram, startNanos, segment.getSpansCount());
        }
    }
}
