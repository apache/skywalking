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

package test.apache.skywalking.apm.testcase.kafka.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.internals.NoOpConsumerRebalanceListener;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.toolkit.kafka.KafkaPollAndInvoke;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import static java.util.Objects.isNull;

@Controller
@RequestMapping("/case")
@PropertySource("classpath:application.properties")
public class CaseController {

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    private static final String SUCCESS = "Success";

    @Value("${bootstrap.servers:127.0.0.1:9092}")
    private String bootstrapServers;

    private String topicName;
    private String topicName2;
    private Pattern topicPattern;

    private static volatile boolean KAFKA_STATUS = false;

    @PostConstruct
    private void setUp() {
        topicName = "test";
        topicName2 = "test2";
        topicPattern = Pattern.compile("test.");
        new CheckKafkaProducerThread(bootstrapServers).start();
    }

    @RequestMapping("/kafka-case")
    @ResponseBody
    public String kafkaCase() {
        wrapProducer(producer -> {
            ProducerRecord<String, String> record = new ProducerRecord<String, String>(topicName, "testKey", Integer.toString(1));
            record.headers().add("TEST", "TEST".getBytes());
            producer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    LOGGER.info("send success metadata={}", metadata);
                }
            });

            ProducerRecord<String, String> record2 = new ProducerRecord<String, String>(topicName2, "testKey", Integer.toString(1));
            record2.headers().add("TEST", "TEST".getBytes());
            Callback callback2 = (metadata, exception) -> {
                LOGGER.info("send success metadata={}", metadata);
            };
            producer.send(record2, callback2);
        }, bootstrapServers);

        Thread thread = new ConsumerThread();
        thread.start();

        Thread thread2 = new ConsumerThread2();
        thread2.start();
        try {
            thread.join();
            thread2.join();
        } catch (InterruptedException e) {
            // ignore
        }
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        if (KAFKA_STATUS) {
            return SUCCESS;
        }
        throw new RuntimeException("kafka not ready");
    }

    @RequestMapping("/kafka-thread2-ping")
    @ResponseBody
    public String kafkaThread2Ping() {
        return SUCCESS;
    }

    private static void wrapProducer(Consumer<Producer<String, String>> consFunc, String bootstrapServers) {
        Properties producerProperties = new Properties();
        producerProperties.put("bootstrap.servers", bootstrapServers);
        producerProperties.put("acks", "all");
        producerProperties.put("retries", 0);
        producerProperties.put("batch.size", 16384);
        producerProperties.put("linger.ms", 1);
        producerProperties.put("buffer.memory", 33554432);
        producerProperties.put("auto.create.topics.enable", "true");
        producerProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProperties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        Producer<String, String> producer = new KafkaProducer<>(producerProperties);
        try {
            consFunc.accept(producer);
        } finally {
            producer.close();
        }
    }

    public static class CheckKafkaProducerThread extends Thread {

        private final String bootstrapServers;

        public CheckKafkaProducerThread(String bootstrapServers) {
            setDaemon(true);
            this.bootstrapServers = bootstrapServers;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    if (KAFKA_STATUS) {
                        return;
                    }
                    wrapProducer(producer -> {
                        ProducerRecord<String, String> record = new ProducerRecord<String, String>("check", "checkKey", Integer
                            .toString(1));
                        record.headers().add("CHECK", "CHECK".getBytes());
                        Callback callback = (metadata, e) -> {
                            if (isNull(e)) {
                                KAFKA_STATUS = true;
                            }
                        };
                        producer.send(record, callback);
                    }, bootstrapServers);
                } catch (Exception e) {
                    LOGGER.error("check " + bootstrapServers + " " + e.getMessage(), e);
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    public class ConsumerThread extends Thread {
        @Override
        public void run() {
            Properties consumerProperties = new Properties();
            consumerProperties.put("bootstrap.servers", bootstrapServers);
            consumerProperties.put("group.id", "testGroup");
            consumerProperties.put("enable.auto.commit", "true");
            consumerProperties.put("auto.commit.interval.ms", "1000");
            consumerProperties.put("auto.offset.reset", "earliest");
            consumerProperties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            consumerProperties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties);
            consumer.subscribe(Arrays.asList(topicName));
            int i = 0;
            while (i++ <= 10) {
                try {
                    Thread.sleep(1 * 1000);
                } catch (InterruptedException e) {
                }

                ConsumerRecords<String, String> records = consumer.poll(100);

                if (!records.isEmpty()) {
                    for (ConsumerRecord<String, String> record : records) {
                        LOGGER.info("header: {}", new String(record.headers()
                                                                   .headers("TEST")
                                                                   .iterator()
                                                                   .next()
                                                                   .value()));
                        LOGGER.info("offset = {}, key = {}, value = {}", record.offset(), record.key(), record.value());
                    }
                    break;
                }
            }

            consumer.close();
        }
    }

    public class ConsumerThread2 extends Thread {
        @Override
        public void run() {
            Properties consumerProperties = new Properties();
            consumerProperties.put("bootstrap.servers", bootstrapServers);
            consumerProperties.put("group.id", "testGroup2");
            consumerProperties.put("enable.auto.commit", "true");
            consumerProperties.put("auto.commit.interval.ms", "1000");
            consumerProperties.put("auto.offset.reset", "earliest");
            consumerProperties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            consumerProperties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties);
            consumer.subscribe(topicPattern, new NoOpConsumerRebalanceListener());
            while (true) {
                if (pollAndInvoke(consumer)) break;
            }
            consumer.close();
        }

        @KafkaPollAndInvoke
        private boolean pollAndInvoke(KafkaConsumer<String, String> consumer) {
            try {
                Thread.sleep(1 * 1000);
            } catch (InterruptedException e) {
            }

            ConsumerRecords<String, String> records = consumer.poll(100);

            if (!records.isEmpty()) {
                OkHttpClient client = new OkHttpClient.Builder().build();
                Request request = new Request.Builder().url("http://localhost:8080/kafka-scenario/case/kafka-thread2-ping").build();
                Response response = null;
                try {
                    response = client.newCall(request).execute();
                } catch (IOException e) {
                }
                response.body().close();
                return true;
            }
            return false;
        }
    }
}

