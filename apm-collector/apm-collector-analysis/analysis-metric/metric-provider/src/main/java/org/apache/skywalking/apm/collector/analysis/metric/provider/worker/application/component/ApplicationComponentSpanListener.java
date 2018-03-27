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

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.SpanDecorator;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.EntrySpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.ExitSpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.FirstSpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.SpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.SpanListenerFactory;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponent;

/**
 * @author peng-yongsheng
 */
public class ApplicationComponentSpanListener implements EntrySpanListener, ExitSpanListener, FirstSpanListener {

    private final ApplicationCacheService applicationCacheService;
    private List<ApplicationComponent> applicationComponents = new ArrayList<>();
    private long timeBucket;

    ApplicationComponentSpanListener(ModuleManager moduleManager) {
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
    }

    @Override
    public void parseExit(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        int applicationIdFromPeerId = applicationCacheService.getApplicationIdByAddressId(spanDecorator.getPeerId());

        String metricId = applicationIdFromPeerId + Const.ID_SPLIT + String.valueOf(spanDecorator.getComponentId());

        ApplicationComponent applicationComponent = new ApplicationComponent();
        applicationComponent.setMetricId(metricId);
        applicationComponent.setComponentId(spanDecorator.getComponentId());
        applicationComponent.setApplicationId(applicationIdFromPeerId);
        applicationComponents.add(applicationComponent);
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        String metricId = String.valueOf(applicationId) + Const.ID_SPLIT + String.valueOf(spanDecorator.getComponentId());

        ApplicationComponent applicationComponent = new ApplicationComponent();
        applicationComponent.setMetricId(metricId);
        applicationComponent.setComponentId(spanDecorator.getComponentId());
        applicationComponent.setApplicationId(applicationId);
        applicationComponents.add(applicationComponent);
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
    }

    @Override public void build() {
        Graph<ApplicationComponent> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.APPLICATION_COMPONENT_GRAPH_ID, ApplicationComponent.class);

        applicationComponents.forEach(applicationComponent -> {
            applicationComponent.setId(timeBucket + Const.ID_SPLIT + applicationComponent.getMetricId());
            applicationComponent.setTimeBucket(timeBucket);
            graph.start(applicationComponent);
        });
    }

    public static class Factory implements SpanListenerFactory {
        @Override public SpanListener create(ModuleManager moduleManager) {
            return new ApplicationComponentSpanListener(moduleManager);
        }
    }
}
