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

package org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.language.agent.v3.JVMMetricCollection;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.analyzer.provider.jvm.JVMSourceDispatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * A handler deserializes the message of JVM Metrics and pushes it to downstream.
 */
@Slf4j
public class JVMMetricsHandler implements KafkaHandler {

    private final NamingControl namingLengthControl;
    private final JVMSourceDispatcher jvmSourceDispatcher;

    private final KafkaFetcherConfig config;

    public JVMMetricsHandler(ModuleManager moduleManager, KafkaFetcherConfig config) {
        this.jvmSourceDispatcher = new JVMSourceDispatcher(moduleManager);
        this.namingLengthControl = moduleManager.find(CoreModule.NAME)
                                                .provider()
                                                .getService(NamingControl.class);
        this.config = config;
    }

    @Override
    public void handle(final ConsumerRecord<String, Bytes> record) {
        try {
            JVMMetricCollection metrics = JVMMetricCollection.parseFrom(record.value().get());

            if (log.isDebugEnabled()) {
                log.debug(
                    "Fetched JVM metrics from service[{}] instance[{}] reported.",
                    metrics.getService(),
                    metrics.getServiceInstance()
                );
            }
            JVMMetricCollection.Builder builder = metrics.toBuilder();
            builder.setService(namingLengthControl.formatServiceName(builder.getService()));
            builder.setServiceInstance(namingLengthControl.formatInstanceName(builder.getServiceInstance()));

            builder.getMetricsList().forEach(jvmMetric -> {
                jvmSourceDispatcher.sendMetric(builder.getService(), builder.getServiceInstance(), jvmMetric);
            });
        } catch (Exception e) {
            log.error("handle record failed", e);
        }
    }

    @Override
    public String getTopic() {
        return config.getMm2SourceAlias() + config.getMm2SourceSeparator() + config.getTopicNameOfMetrics();
    }

    @Override
    public String getConsumePartitions() {
        return config.getConsumePartitions();
    }
}
