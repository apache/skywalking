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

package org.apache.skywalking.oap.server.receiver.browser.provider.handler.grpc;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfData;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfServiceGrpc;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
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
 * Collect and process the performance data and error log.
 */
@Slf4j
public class BrowserPerfServiceHandler extends BrowserPerfServiceGrpc.BrowserPerfServiceImplBase implements GRPCHandler {
    private final ModuleManager moduleManager;
    private final BrowserServiceModuleConfig config;
    private final PerfDataParserListenerManager perfDataListenerManager;
    private final ErrorLogParserListenerManager errorLogListenerManager;

    // performance
    private final HistogramMetrics perfHistogram;
    private final CounterMetrics perfErrorCounter;

    // error log
    private final HistogramMetrics errorLogHistogram;
    private final CounterMetrics logErrorCounter;

    public BrowserPerfServiceHandler(ModuleManager moduleManager,
                                     BrowserServiceModuleConfig config,
                                     PerfDataParserListenerManager perfDataListenerManager,
                                     ErrorLogParserListenerManager errorLogListenerManager) {
        this.moduleManager = moduleManager;
        this.config = config;
        this.perfDataListenerManager = perfDataListenerManager;
        this.errorLogListenerManager = errorLogListenerManager;

        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);

        // performance
        perfHistogram = metricsCreator.createHistogramMetric(
            "browser_perf_data_in_latency", "The process latency of browser performance data",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("grpc")
        );
        perfErrorCounter = metricsCreator.createCounter(
            "browser_perf_data_analysis_error_count", "The error number of browser performance data analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("grpc")
        );

        // error log
        errorLogHistogram = metricsCreator.createHistogramMetric(
            "browser_error_log_in_latency", "The process latency of browser error log", new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("grpc")
        );
        logErrorCounter = metricsCreator.createCounter(
            "browser_error_log_analysis_error_count", "The error number of browser error log analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("grpc")
        );
    }

    @Override
    public void collectPerfData(final BrowserPerfData request, final StreamObserver<Commands> responseObserver) {
        if (log.isDebugEnabled()) {
            log.debug("receive browser performance data");
        }
        HistogramMetrics.Timer timer = perfHistogram.createTimer();
        try {
            PerfDataAnalyzer analyzer = new PerfDataAnalyzer(moduleManager, perfDataListenerManager, config);
            analyzer.doAnalysis(request);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            perfErrorCounter.inc();
        } finally {
            timer.finish();
            responseObserver.onNext(Commands.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public StreamObserver<BrowserErrorLog> collectErrorLogs(final StreamObserver<Commands> responseObserver) {
        return new StreamObserver<BrowserErrorLog>() {
            @Override
            public void onNext(final BrowserErrorLog browserErrorLog) {
                if (log.isDebugEnabled()) {
                    log.debug("receive browser error log");
                }

                HistogramMetrics.Timer timer = errorLogHistogram.createTimer();
                try {
                    ErrorLogAnalyzer analyzer = new ErrorLogAnalyzer(moduleManager, errorLogListenerManager, config);
                    analyzer.doAnalysis(browserErrorLog);
                } catch (Throwable e) {
                    log.error(e.getMessage(), e);
                    logErrorCounter.inc();
                } finally {
                    timer.finish();
                }
            }

            @Override
            public void onError(final Throwable throwable) {
                log.error(throwable.getMessage(), throwable);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
