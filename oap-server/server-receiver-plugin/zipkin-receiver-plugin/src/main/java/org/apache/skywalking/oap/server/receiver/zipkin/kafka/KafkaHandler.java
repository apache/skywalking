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

package org.apache.skywalking.oap.server.receiver.zipkin.kafka;

import com.google.gson.Gson;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.server.pool.CustomThreadFactory;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import zipkin2.Span;
import zipkin2.SpanBytesDecoderDetector;
import zipkin2.codec.BytesDecoder;

@Slf4j
public class KafkaHandler {
    private final ZipkinReceiverConfig config;
    private final SpanForward spanForward;
    private final Properties properties;
    private final ThreadPoolExecutor executor;
    private final boolean enableKafkaMessageAutoCommit;
    private final List<String> topics;
    private final CounterMetrics msgDroppedIncr;
    private final CounterMetrics errorCounter;
    private final HistogramMetrics histogram;

    public KafkaHandler(final ZipkinReceiverConfig config, SpanForward forward, ModuleManager manager) {
        this.config = config;
        this.spanForward = forward;

        properties = new Properties();
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, config.getKafkaGroupId());
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        Gson gson = new Gson();
        Properties override = gson.fromJson(config.getKafkaConsumerConfig(), Properties.class);
        properties.putAll(override);

        int threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;
        if (config.getKafkaHandlerThreadPoolSize() > 0) {
            threadPoolSize = config.getKafkaHandlerThreadPoolSize();
        }
        int threadPoolQueueSize = 10000;
        if (config.getKafkaHandlerThreadPoolQueueSize() > 0) {
            threadPoolQueueSize = config.getKafkaHandlerThreadPoolQueueSize();
        }
        enableKafkaMessageAutoCommit = new ConsumerConfig(properties).getBoolean(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG);
        executor = new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 60, TimeUnit.SECONDS,
                                          new ArrayBlockingQueue<>(threadPoolQueueSize),
                                          new CustomThreadFactory("Zipkin-Kafka-Consumer"),
                                          new ThreadPoolExecutor.CallerRunsPolicy()
        );

        topics = Arrays.asList(config.getKafkaTopic().split(","));
        MetricsCreator metricsCreator = manager.find(TelemetryModule.NAME)
                                               .provider()
                                               .getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric(
            "trace_in_latency",
            "The process latency of trace data",
            new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("zipkin-kafka")
        );
        msgDroppedIncr = metricsCreator.createCounter(
            "trace_dropped_count", "The dropped number of traces",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("zipkin-kafka"));
        errorCounter = metricsCreator.createCounter(
            "trace_analysis_error_count", "The error number of trace analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("zipkin-kafka")
        );
    }

    public void start() throws ModuleStartException {
        for (int i = 0; i < config.getKafkaConsumers(); i++) {
            KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(properties);
            consumer.subscribe(topics);
            consumer.seekToEnd(consumer.assignment());
            executor.submit(() -> runTask(consumer));
        }
    }

    private void runTask(final KafkaConsumer<byte[], byte[]> consumer) {
        if (log.isDebugEnabled()) {
            log.debug("Start Consume zipkin trace records from kafka.");
        }
        while (true) {
            try {
                ConsumerRecords<byte[], byte[]> consumerRecords = consumer.poll(Duration.ofMillis(1000L));
                if (log.isDebugEnabled()) {
                    log.debug(
                        "Consume zipkin trace records from kafka, records count:[{}].",
                        consumerRecords.count()
                    );
                }
                if (!consumerRecords.isEmpty()) {
                    for (final ConsumerRecord<byte[], byte[]> record : consumerRecords) {
                        final byte[] bytes = record.value();
                        //empty or illegal message
                        if (bytes.length < 2) {
                            msgDroppedIncr.inc();
                            continue;
                        }
                        executor.submit(() -> handleRecord(bytes));
                    }
                    if (!enableKafkaMessageAutoCommit) {
                        consumer.commitAsync();
                    }
                }
            } catch (Exception e) {
                log.error("Kafka handle message error.", e);
                errorCounter.inc();
            }
        }
    }

    private void handleRecord(byte[] bytes) {
        try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
            BytesDecoder<Span> decoder = SpanBytesDecoderDetector.decoderForListMessage(bytes);
            final List<Span> spanList = decoder.decodeList(bytes);
            spanForward.send(spanList);
        }
    }
}
