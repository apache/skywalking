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
 */

package org.apache.skywalking.oap.server.receiver.event.rest;

import com.linecorp.armeria.server.annotation.Post;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.event.v3.Event;
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerService;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class EventRestServiceHandler {
    private final HistogramMetrics histogram;

    private final CounterMetrics errorCounter;

    private final EventAnalyzerService eventAnalyzerService;

    public EventRestServiceHandler(final ModuleManager manager) {
        final MetricsCreator metricsCreator = manager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);

        eventAnalyzerService = manager.find(EventAnalyzerModule.NAME)
                                      .provider()
                                      .getService(EventAnalyzerService.class);

        histogram = metricsCreator.createHistogramMetric(
            "event_in_latency_seconds", "The process latency of event data",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
        errorCounter = metricsCreator.createCounter(
            "event_error_count", "The error number of event analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
    }

    @Post("/v3/events")
    public Commands collectEvents(final List<Event> events) {
        try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
            events.forEach(e -> {
                // Check event's layer
                if (e.getLayer().isEmpty()) {
                    throw new IllegalArgumentException("layer field is required since v9.0.0, please upgrade your event report tools");
                }
                Layer.nameOf(e.getLayer());

                eventAnalyzerService.analyze(e);
            });
            return Commands.newBuilder().build();
        } catch (Exception e) {
            errorCounter.inc();
            throw e;
        }
    }
}
