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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.service;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.ReferenceDecorator;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentCoreInfo;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SpanDecorator;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.EntryAnalysisListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.AnalysisListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.AnalysisListenerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service Instance mapping basically is as same as the service mapping. The network address fetched from the propagated
 * context is the alias for the specific service instance. This is just more detailed mapping setup.
 * <p>
 * Read {@link ServiceMappingAnalysisListener}.
 */
@Slf4j
public class ServiceInstanceMappingAnalysisListener implements EntryAnalysisListener {
    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private final TraceServiceModuleConfig config;
    private final ServiceInventoryCache serviceInventoryCache;
    private final ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private final NetworkAddressAliasCache networkAddressAliasCache;
    private final List<ServiceInstanceMapping> serviceInstanceMappings = new ArrayList<>();
    private final List<Integer> serviceInstancesToResetMapping = new ArrayList<>();

    public ServiceInstanceMappingAnalysisListener(ModuleManager moduleManager, TraceServiceModuleConfig config) {
        this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME)
            .provider()
            .getService(ServiceInstanceInventoryCache.class);
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME)
            .provider()
            .getService(ServiceInventoryCache.class);
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME)
            .provider()
            .getService(IServiceInstanceInventoryRegister.class);
        this.networkAddressAliasCache = moduleManager.find(CoreModule.NAME)
                                                     .provider()
                                                     .getService(NetworkAddressAliasCache.class);
        this.config = config;
    }

    @Override
    public void parseEntry(SpanObject span, SegmentObject segmentObject) {
        if (log.isDebugEnabled()) {
            log.debug("service instance mapping listener parse reference");
        }
        if (!spanDecorator.getSpanLayer().equals(SpanLayer.MQ)) {
            if (spanDecorator.getRefsCount() > 0) {
                for (int i = 0; i < spanDecorator.getRefsCount(); i++) {
                    ReferenceDecorator referenceDecorator = spanDecorator.getRefs(i);
                    String parentLanguage = serviceInstanceInventoryCache.getServiceInstanceLanguage(referenceDecorator.getParentServiceInstanceId());
                    if (config.getNoUpstreamRealAddressAgents().contains(parentLanguage)) {
                        /*
                         * Some of the agent can not have the upstream real network address, such as https://github.com/apache/skywalking-nginx-lua.
                         */
                        continue;
                    }
                    int networkAddressId = spanDecorator.getRefs(i).getNetworkAddressId();
                    String address = networkAddressAliasCache.get(networkAddressId).getName();
                    int serviceInstanceId = serviceInstanceInventoryCache.getServiceInstanceId(
                        serviceInventoryCache.getServiceId(networkAddressId), networkAddressId);

                    if (config.getUninstrumentedGatewaysConfig().isAddressConfiguredAsGateway(address)) {
                        if (log.isDebugEnabled()) {
                            log.debug(
                                "{} is configured as gateway, will reset its mapping service instance id",
                                serviceInstanceId
                            );
                        }
                        ServiceInstanceInventory instanceInventory = serviceInstanceInventoryCache.get(
                            serviceInstanceId);
                        if (instanceInventory.getMappingServiceInstanceId() != Const.NONE && !serviceInstancesToResetMapping
                            .contains(serviceInstanceId)) {
                            serviceInstancesToResetMapping.add(serviceInstanceId);
                        }
                    } else {
                        ServiceInstanceMapping serviceMapping = new ServiceInstanceMapping();
                        serviceMapping.setServiceInstanceId(serviceInstanceId);
                        serviceMapping.setMappingServiceInstanceId(segmentCoreInfo.getServiceInstanceId());
                        serviceInstanceMappings.add(serviceMapping);
                    }
                }
            }
        }
    }

    @Override
    public void build() {
        serviceInstanceMappings.forEach(instanceMapping -> {
            if (log.isDebugEnabled()) {
                log.debug(
                    "service instance mapping listener build, service instance id: {}, mapping service instance id: {}", instanceMapping
                        .getServiceInstanceId(), instanceMapping.getMappingServiceInstanceId());
            }
            serviceInstanceInventoryRegister.updateMapping(
                instanceMapping.getServiceInstanceId(), instanceMapping.getMappingServiceInstanceId());
        });
        serviceInstancesToResetMapping.forEach(instanceId -> {
            if (log.isDebugEnabled()) {
                log.debug("service instance mapping listener build, reset mapping of service instance id: {}", instanceId);
            }
            serviceInstanceInventoryRegister.resetMapping(instanceId);
        });
    }

    @Override
    public boolean containsPoint(Point point) {
        return Point.Entry.equals(point);
    }

    public static class Factory implements AnalysisListenerFactory {

        @Override
        public AnalysisListener create(ModuleManager moduleManager, TraceServiceModuleConfig config) {
            return new ServiceInstanceMappingAnalysisListener(moduleManager, config);
        }
    }

    @Getter
    @Setter
    private static class ServiceInstanceMapping {
        private int serviceInstanceId;
        private int mappingServiceInstanceId;
    }
}
