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
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.core.trace.TraceAnalysisError;
import org.apache.skywalking.oap.server.core.trace.TraceInLatency;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.ISegmentParserService;

/**
 * A handler deserializes the message of the trace segment data and pushes it to downstream.
 */
@Slf4j
public class TraceSegmentHandler extends AbstractKafkaHandler {

    private final ISegmentParserService segmentParserService;

    private final HistogramMetrics histogram;
    private final CounterMetrics errorCounter;

    public TraceSegmentHandler(ModuleManager moduleManager, KafkaFetcherConfig config) {
        super(moduleManager, config);
        this.segmentParserService = moduleManager.find(TraceAnalyzerModule.NAME)
                                                 .provider()
                                                 .getService(ISegmentParserService.class);

        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider().getService(MetricsCreator.class);

        histogram = metricsCreator.createHistogramMetric(
            TraceInLatency.NAME,
            TraceInLatency.HELP,
            new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("kafka")
        );
        errorCounter = metricsCreator.createCounter(
            TraceAnalysisError.NAME,
            TraceAnalysisError.HELP,
            new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("kafka")
        );
    }

    @Override
    public void handle(final ConsumerRecord<String, Bytes> record) {
        try {
            final SegmentObject segment = SegmentObject.parseFrom(record.value().get());
            if (log.isDebugEnabled()) {
                log.debug(
                    "Fetched a tracing segment[{}] from service instance[{}].",
                    segment.getTraceSegmentId(),
                    segment.getServiceInstance()
                );
            }
            final long startNanos = System.nanoTime();
            try {
                segmentParserService.send(segment);
            } finally {
                TraceInLatency.observeSpans(histogram, startNanos, segment.getSpansCount());
            }
        } catch (Exception e) {
            // Counted here: the record runs on an executor whose Future nobody reads, so an escaping exception
            // would be lost without a log.
            errorCounter.inc();
            log.error("handle record failed", e);
        }
    }

    @Override
    protected String getPlainTopic() {
        return config.getTopicNameOfTracingSegments();
    }
}
