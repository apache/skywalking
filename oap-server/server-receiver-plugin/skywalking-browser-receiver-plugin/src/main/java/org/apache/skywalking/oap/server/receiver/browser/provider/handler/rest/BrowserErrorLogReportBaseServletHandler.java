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

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.JettyHandler;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.ErrorLogAnalyzer;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.ErrorLogParserListenerManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * Collect and process the error log
 */
@Slf4j
public abstract class BrowserErrorLogReportBaseServletHandler extends JettyHandler {
    private final ModuleManager moduleManager;
    private final BrowserServiceModuleConfig config;
    private final ErrorLogParserListenerManager errorLogListenerManager;

    private final HistogramMetrics errorLogHistogram;
    private final CounterMetrics logErrorCounter;

    public BrowserErrorLogReportBaseServletHandler(ModuleManager moduleManager,
                                                   BrowserServiceModuleConfig config,
                                                   ErrorLogParserListenerManager errorLogListenerManager) {
        this.moduleManager = moduleManager;
        this.config = config;
        this.errorLogListenerManager = errorLogListenerManager;

        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);

        errorLogHistogram = metricsCreator.createHistogramMetric(
            "browser_error_log_in_latency", "The process latency of browser error log", new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("grpc")
        );
        logErrorCounter = metricsCreator.createCounter(
            "browser_error_log_analysis_error_count", "The error number of browser error log analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
    }

    @Override
    protected void doPost(final HttpServletRequest req,
                          final HttpServletResponse resp) throws ServletException, IOException {
        if (log.isDebugEnabled()) {
            log.debug("receive browser error log");
        }

        HistogramMetrics.Timer timer = errorLogHistogram.createTimer();
        try {
            for (BrowserErrorLog browserErrorLog : parseBrowserErrorLog(req)) {
                ErrorLogAnalyzer analyzer = new ErrorLogAnalyzer(moduleManager, errorLogListenerManager, config);
                analyzer.doAnalysis(browserErrorLog);
            }
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            logErrorCounter.inc();
        } finally {
            timer.finish();
        }
    }

    protected abstract List<BrowserErrorLog> parseBrowserErrorLog(HttpServletRequest request) throws IOException;
}
