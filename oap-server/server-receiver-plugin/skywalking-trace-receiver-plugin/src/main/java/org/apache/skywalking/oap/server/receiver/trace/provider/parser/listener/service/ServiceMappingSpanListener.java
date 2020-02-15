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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.register.service.IServiceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SegmentCoreInfo;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SpanDecorator;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.EntrySpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.SpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.SpanListenerFactory;

/**
 * ServiceMappingSpanListener includes the specific logic about the concept of service mapping. Service mapping is the
 * core idea to make the SkyWalking has good performance and low memory costs, when discovery the big topology.
 */
@Slf4j
public class ServiceMappingSpanListener implements EntrySpanListener {
    private final IServiceInventoryRegister serviceInventoryRegister;
    private final TraceServiceModuleConfig config;
    private final ServiceInventoryCache serviceInventoryCache;
    private final NetworkAddressInventoryCache networkAddressInventoryCache;
    private final List<ServiceMapping> serviceMappings = new ArrayList<>();
    private final List<Integer> servicesToResetMapping = new ArrayList<>();

    private ServiceMappingSpanListener(ModuleManager moduleManager, TraceServiceModuleConfig config) {
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME)
                                                  .provider()
                                                  .getService(ServiceInventoryCache.class);
        this.networkAddressInventoryCache = moduleManager.find(CoreModule.NAME)
                                                         .provider()
                                                         .getService(NetworkAddressInventoryCache.class);
        this.serviceInventoryRegister = moduleManager.find(CoreModule.NAME)
                                                     .provider()
                                                     .getService(IServiceInventoryRegister.class);
        this.config = config;
    }

    @Override
    public boolean containsPoint(Point point) {
        return Point.Entry.equals(point);
    }

    /**
     * Fetch the network address information used at the client side from the propagated context(headers mostly. Besides
     * the MQ and uninstrumented services, the the network address will be treated as the alias name of the current
     * service. The alias mechanism is the service mapping.
     */
    @Override
    public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        if (log.isDebugEnabled()) {
            log.debug("service mapping listener parse reference");
        }

        if (!spanDecorator.getSpanLayer().equals(SpanLayer.MQ)) {
            if (spanDecorator.getRefsCount() > 0) {
                for (int i = 0; i < spanDecorator.getRefsCount(); i++) {
                    int networkAddressId = spanDecorator.getRefs(i).getNetworkAddressId();
                    String address = networkAddressInventoryCache.get(networkAddressId).getName();
                    int serviceId = serviceInventoryCache.getServiceId(networkAddressId);

                    if (config.getUninstrumentedGatewaysConfig().isAddressConfiguredAsGateway(address)) {
                        if (log.isDebugEnabled()) {
                            log.debug("{} is configured as gateway, will reset its mapping service id", serviceId);
                        }
                        ServiceInventory serviceInventory = serviceInventoryCache.get(serviceId);
                        if (serviceInventory.getMappingServiceId() != Const.NONE && !servicesToResetMapping.contains(
                            serviceId)) {
                            servicesToResetMapping.add(serviceId);
                        }
                    } else {
                        ServiceMapping serviceMapping = new ServiceMapping();
                        serviceMapping.setServiceId(serviceId);
                        serviceMapping.setMappingServiceId(segmentCoreInfo.getServiceId());
                        serviceMappings.add(serviceMapping);
                    }
                }
            }
        }
    }

    @Override
    public void build() {
        serviceMappings.forEach(serviceMapping -> {
            if (log.isDebugEnabled()) {
                log.debug(
                    "service mapping listener build, service id: {}, mapping service id: {}",
                    serviceMapping.getServiceId(), serviceMapping
                        .getMappingServiceId()
                );
            }
            serviceInventoryRegister.updateMapping(serviceMapping.getServiceId(), serviceMapping.getMappingServiceId());
        });
        servicesToResetMapping.forEach(serviceId -> {
            if (log.isDebugEnabled()) {
                log.debug("service mapping listener build, reset mapping of service id: {}", serviceId);
            }
            serviceInventoryRegister.resetMapping(serviceId);
        });
    }

    public static class Factory implements SpanListenerFactory {

        @Override
        public SpanListener create(ModuleManager moduleManager, TraceServiceModuleConfig config) {
            return new ServiceMappingSpanListener(moduleManager, config);
        }
    }

    @Setter
    @Getter
    private static class ServiceMapping {
        private int serviceId;
        private int mappingServiceId;
    }
}
