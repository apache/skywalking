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

package org.apache.skywalking.apm.agent.core.kafka;

import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

/**
 * Configuring, initializing and holding a KafkaProducer instance for reporters.
 */
@DefaultImplementor
public class KafkaProducerManager implements BootService, Runnable {

    private static final ILog LOGGER = LogManager.getLogger(KafkaProducerManager.class);

    private KafkaProducer<String, Bytes> producer;

    @Override
    public void prepare() throws Throwable {
        Properties properties = new Properties();
        properties.setProperty(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaReporterPluginConfig.Plugin.Kafka.BOOTSTRAP_SERVERS);
        KafkaReporterPluginConfig.Plugin.Kafka.PRODUCER_CONFIG.forEach((k, v) -> properties.setProperty(k, v));

        AdminClient adminClient = AdminClient.create(properties);
        DescribeTopicsResult topicsResult = adminClient.describeTopics(Arrays.asList(
            KafkaReporterPluginConfig.Plugin.Kafka.TOPIC_MANAGEMENT,
            KafkaReporterPluginConfig.Plugin.Kafka.TOPIC_METRICS,
            KafkaReporterPluginConfig.Plugin.Kafka.TOPIC_PROFILING,
            KafkaReporterPluginConfig.Plugin.Kafka.TOPIC_SEGMENT,
            KafkaReporterPluginConfig.Plugin.Kafka.TOPIC_METER
        ));
        Set<String> topics = topicsResult.values().entrySet().stream()
                                         .map(entry -> {
                                             try {
                                                 entry.getValue().get(
                                                     KafkaReporterPluginConfig.Plugin.Kafka.GET_TOPIC_TIMEOUT,
                                                     TimeUnit.SECONDS
                                                 );
                                                 return null;
                                             } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                                 LOGGER.error(e, "Get KAFKA topic:{} error.", entry.getKey());
                                             }
                                             return entry.getKey();
                                         })
                                         .filter(Objects::nonNull)
                                         .collect(Collectors.toSet());
        if (!topics.isEmpty()) {
            throw new Exception("These topics" + topics + " don't exist.");
        }

        producer = new KafkaProducer<>(properties, new StringSerializer(), new BytesSerializer());
    }

    @Override
    public void boot() {

    }

    @Override
    public void onComplete() {
    }

    @Override
    public void run() {

    }

    /**
     * Get the KafkaProducer instance to send data to Kafka broker.
     */
    public final KafkaProducer<String, Bytes> getProducer() {
        return producer;
    }

    @Override
    public void shutdown() {
        producer.flush();
        producer.close();
    }
}
