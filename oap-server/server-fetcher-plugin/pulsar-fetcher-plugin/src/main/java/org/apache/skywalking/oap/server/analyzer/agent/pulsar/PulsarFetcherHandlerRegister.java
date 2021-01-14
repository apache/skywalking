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

package org.apache.skywalking.oap.server.analyzer.agent.pulsar;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.module.PulsarFetcherConfig;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.provider.handler.PulsarHandler;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.server.pool.CustomThreadFactory;

/**
 * Configuring and initializing a PulsarConsumer client as a dispatcher to delivery Pulsar Message to registered handler
 * by topic.
 */
@Slf4j
public class PulsarFetcherHandlerRegister implements Runnable {

    private ImmutableMap.Builder<String, PulsarHandler> builder = ImmutableMap.builder();
    private ImmutableMap<String, PulsarHandler> handlerMap;

    private Consumer consumer = null;
    private final PulsarFetcherConfig config;
    private List<String> topicList = new ArrayList<>();

    private PulsarClient client;

    private int threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;
    private int threadPoolQueueSize = 10000;
    private final ThreadPoolExecutor executor;

    public PulsarFetcherHandlerRegister(PulsarFetcherConfig config) throws ModuleStartException, PulsarClientException {
        this.config = config;

        if (config.getPulsarHandlerThreadPoolSize() > 0) {
            threadPoolSize = config.getPulsarHandlerThreadPoolSize();
        }
        if (config.getPulsarHandlerThreadPoolQueueSize() > 0) {
            threadPoolQueueSize = config.getPulsarHandlerThreadPoolQueueSize();
        }

        log.info("PulsarConsumerConfig: {}", config.getPulsarConsumerConfig());
        client = PulsarClient.builder()
                .serviceUrl(config.getServiceUrl())
                .loadConf(new HashMap<String, Object>((Map) config.getPulsarConsumerConfig()))
                .build();

        executor = new ThreadPoolExecutor(threadPoolSize, threadPoolSize,
                60, TimeUnit.SECONDS,
                new ArrayBlockingQueue(threadPoolQueueSize),
                new CustomThreadFactory("PulsarConsumer"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public void register(PulsarHandler handler) {
        topicList.add(handler.getTopic());
        builder.put(handler.getTopic(), handler);
    }

    public void start() throws PulsarClientException {
        handlerMap = builder.build();
        consumer = client.newConsumer()
                .topics(topicList)
                .subscriptionName(config.getSubscriptionName())
                .subscribe();

        executor.submit(this);
    }

    @Override
    public void run() {
        while (true) {
            try {
                Message msg = consumer.receive();
                executor.submit(() -> handlerMap.get(msg.getTopicName()).handle(msg));
            } catch (Exception e) {
                log.error("Pulsar handle message error.", e);
            }
        }
    }
}
