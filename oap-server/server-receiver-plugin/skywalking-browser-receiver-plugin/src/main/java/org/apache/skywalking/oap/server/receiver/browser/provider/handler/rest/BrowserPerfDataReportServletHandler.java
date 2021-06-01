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

package org.apache.skywalking.oap.server.receiver.browser.provider.handler.rest;

import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfData;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.JettyHandler;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.PerfDataAnalyzer;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.PerfDataParserListenerManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * Collect and process the performance data
 */
@Slf4j
public class BrowserPerfDataReportServletHandler extends JettyHandler {
    private final ModuleManager moduleManager;
    private final BrowserServiceModuleConfig config;
    private final PerfDataParserListenerManager perfDataListenerManager;

    private final HistogramMetrics perfHistogram;
    private final CounterMetrics perfErrorCounter;

    public BrowserPerfDataReportServletHandler(ModuleManager moduleManager,
                                               BrowserServiceModuleConfig config,
                                               PerfDataParserListenerManager perfDataListenerManager) {
        this.moduleManager = moduleManager;
        this.config = config;
        this.perfDataListenerManager = perfDataListenerManager;

        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);
        perfHistogram = metricsCreator.createHistogramMetric(
            "browser_perf_data_in_latency", "The process latency of browser performance data",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
        perfErrorCounter = metricsCreator.createCounter(
            "browser_perf_data_analysis_error_count", "The error number of browser performance data analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
    }

    @Override
    protected void doPost(final HttpServletRequest req,
                          final HttpServletResponse resp) throws ServletException, IOException {
        if (log.isDebugEnabled()) {
            log.debug("receive browser performance data");
        }

        HistogramMetrics.Timer timer = perfHistogram.createTimer();
        try {
            BrowserPerfData browserPerfData = parseBrowserPerfData(req);
            PerfDataAnalyzer analyzer = new PerfDataAnalyzer(moduleManager, perfDataListenerManager, config);
            analyzer.doAnalysis(browserPerfData);
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            perfErrorCounter.inc();
        } finally {
            timer.finish();
        }
    }

    protected BrowserPerfData parseBrowserPerfData(HttpServletRequest request) throws IOException {
        BufferedReader reader = request.getReader();
        String line;
        StringBuilder stringBuilder = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }

        BrowserPerfData.Builder builder = BrowserPerfData.newBuilder();
        ProtoBufJsonUtils.fromJSON(stringBuilder.toString(), builder);
        return builder.build();
    }

    @Override
    public String pathSpec() {
        return "/browser/perfData";
    }
}
