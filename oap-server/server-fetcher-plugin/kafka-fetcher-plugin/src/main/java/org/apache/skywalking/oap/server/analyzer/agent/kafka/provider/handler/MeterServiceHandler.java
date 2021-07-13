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

package org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.language.agent.v3.MeterDataCollection;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.IMeterProcessService;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.MeterProcessor;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * A handler deserializes the message of meter system data and pushes it to downstream.
 */
@Slf4j
public class MeterServiceHandler extends AbstractKafkaHandler {
    private final IMeterProcessService processService;
    private final HistogramMetrics histogram;
    private final HistogramMetrics histogramBatch;
    private final CounterMetrics errorCounter;

    public MeterServiceHandler(ModuleManager manager, KafkaFetcherConfig config) {
        super(manager, config);
        this.processService = manager.find(AnalyzerModule.NAME).provider().getService(IMeterProcessService.class);
        MetricsCreator metricsCreator = manager.find(TelemetryModule.NAME)
                                               .provider()
                                               .getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric(
            "meter_in_latency",
            "The process latency of meter",
            new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("kafka")
        );
        histogramBatch = metricsCreator.createHistogramMetric(
            "meter_batch_in_latency",
            "The process latency of meter",
            new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("kafka")
        );
        errorCounter = metricsCreator.createCounter(
            "meter_analysis_error_count",
            "The error number of meter analysis",
            new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("kafka")
        );
    }

    @Override
    public void handle(final ConsumerRecord<String, Bytes> record) {
        try (HistogramMetrics.Timer timer = histogramBatch.createTimer()) {
            MeterDataCollection meterDataCollection = MeterDataCollection.parseFrom(record.value().get());
            MeterProcessor processor = processService.createProcessor();
            meterDataCollection.getMeterDataList().forEach(meterData -> {
                try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
                    processor.read(meterData);
                } catch (Exception e) {
                    errorCounter.inc();
                    log.error(e.getMessage(), e);
                }
            });
            processor.process();
        } catch (Exception e) {
            log.error("handle record failed", e);
        }
    }

    @Override
    protected String getPlainTopic() {
        return config.getTopicNameOfMeters();
    }
}
