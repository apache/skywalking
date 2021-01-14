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

package org.apache.skywalking.apm.agent.core.pulsar;

import java.util.HashMap;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.pulsar.PulsarReporterPluginConfig.Plugin.Pulsar;

/**
 * Configuring, initializing and holding a KafkaProducer instance for reporters.
 */
@DefaultImplementor
public class PulsarProducerManager implements BootService, Runnable {

    private static final ILog LOGGER = LogManager.getLogger(PulsarProducerManager.class);

    private HashMap<String, Producer<byte[]>> producerMap = new HashMap<>();

    @Override
    public void prepare() throws Throwable {
        PulsarClient client = PulsarClient.builder()
                .serviceUrl(Pulsar.SERVICE_URL)
                .build();
        LOGGER.info("Pulsar client is built");
        producerMap.put(Pulsar.TOPIC_MANAGEMENT, producerBuild(client, Pulsar.TOPIC_MANAGEMENT));
        producerMap.put(Pulsar.TOPIC_METER, producerBuild(client, Pulsar.TOPIC_METER));
        producerMap.put(Pulsar.TOPIC_METRICS, producerBuild(client, Pulsar.TOPIC_METRICS));
        producerMap.put(Pulsar.TOPIC_PROFILING, producerBuild(client, Pulsar.TOPIC_PROFILING));
        producerMap.put(Pulsar.TOPIC_SEGMENT, producerBuild(client, Pulsar.TOPIC_SEGMENT));
    }

    private Producer<byte[]> producerBuild(PulsarClient client, String topic) {
        String fullTopic = "persistent://" + Pulsar.TENANT + "/" + Pulsar.NAMESPACE + "/" + topic;
        LOGGER.info("Pulsar producer is building, full topic name: {}", fullTopic);
        Producer<byte[]> producer = null;
        try {
            producer = client.newProducer()
                    .topic(topic)
                    .loadConf(Pulsar.PRODUCER_CONFIG)
                    .create();
        } catch (PulsarClientException e) {
            e.printStackTrace();
        }
        return producer;
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
     * Get the Producer instance to send data to Pulsar broker.
     */
    public final Producer<byte[]> getProducer(String topic) {
        return producerMap.get(topic);
    }

    @Override
    public void shutdown() {
        producerMap.forEach((topic, producer) -> {
                    try {
                        producer.flush();
                        producer.close();
                        LOGGER.info("{}'s producer is closed", topic);
                    } catch (PulsarClientException e) {
                        e.printStackTrace();
                    }
                }
        );
    }
}
