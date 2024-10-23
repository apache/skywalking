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
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.ISegmentParserService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class TraceSegmentReportHandler {
    private final ISegmentParserService segmentParserService;
    private final HistogramMetrics histogram;
    private final CounterMetrics errorCounter;

    public TraceSegmentReportHandler(ModuleManager moduleManager) {
        this.segmentParserService = moduleManager.find(AnalyzerModule.NAME)
                                                 .provider()
                                                 .getService(ISegmentParserService.class);
        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric(
            "trace_in_latency_seconds", "The process latency of trace data",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
        errorCounter = metricsCreator.createCounter(
            "trace_analysis_error_count", "The error number of trace analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
    }

    @Post("/v3/segment")
    public Commands collectSegment(final SegmentObject segment) {
        try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
            segmentParserService.send(segment);
        } catch (Exception e) {
            errorCounter.inc();
            throw e;
        }
        return Commands.newBuilder().build();
    }

    @Post("/v3/segments")
    public Commands collectSegments(final List<SegmentObject> segments) {
        try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
            segments.forEach(segmentParserService::send);
        } catch (Exception e) {
            errorCounter.inc();
            throw e;
        }

        return Commands.newBuilder().build();
    }
}
