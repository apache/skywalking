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

package org.apache.skywalking.oap.server.recevier.log.provider.handler.rest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.skywalking.apm.network.logging.v3.JSONLog;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.oap.log.analyzer.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.JettyHandler;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class LogRawReportServiceRestHandler extends JettyHandler {
    private final Gson gson = new Gson();

    private final HistogramMetrics histogram;

    private final CounterMetrics errorCounter;

    private final ILogAnalyzerService logAnalyzerService;

    public LogRawReportServiceRestHandler(final ModuleManager moduleManager) {
        final MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                           .provider()
                                                           .getService(MetricsCreator.class);

        logAnalyzerService = moduleManager.find(LogAnalyzerModule.NAME)
                                          .provider()
                                          .getService(ILogAnalyzerService.class);

        histogram = metricsCreator.createHistogramMetric(
            "log_in_latency", "The process latency of log",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
        errorCounter = metricsCreator.createCounter(
            "log_analysis_error_count", "The error number of log analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) {
        try (final HistogramMetrics.Timer ignored = histogram.createTimer()) {

            LogData.Builder base = LogData.newBuilder()
                                          .setService(req.getHeader("service"))
                                          .setServiceInstance(req.getHeader("serviceInstance"))
                                          .setEndpoint(req.getHeader("endpoint"));

            final BufferedReader reader = req.getReader();
            switch (req.getContentType()) {
                case "application/json": {
                    final StringBuilder content = new StringBuilder();
                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        content.append(line);
                    }
                    final JsonArray array = gson.fromJson(content.toString(), JsonArray.class);
                    ArrayList<LogData.Builder> logs = new ArrayList<>(array.size());
                    for (final JsonElement it : array) {
                        LogData.Builder builder = base.clone();
                        JSONLog log = JSONLog.newBuilder().setJson(it.getAsString()).build();
                        builder.setBody(LogDataBody.newBuilder().setJson(log).build());
                        logs.add(builder);
                    }

                    logs.forEach(it -> logAnalyzerService.doAnalysis(it, null));
                    break;
                }
                case "text/plain": {
                    List<String> lines = Lists.newArrayList();
                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        lines.add(line);
                    }
                    lines.forEach(it -> {
                        LogData.Builder builder = base.clone();
                        builder.setBody(LogDataBody.newBuilder().setText(TextLog.newBuilder().setText(it)).build());
                        logAnalyzerService.doAnalysis(builder, null);
                    });
                    break;
                }
                default:
                    break;
            }
        } catch (final Exception e) {
            log.error(e.getMessage(), e);
            errorCounter.inc();
        }
    }

    @Override
    public String pathSpec() {
        return "/v3/logs/raw";
    }
}
