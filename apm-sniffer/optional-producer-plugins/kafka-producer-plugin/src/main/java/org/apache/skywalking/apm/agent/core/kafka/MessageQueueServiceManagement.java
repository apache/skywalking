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

import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

@DefaultImplementor
public class MessageQueueServiceManagement implements BootService, Runnable {
    private static final ILog logger = LogManager.getLogger(MessageQueueServiceManagement.class);
    private KafkaProducer<String, Bytes> producer;

    @Override
    public void prepare() {
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.Collector.Kafka.BOOTSTRAP_SERVERS);
        properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, String.valueOf(Config.Collector.Kafka.BATCH_SIZE));
        properties.setProperty(ProducerConfig.ACKS_CONFIG, Config.Collector.Kafka.ACKS);

        Config.Collector.Kafka.CONSUMER_CONFIG.forEach((k, v) -> properties.setProperty(k, v));

        if (!properties.contains("max.block.ms")) {
            properties.setProperty("properties", "1000");
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

    public final KafkaProducer<String, Bytes> getProducer(String topic) {
        // Reservation. Maybe we need to create a separate producer for each topic in the future
        return producer;
    }

    @Override
    public void shutdown() {
        producer.flush();
        producer.close();
    }
}
