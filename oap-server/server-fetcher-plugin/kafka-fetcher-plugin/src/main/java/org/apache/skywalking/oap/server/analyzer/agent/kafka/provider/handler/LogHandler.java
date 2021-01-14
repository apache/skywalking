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

package org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class LogHandler implements KafkaHandler {

    private final KafkaFetcherConfig config;
    private final HistogramMetrics histogram;
    private final CounterMetrics errorCounter;
    private final ILogAnalyzerService logAnalyzerService;

    public LogHandler(final ModuleManager moduleManager,
                      final KafkaFetcherConfig config) {
        this.config = config;
        this.logAnalyzerService = moduleManager.find(LogAnalyzerModule.NAME)
                                               .provider()
                                               .getService(ILogAnalyzerService.class);

        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric(
            "log_in_latency", "The process latency of log",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("kafka-fetcher")
        );
        errorCounter = metricsCreator.createCounter("log_analysis_error_count", "The error number of log analysis",
                                                    new MetricsTag.Keys("protocol"),
                                                    new MetricsTag.Values("kafka-fetcher")
        );
    }

    @Override
    public String getConsumePartitions() {
        return config.getConsumePartitions();
    }

    @Override
    public String getTopic() {
        return config.getTopicNameOfLogs();
    }

    @Override
    public void handle(final ConsumerRecord<String, Bytes> record) {
        HistogramMetrics.Timer timer = histogram.createTimer();
        try {
            LogData logData = LogData.parseFrom(record.value().get());
            logAnalyzerService.doAnalysis(logData);
        } catch (Exception e) {
            errorCounter.inc();
            log.error(e.getMessage(), e);
        } finally {
            timer.finish();
        }
    }
}
