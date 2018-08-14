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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.component;

import java.util.*;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.*;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.*;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.graph.*;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponent;

/**
 * @author peng-yongsheng
 */
public class ApplicationComponentSpanListener implements EntrySpanListener, ExitSpanListener {

    private final ApplicationCacheService applicationCacheService;
    private final List<ApplicationComponent> applicationComponents = new LinkedList<>();

    private ApplicationComponentSpanListener(ModuleManager moduleManager) {
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
    }

    @Override public boolean containsPoint(Point point) {
        return Point.Entry.equals(point) || Point.Exit.equals(point);
    }

    @Override
    public void parseExit(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        int applicationIdFromPeerId = applicationCacheService.getApplicationIdByAddressId(spanDecorator.getPeerId());

        String metricId = applicationIdFromPeerId + Const.ID_SPLIT + String.valueOf(spanDecorator.getComponentId());

        ApplicationComponent applicationComponent = new ApplicationComponent();
        applicationComponent.setMetricId(metricId);
        applicationComponent.setComponentId(spanDecorator.getComponentId());
        applicationComponent.setApplicationId(applicationIdFromPeerId);
        applicationComponent.setId(segmentCoreInfo.getMinuteTimeBucket() + Const.ID_SPLIT + applicationComponent.getMetricId());
        applicationComponent.setTimeBucket(segmentCoreInfo.getMinuteTimeBucket());
        applicationComponents.add(applicationComponent);
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        String metricId = String.valueOf(segmentCoreInfo.getApplicationId()) + Const.ID_SPLIT + String.valueOf(spanDecorator.getComponentId());

        ApplicationComponent applicationComponent = new ApplicationComponent();
        applicationComponent.setMetricId(metricId);
        applicationComponent.setComponentId(spanDecorator.getComponentId());
        applicationComponent.setApplicationId(segmentCoreInfo.getApplicationId());
        applicationComponent.setId(segmentCoreInfo.getMinuteTimeBucket() + Const.ID_SPLIT + applicationComponent.getMetricId());
        applicationComponent.setTimeBucket(segmentCoreInfo.getMinuteTimeBucket());
        applicationComponents.add(applicationComponent);
    }

    @Override public void build() {
        Graph<ApplicationComponent> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.APPLICATION_COMPONENT_GRAPH_ID, ApplicationComponent.class);
        applicationComponents.forEach(graph::start);
    }

    public static class Factory implements SpanListenerFactory {

        @GraphComputingMetric(name = "/segment/parse/createSpanListeners/applicationComponentSpanListener")
        @Override public SpanListener create(ModuleManager moduleManager) {
            return new ApplicationComponentSpanListener(moduleManager);
        }
    }
}
