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

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.ISegmentParserService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

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
        this.segmentParserService = moduleManager.find(AnalyzerModule.NAME)
                                                 .provider()
                                                 .getService(ISegmentParserService.class);

        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider().getService(MetricsCreator.class);

        histogram = metricsCreator.createHistogramMetric(
            "trace_in_latency",
            "The process latency of trace data",
            new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("kafka")
        );
        errorCounter = metricsCreator.createCounter(
            "trace_analysis_error_count",
            "The error number of trace analysis",
            new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("kafka")
        );
    }

    @Override
    public void handle(final ConsumerRecord<String, Bytes> record) {
        try (HistogramMetrics.Timer ignore = histogram.createTimer()) {
            SegmentObject segment = SegmentObject.parseFrom(record.value().get());
            if (log.isDebugEnabled()) {
                log.debug(
                    "Fetched a tracing segment[{}] from service instance[{}].",
                    segment.getTraceSegmentId(),
                    segment.getServiceInstance()
                );
            }
            segmentParserService.send(segment);
        } catch (InvalidProtocolBufferException e) {
            errorCounter.inc();
            log.error("handle record failed", e);
        }
    }

    @Override
    protected String getPlainTopic() {
        return config.getTopicNameOfTracingSegments();
    }
}
