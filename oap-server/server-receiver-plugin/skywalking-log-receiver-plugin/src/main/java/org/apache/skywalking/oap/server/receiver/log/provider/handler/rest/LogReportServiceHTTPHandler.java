/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.receiver.log.provider.handler.rest;

import com.linecorp.armeria.server.annotation.Post;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class LogReportServiceHTTPHandler {
    private final HistogramMetrics histogram;

    private final CounterMetrics errorCounter;

    private final ILogAnalyzerService logAnalyzerService;

    public LogReportServiceHTTPHandler(final ModuleManager moduleManager) {
        final MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                           .provider()
                                                           .getService(MetricsCreator.class);

        logAnalyzerService = moduleManager.find(LogAnalyzerModule.NAME)
                                          .provider()
                                          .getService(ILogAnalyzerService.class);

        histogram = metricsCreator.createHistogramMetric(
            "log_in_latency", "The process latency of log",
            new MetricsTag.Keys("protocol", "data_format"),
            new MetricsTag.Values("http", "json")
        );
        errorCounter = metricsCreator.createCounter(
            "log_analysis_error_count", "The error number of log analysis",
            new MetricsTag.Keys("protocol", "data_format"),
            new MetricsTag.Values("http", "json")
        );
    }

    @Post("/v3/logs")
    public Commands collectLogs(final List<LogData> logs) {
        try (final HistogramMetrics.Timer ignored = histogram.createTimer()) {
            logs.forEach(it -> logAnalyzerService.doAnalysis(it, null));
            return Commands.newBuilder().build();
        } catch (final Throwable e) {
            errorCounter.inc();
            throw e;
        }
    }
}
