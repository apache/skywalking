/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.controller;

import java.util.Collections;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.nonNull;

@Slf4j
@RestController
public class LogController {

    private static final String TOPIC = "skywalking-logs";

    private KafkaProducer<String, Bytes> producer;

    @Value("#{systemProperties['bootstrap.service'] ?: 'localhost:9092'}")
    private String bootstrapService;

    @PostConstruct
    public void up() {
        Properties properties = new Properties();
        properties.setProperty(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapService);
        producer = new KafkaProducer<>(properties, new StringSerializer(), new BytesSerializer());
        AdminClient adminClient = AdminClient.create(properties);
        DescribeTopicsResult topicsResult = adminClient.describeTopics(Collections.singletonList(TOPIC));
        Set<String> topics = topicsResult.values().entrySet().stream()
                                         .map(entry -> {
                                             try {
                                                 entry.getValue().get(10, TimeUnit.SECONDS);
                                                 return null;
                                             } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                                 LOGGER.error("Get KAFKA topic:" + entry.getKey() + " error", e);
                                             }
                                             return entry.getKey();
                                         }).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!topics.isEmpty()) {
            throw new RuntimeException("These topics" + topics + " don't exist.");
        }
    }

    @PreDestroy
    public void down() {
        if (nonNull(producer)) {
            producer.close();
        }
    }

    @PostMapping("/sendLog")
    @SuppressWarnings("EmptyMethod")
    @ResponseStatus(code = HttpStatus.OK)
    public void sendLog() {
        try {
            LogData logData = LogData.newBuilder()
                                     .setService("e2e")
                                     .setServiceInstance("e2e-instance")
                                     .setEndpoint("/traffic")
                                     .setBody(
                                         LogDataBody.newBuilder()
                                                    .setText(
                                                        TextLog.newBuilder().setText("[main] INFO log message").build())
                                                    .build())
                                     .setTags(LogTags.newBuilder()
                                                     .addData(KeyStringValuePair.newBuilder()
                                                                                .setKey("status_code")
                                                                                .setValue("200")
                                                                                .build())
                                                     .build())
                                     .setTraceContext(TraceContext.newBuilder()
                                                                  .setTraceId("ac81b308-0d66-4c69-a7af-a023a536bd3e")
                                                                  .setTraceSegmentId(
                                                                      "6024a2b1fcff48e4a641d69d388bac53.41.16088574455279608")
                                                                  .setSpanId(0)
                                                                  .build())
                                     .build();

            producer.send(
                new ProducerRecord<>(TOPIC, logData.getService(), Bytes.wrap(logData.toByteArray())),
                (m, e) -> {
                    if (nonNull(e)) {
                        LOGGER.error("Failed to report logs.", e);
                    }
                }
            );
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
