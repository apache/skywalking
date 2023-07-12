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
import org.apache.skywalking.apm.network.language.agent.v3.CLRMetricCollection;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.analyzer.provider.clr.CLRSourceDispatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * A handler deserializes the message of CLR Metrics and pushes it to downstream.
 */
@Slf4j
public class CLRMetricsHandler extends AbstractKafkaHandler {

    private final NamingControl namingLengthControl;
    private final CLRSourceDispatcher clrSourceDispatcher;

    public CLRMetricsHandler(ModuleManager manager, KafkaFetcherConfig config) {
        super(manager, config);
        this.clrSourceDispatcher = new CLRSourceDispatcher(manager);
        this.namingLengthControl = manager.find(CoreModule.NAME)
                                          .provider()
                                          .getService(NamingControl.class);
    }

    @Override
    public void handle(final ConsumerRecord<String, Bytes> record) {
        try {
            CLRMetricCollection metrics = CLRMetricCollection.parseFrom(record.value().get());

            if (log.isDebugEnabled()) {
                log.debug(
                    "Fetched CLR metrics from service[{}] instance[{}] reported.",
                    metrics.getService(),
                    metrics.getServiceInstance()
                );
            }
            CLRMetricCollection.Builder builder = metrics.toBuilder();
            builder.setService(namingLengthControl.formatServiceName(builder.getService()));
            builder.setServiceInstance(namingLengthControl.formatInstanceName(builder.getServiceInstance()));

            builder.getMetricsList().forEach(clrMetric -> {
                try {
                    clrSourceDispatcher.sendMetric(
                        builder.getService(),
                        builder.getServiceInstance(),
                        TimeBucket.getMinuteTimeBucket(clrMetric.getTime()),
                        clrMetric);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            log.error("handle record failed", e);
        }
    }

    @Override
    protected String getPlainTopic() {
        return config.getTopicNameOfCLRMetrics();
    }
}
