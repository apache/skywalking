/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.skywalking.e2e;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeoutException;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnExpression("#{'true'.equals(environment['kafka_enable'])}")
public class KafkaConsumer {

    @PostConstruct
    public void startConsumer() throws IOException, TimeoutException {
        String topic = Optional.ofNullable(System.getenv("kafka_topic")).orElse("topic");
        String server = Optional.ofNullable(System.getenv("kafka_server")).orElse("kafka:9092");

        Properties config = new Properties();
        config.put("client.id", InetAddress.getLocalHost().getHostName());
        config.put("bootstrap.servers", server);
        config.put("group.id", "a");
        config.put("key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        config.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        final org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(config);
        consumer.subscribe(Collections.singletonList(topic));
        new Thread(() -> {
            while (true) {
                try {
                    final ConsumerRecords<String, String> poll = consumer.poll(Duration.ofHours(2));
                    poll.forEach(e -> log.info("receive msg : {}", e));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
