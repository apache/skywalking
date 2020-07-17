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
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.receiver.trace.TraceServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.trace.parser.SegmentParserListenerManager;
import org.apache.skywalking.oap.server.receiver.trace.parser.TraceAnalyzer;
import org.apache.skywalking.oap.server.receiver.trace.parser.listener.MultiScopesAnalysisListener;
import org.apache.skywalking.oap.server.receiver.trace.parser.listener.NetworkAddressAliasMappingListener;
import org.apache.skywalking.oap.server.receiver.trace.parser.listener.SegmentAnalysisListener;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class TraceSegmentHandler implements KafkaHandler {

    private final ModuleManager moduleManager;
    private final KafkaFetcherConfig config;

    private HistogramMetrics histogram;
    private CounterMetrics errorCounter;
    private final TraceServiceModuleConfig traceModuleConfig;
    private final SegmentParserListenerManager listenerManager;

    public TraceSegmentHandler(ModuleManager moduleManager,
                               KafkaFetcherConfig config) {
        this.config = config;
        this.moduleManager = moduleManager;
        this.traceModuleConfig = (TraceServiceModuleConfig) ((ModuleProvider) moduleManager
            .find("receiver-trace").provider()).createConfigBeanIfAbsent();

        this.listenerManager = listenerManager(traceModuleConfig, moduleManager);
        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider().getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric("trace_in_latency",
                                                         "The process latency of trace data",
                                                         new MetricsTag.Keys("protocol"),
                                                         new MetricsTag.Values("kafka-consumer")
        );
        errorCounter = metricsCreator.createCounter("trace_analysis_error_count",
                                                    "The error number of trace analysis",
                                                    new MetricsTag.Keys("protocol"),
                                                    new MetricsTag.Values("kafka-consumer")
        );
    }

    @Override
    public void handle(final ConsumerRecord<String, Bytes> record) {
        try {
            SegmentObject segment = SegmentObject.parseFrom(record.value().get());
            if (log.isDebugEnabled()) {
                log.debug("receive segment");
            }

            HistogramMetrics.Timer timer = histogram.createTimer();
            try {
                TraceAnalyzer traceAnalyzer = new TraceAnalyzer(moduleManager, listenerManager, traceModuleConfig);
                traceAnalyzer.doAnalysis(segment);
            } catch (Exception e) {
                errorCounter.inc();
            } finally {
                timer.finish();
            }
        } catch (InvalidProtocolBufferException e) {
            log.error(e.getMessage(), e);
        }
    }

    private SegmentParserListenerManager listenerManager(
        TraceServiceModuleConfig traceModuleConfig, ModuleManager moduleManager) {
        SegmentParserListenerManager listenerManager = new SegmentParserListenerManager();
        if (traceModuleConfig.isTraceAnalysis()) {
            listenerManager.add(new MultiScopesAnalysisListener.Factory(moduleManager));
            listenerManager
                .add(new NetworkAddressAliasMappingListener.Factory(moduleManager));
        }
        listenerManager.add(new SegmentAnalysisListener.Factory(
            moduleManager,
            traceModuleConfig
        ));
        return listenerManager;
    }

    @Override
    public String getTopic() {
        return config.getTopicNameOfTracingSegments();
    }

    @Override
    public TopicPartition getTopicPartition() {
        return new TopicPartition(getTopic(), config.getServerId());
    }
}
