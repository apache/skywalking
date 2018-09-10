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
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.*;

/**
 * @author peng-yongsheng
 */
public class ServiceComponentSpanListener implements EntrySpanListener, ExitSpanListener {

    private final SourceReceiver sourceReceiver;
    private final ServiceInventoryCache serviceInventoryCache;
    private final List<ServiceComponent> serviceComponents = new LinkedList<>();

    private ServiceComponentSpanListener(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).getService(SourceReceiver.class);
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).getService(ServiceInventoryCache.class);
    }

    @Override public boolean containsPoint(Point point) {
        return Point.Entry.equals(point) || Point.Exit.equals(point);
    }

    @Override
    public void parseExit(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        int serviceIdByPeerId = serviceInventoryCache.getServiceId(spanDecorator.getPeerId());

        ServiceComponent serviceComponent = new ServiceComponent();
        serviceComponent.setServiceId(serviceIdByPeerId);
        serviceComponent.setComponentId(spanDecorator.getComponentId());
        serviceComponent.setTimeBucket(segmentCoreInfo.getMinuteTimeBucket());
        serviceComponents.add(serviceComponent);
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        ServiceComponent serviceComponent = new ServiceComponent();
        serviceComponent.setServiceId(segmentCoreInfo.getApplicationId());
        serviceComponent.setComponentId(spanDecorator.getComponentId());
        serviceComponent.setTimeBucket(segmentCoreInfo.getMinuteTimeBucket());
        serviceComponents.add(serviceComponent);
    }

    @Override public void build() {
        serviceComponents.forEach(sourceReceiver::receive);
    }

    public static class Factory implements SpanListenerFactory {

        @Override public SpanListener create(ModuleManager moduleManager) {
            return new ServiceComponentSpanListener(moduleManager);
        }
    }
}
