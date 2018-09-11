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
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ServiceMappingSpanListener implements EntrySpanListener {

    private static final Logger logger = LoggerFactory.getLogger(ServiceMappingSpanListener.class);

    private final SourceReceiver sourceReceiver;
    private final ServiceInventoryCache serviceInventoryCache;
    private List<ServiceMapping> serviceMappings = new LinkedList<>();

    private ServiceMappingSpanListener(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).getService(SourceReceiver.class);
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).getService(ServiceInventoryCache.class);
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
                    ServiceMapping serviceMapping = new ServiceMapping();
                    serviceMapping.setServiceId(segmentCoreInfo.getApplicationId());

                    int addressId = spanDecorator.getRefs(i).getNetworkAddressId();
                    int mappingServiceId = serviceInventoryCache.getServiceId(addressId);
                    serviceMapping.setMappingServiceId(mappingServiceId);
                    serviceMapping.setTimeBucket(segmentCoreInfo.getMinuteTimeBucket());
                    serviceMappings.add(serviceMapping);
                }
            }
        }
    }

    @Override public void build() {
        if (logger.isDebugEnabled()) {
            logger.debug("service mapping listener build");
        }

        serviceMappings.forEach(sourceReceiver::receive);
    }

    public static class Factory implements SpanListenerFactory {

        @Override public SpanListener create(ModuleManager moduleManager) {
            return new ServiceMappingSpanListener(moduleManager);
        }
    }
}
