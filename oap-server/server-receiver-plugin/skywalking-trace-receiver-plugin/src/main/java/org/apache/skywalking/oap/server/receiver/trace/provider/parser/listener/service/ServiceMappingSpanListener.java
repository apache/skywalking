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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * @author peng-yongsheng
 */
public class ServiceMappingSpanListener implements EntrySpanListener {

    private static final Logger logger = LoggerFactory.getLogger(ServiceMappingSpanListener.class);

    private final IServiceInventoryRegister serviceInventoryRegister;
    private final TraceServiceModuleConfig config;
    private final ServiceInventoryCache serviceInventoryCache;
    private final NetworkAddressInventoryCache networkAddressInventoryCache;
    private List<ServiceMapping> serviceMappings = new LinkedList<>();

    private ServiceMappingSpanListener(ModuleManager moduleManager, TraceServiceModuleConfig config) {
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        this.networkAddressInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(NetworkAddressInventoryCache.class);
        this.serviceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
        this.config = config;
    }

    @Override public boolean containsPoint(Point point) {
        return Point.Entry.equals(point);
    }

    @Override public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        if (logger.isDebugEnabled()) {
            logger.debug("service mapping listener parse reference");
        }

        if (!spanDecorator.getSpanLayer().equals(SpanLayer.MQ)) {
            if (spanDecorator.getRefsCount() > 0) {
                for (int i = 0; i < spanDecorator.getRefsCount(); i++) {
                    int networkAddressId = spanDecorator.getRefs(i).getNetworkAddressId();
                    String address = networkAddressInventoryCache.get(networkAddressId).getName();
                    int serviceId = serviceInventoryCache.getServiceId(networkAddressId);
                    ServiceInventory serviceInventory = serviceInventoryCache.get(serviceId);
                    ServiceMapping serviceMapping = new ServiceMapping();
                    serviceMapping.setServiceId(serviceId);

                    if (config.getStaticGatewaysConfig().isAddressConfiguredAsGateway(address)) {
                        serviceMapping.setMappingServiceId(Const.NONE);
                        serviceInventory.setMappingServiceId(Const.NONE);
                    } else {
                        serviceMapping.setMappingServiceId(segmentCoreInfo.getServiceId());
                    }
                    serviceMappings.add(serviceMapping);
                }
            }
        }
    }

    @Override public void build() {
        serviceMappings.forEach(serviceMapping -> {
            if (logger.isDebugEnabled()) {
                logger.debug("service mapping listener build, service id: {}, mapping service id: {}", serviceMapping.getServiceId(), serviceMapping.getMappingServiceId());
            }
            serviceInventoryRegister.updateMapping(serviceMapping.getServiceId(), serviceMapping.getMappingServiceId());
        });
    }

    public static class Factory implements SpanListenerFactory {

        @Override public SpanListener create(ModuleManager moduleManager, TraceServiceModuleConfig config) {
            return new ServiceMappingSpanListener(moduleManager, config);
        }
    }

    @Setter
    @Getter
    private class ServiceMapping {
        private int serviceId;
        private int mappingServiceId;
    }
}
