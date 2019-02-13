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

import java.util.*;
import lombok.*;
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.service.IServiceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ServiceMappingSpanListener implements EntrySpanListener {

    private static final Logger logger = LoggerFactory.getLogger(ServiceMappingSpanListener.class);

    private final IServiceInventoryRegister serviceInventoryRegister;
    private final ServiceInventoryCache serviceInventoryCache;
    private List<ServiceMapping> serviceMappings = new LinkedList<>();

    private ServiceMappingSpanListener(ModuleManager moduleManager) {
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        this.serviceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
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
                    int serviceId = serviceInventoryCache.getServiceId(spanDecorator.getRefs(i).getNetworkAddressId());
                    int mappingServiceId = serviceInventoryCache.get(serviceId).getMappingServiceId();
                    if (mappingServiceId != segmentCoreInfo.getServiceId()) {
                        ServiceMapping serviceMapping = new ServiceMapping();
                        serviceMapping.setServiceId(serviceId);
                        serviceMapping.setMappingServiceId(segmentCoreInfo.getServiceId());
                        serviceMappings.add(serviceMapping);
                    }
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
            return new ServiceMappingSpanListener(moduleManager);
        }
    }

    @Setter
    @Getter
    private class ServiceMapping {
        private int serviceId;
        private int mappingServiceId;
    }
}
