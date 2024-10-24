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

import com.linecorp.armeria.server.annotation.Post;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfData;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.ErrorLogAnalyzer;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.ErrorLogParserListenerManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.PerfDataAnalyzer;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.PerfDataParserListenerManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * Collect and process the error log
 */
@Slf4j
public class BrowserPerfServiceHTTPHandler {
    private final ModuleManager moduleManager;
    private final BrowserServiceModuleConfig config;
    private final ErrorLogParserListenerManager errorLogListenerManager;
    private final PerfDataParserListenerManager perfDataListenerManager;

    private final HistogramMetrics perfHistogram;
    private final CounterMetrics perfErrorCounter;
    private final HistogramMetrics errorLogHistogram;
    private final CounterMetrics logErrorCounter;

    public BrowserPerfServiceHTTPHandler(ModuleManager moduleManager,
                                         BrowserServiceModuleConfig config,
                                         ErrorLogParserListenerManager errorLogListenerManager,
                                         PerfDataParserListenerManager perfDataListenerManager) {
        this.moduleManager = moduleManager;
        this.config = config;
        this.errorLogListenerManager = errorLogListenerManager;
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

        errorLogHistogram = metricsCreator.createHistogramMetric(
            "browser_error_log_in_latency", "The process latency of browser error log", new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("http")
        );
        logErrorCounter = metricsCreator.createCounter(
            "browser_error_log_analysis_error_count", "The error number of browser error log analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("http")
        );
    }

    @Post("/browser/errorLog")
    public Commands collectSingleErrorLog(final BrowserErrorLog browserErrorLog) {
        if (log.isDebugEnabled()) {
            log.debug("receive browser error log");
        }

        try (HistogramMetrics.Timer ignored = errorLogHistogram.createTimer()) {
            final ErrorLogAnalyzer analyzer = new ErrorLogAnalyzer(moduleManager, errorLogListenerManager, config);
            analyzer.doAnalysis(browserErrorLog);
            return Commands.newBuilder().build();
        } catch (Throwable e) {
            logErrorCounter.inc();
            throw e;
        }
    }

    @Post("/browser/errorLogs")
    public Commands collectErrorLogList(final List<BrowserErrorLog> logs) {
        try (HistogramMetrics.Timer ignored = errorLogHistogram.createTimer()) {
            logs.forEach(log -> {
                final ErrorLogAnalyzer analyzer = new ErrorLogAnalyzer(moduleManager, errorLogListenerManager, config);
                analyzer.doAnalysis(log);
            });

            return Commands.newBuilder().build();
        } catch (Throwable e) {
            logErrorCounter.inc();
            throw e;
        }
    }

    @Post("/browser/perfData")
    public Commands collectPerfData(final BrowserPerfData browserPerfData) {
        try (HistogramMetrics.Timer ignored = perfHistogram.createTimer()) {
            final PerfDataAnalyzer analyzer = new PerfDataAnalyzer(moduleManager, perfDataListenerManager, config);
            analyzer.doAnalysis(browserPerfData);
            return Commands.newBuilder().build();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            perfErrorCounter.inc();
            throw e;
        }
    }
}
