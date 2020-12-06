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

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.management.v3.InstancePingPkg;
import org.apache.skywalking.apm.network.management.v3.InstanceProperties;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceUpdate;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * A handler deserializes the message of Service Management and pushes it to downstream.
 */
@Slf4j
public class ServiceManagementHandler implements KafkaHandler {

    private final SourceReceiver sourceReceiver;
    private final NamingControl namingLengthControl;

    private final KafkaFetcherConfig config;

    public ServiceManagementHandler(ModuleManager moduleManager, KafkaFetcherConfig config) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.namingLengthControl = moduleManager.find(CoreModule.NAME)
                                                .provider()
                                                .getService(NamingControl.class);
        this.config = config;
    }

    @Override
    public void handle(final ConsumerRecord<String, Bytes> record) {
        try {
            if (record.key().startsWith("register-")) {
                serviceReportProperties(InstanceProperties.parseFrom(record.value().get()));
            } else {
                keepAlive(InstancePingPkg.parseFrom(record.value().get()));
            }
        } catch (Exception e) {
            log.error("handle record failed", e);
        }
    }

    private final void serviceReportProperties(InstanceProperties request) {
        ServiceInstanceUpdate serviceInstanceUpdate = new ServiceInstanceUpdate();
        final String serviceName = namingLengthControl.formatServiceName(request.getService());
        final String instanceName = namingLengthControl.formatInstanceName(request.getServiceInstance());
        serviceInstanceUpdate.setServiceId(IDManager.ServiceID.buildId(serviceName, NodeType.Normal));
        serviceInstanceUpdate.setName(instanceName);

        if (log.isDebugEnabled()) {
            log.debug(
                "Service[{}] instance[{}] registered.",
                serviceName,
                instanceName
            );
        }

        JsonObject properties = new JsonObject();
        List<String> ipv4List = new ArrayList<>();
        request.getPropertiesList().forEach(prop -> {
            if (InstanceTraffic.PropertyUtil.IPV4.equals(prop.getKey())) {
                ipv4List.add(prop.getValue());
            } else {
                properties.addProperty(prop.getKey(), prop.getValue());
            }
        });
        properties.addProperty(InstanceTraffic.PropertyUtil.IPV4S, ipv4List.stream().collect(Collectors.joining(",")));
        serviceInstanceUpdate.setProperties(properties);
        serviceInstanceUpdate.setTimeBucket(
            TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Minute));
        sourceReceiver.receive(serviceInstanceUpdate);
    }

    private final void keepAlive(InstancePingPkg request) {
        final long timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Minute);
        final String serviceName = namingLengthControl.formatServiceName(request.getService());
        final String instanceName = namingLengthControl.formatInstanceName(request.getServiceInstance());

        if (log.isDebugEnabled()) {
            log.debug(
                "A ping of Service[{}] instance[{}].",
                serviceName,
                instanceName
            );
        }

        ServiceInstanceUpdate serviceInstanceUpdate = new ServiceInstanceUpdate();
        serviceInstanceUpdate.setServiceId(IDManager.ServiceID.buildId(serviceName, NodeType.Normal));
        serviceInstanceUpdate.setName(instanceName);
        serviceInstanceUpdate.setTimeBucket(timeBucket);
        sourceReceiver.receive(serviceInstanceUpdate);
    }

    @Override
    public String getTopic() {
        return config.getMm2SourceAlias() + config.getMm2SourceSeparator() + config.getTopicNameOfManagements();
    }

    @Override
    public String getConsumePartitions() {
        return config.getConsumePartitions();
    }
}
