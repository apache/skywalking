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

package org.apache.skywalking.oap.server.receiver.kafka;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.BytesDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.oap.server.receiver.kafka.module.KafkaReceiverConfig;
import org.apache.skywalking.oap.server.receiver.kafka.provider.handler.KafkaReceiveHandler;
import org.apache.skywalking.oap.server.receiver.mq.ReceiveHandlerRegister;
import org.apache.skywalking.oap.server.receiver.mq.handler.ReceiveHandler;

@Slf4j
public class KafkaReceiveHandlerRegister implements ReceiveHandlerRegister, Runnable {
    private ImmutableMap.Builder<String, ReceiveHandler> builder = ImmutableMap.builder();
    private ImmutableMap<String, ReceiveHandler> handlerMap;

    private List<TopicPartition> topicPartitions = Lists.newArrayList();
    private KafkaConsumer<String, Bytes> consumer = null;
    private final KafkaReceiverConfig config;
    private final boolean isSharding;

    public KafkaReceiveHandlerRegister(KafkaReceiverConfig config) {
        this.config = config;
        Properties properties = new Properties(config.getKafkaConsumerConfig());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, config.getGroupId());
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        consumer = new KafkaConsumer<>(properties, new StringDeserializer(), new BytesDeserializer());

        if (config.isSharding() && config.getServerId() > 0) {
            isSharding = true;
        } else {
            isSharding = false;
        }
    }

    public void prepare() {

    }

    public void register(KafkaReceiveHandler handler) {
        builder.put(handler.getTopic(), handler);
        topicPartitions.add(handler.getTopicPartition());
    }

    public void start() {
        handlerMap = builder.build();
        if (isSharding) {
            consumer.assign(topicPartitions);
        } else {
            consumer.subscribe(handlerMap.keySet());
        }
        consumer.seekToEnd(consumer.assignment());
        Executors.newSingleThreadExecutor(new DefaultThreadFactory("KafkaConsumer")).submit(this);
    }

    @Override
    public void run() {
        while (true) {
            ConsumerRecords<String, Bytes> consumerRecords = consumer.poll(Duration.ofMillis(500L));
            if (!consumerRecords.isEmpty()) {
                Iterator<ConsumerRecord<String, Bytes>> iterator = consumerRecords.iterator();
                while (iterator.hasNext()) { // by topic?
                    ConsumerRecord<String, Bytes> record = iterator.next();
                    handlerMap.get(record.topic()).handle(record);
                }
                consumer.commitAsync();
            }
        }
    }
}
