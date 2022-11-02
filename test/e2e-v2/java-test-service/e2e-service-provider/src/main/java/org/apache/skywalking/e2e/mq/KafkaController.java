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

package org.apache.skywalking.e2e.mq;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@ConditionalOnExpression("#{'true'.equals(environment['kafka_enable'])}")
public class KafkaController {

    private KafkaProducer<Object, Object> objectObjectKafkaProducer;

    @GetMapping(value = "kafka/send")
    public String sendMsg() throws ExecutionException, InterruptedException {

        String topic = Optional.ofNullable(System.getenv("kafka_topic")).orElse("topic");

        objectObjectKafkaProducer.send(
            new ProducerRecord<>(topic, 0, System.currentTimeMillis(), "a".getBytes(), "test".getBytes())
        ).get();
        return "ok";
    }

    @PostConstruct
    public void init() throws UnknownHostException {
        String server = Optional.ofNullable(System.getenv("kafka_server")).orElse("kafka:9092");
        Properties config = new Properties();
        config.put("client.id", InetAddress.getLocalHost().getHostName());
        config.put("bootstrap.servers", server);
        config.put("acks", "all");
        config.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        config.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        objectObjectKafkaProducer = new KafkaProducer<>(config);
    }
}
