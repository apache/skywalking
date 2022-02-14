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

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.event.v3.Event;
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.JettyHandler;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class EventRestServiceHandler extends JettyHandler {
    private final HistogramMetrics histogram;

    private final CounterMetrics errorCounter;

    private final EventAnalyzerService eventAnalyzerService;

    private final Gson gson = new Gson();

    public EventRestServiceHandler(final ModuleManager manager) {
        final MetricsCreator metricsCreator = manager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);

        eventAnalyzerService = manager.find(EventAnalyzerModule.NAME)
                                      .provider()
                                      .getService(EventAnalyzerService.class);

        histogram = metricsCreator.createHistogramMetric(
            "event_in_latency", "The process latency of event data",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
        errorCounter = metricsCreator.createCounter(
            "event_error_count", "The error number of event analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) {
        try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
            List<Event> events = Lists.newArrayList();
            JsonArray array = gson.fromJson(req.getReader(), JsonArray.class);
            for (JsonElement element : array) {
                Event.Builder builder = Event.newBuilder();
                ProtoBufJsonUtils.fromJSON(element.toString(), builder);
                events.add(builder.build());
            }

            events.forEach(eventAnalyzerService::analyze);
        } catch (Exception e) {
            errorCounter.inc();
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public String pathSpec() {
        return "/v3/events";
    }
}
