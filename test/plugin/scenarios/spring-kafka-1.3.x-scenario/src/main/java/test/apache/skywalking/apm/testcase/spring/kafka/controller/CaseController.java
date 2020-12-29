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

package test.apache.skywalking.apm.testcase.spring.kafka.controller;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.config.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.kafka.listener.AbstractMessageListenerContainer.AckMode;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Controller
@RequestMapping("/case")
@PropertySource("classpath:application.properties")
public class CaseController {

    private static final String SUCCESS = "Success";

    @Value("${bootstrap.servers:127.0.0.1:9092}")
    private String bootstrapServers;
    private String topicName;
    private KafkaTemplate<String, String> kafkaTemplate;

    private CountDownLatch latch = new CountDownLatch(1);
    private String helloWorld = "helloWorld";

    @PostConstruct
    private void setUp() {
        topicName = "spring_test";
        setUpProvider();
        setUpConsumer();
    }

    private void setUpProvider() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<String, String>(new DefaultKafkaProducerFactory<>(props));
        try {
            kafkaTemplate.send(topicName, "key", "ping").get();
            kafkaTemplate.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setUpConsumer() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, "grop:" + topicName);
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        Deserializer<String> stringDeserializer = new StringDeserializer();
        DefaultKafkaConsumerFactory<String, String> factory = new DefaultKafkaConsumerFactory(configs, stringDeserializer, stringDeserializer);
        ContainerProperties props = new ContainerProperties(topicName);
        props.setMessageListener(new AcknowledgingMessageListener<String, String>() {
            @Override
            public void onMessage(ConsumerRecord<String, String> data, Acknowledgment acknowledgment) {
                if (data.value().equals(helloWorld)) {
                    OkHttpClient client = new OkHttpClient.Builder().build();
                    Request request = new Request.Builder().url("http://localhost:8080/spring-kafka-1.3.x-scenario/case/spring-kafka-consumer-ping").build();
                    Response response = null;
                    try {
                        response = client.newCall(request).execute();
                    } catch (IOException e) {
                    }
                    response.body().close();
                    acknowledgment.acknowledge();
                    latch.countDown();
                }
            }
        });
        KafkaMessageListenerContainer<String, String> container = new KafkaMessageListenerContainer<>(factory, props);
        container.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        container.start();
    }

    @RequestMapping("/spring-kafka-case")
    @ResponseBody
    public String springKafkaCase() throws Exception {
        kafkaTemplate.send(topicName, "key", helloWorld).get();
        latch.await();
        kafkaTemplate.flush();
        return SUCCESS;
    }

    @RequestMapping("/spring-kafka-consumer-ping")
    @ResponseBody
    public String springKafkaConsumerPing() {
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return SUCCESS;
    }
}

