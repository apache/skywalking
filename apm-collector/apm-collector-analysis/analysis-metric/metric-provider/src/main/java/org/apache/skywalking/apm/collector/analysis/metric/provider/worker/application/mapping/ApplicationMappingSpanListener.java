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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.mapping;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.SpanDecorator;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.EntrySpanListener;
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
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMapping;
import org.apache.skywalking.apm.network.proto.SpanLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationMappingSpanListener implements FirstSpanListener, EntrySpanListener {

    private final Logger logger = LoggerFactory.getLogger(ApplicationMappingSpanListener.class);

    private final ApplicationCacheService applicationCacheService;
    private List<ApplicationMapping> applicationMappings = new LinkedList<>();
    private long timeBucket;

    ApplicationMappingSpanListener(ModuleManager moduleManager) {
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
    }

    @Override public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        logger.debug("application mapping listener parse reference");
        if (!spanDecorator.getSpanLayer().equals(SpanLayer.MQ)) {
            if (spanDecorator.getRefsCount() > 0) {
                for (int i = 0; i < spanDecorator.getRefsCount(); i++) {
                    ApplicationMapping applicationMapping = new ApplicationMapping();
                    applicationMapping.setApplicationId(applicationId);

                    int addressId = spanDecorator.getRefs(i).getNetworkAddressId();
                    int mappingApplicationId = applicationCacheService.getApplicationIdByAddressId(addressId);
                    applicationMapping.setMappingApplicationId(mappingApplicationId);

                    String metricId = String.valueOf(applicationId) + Const.ID_SPLIT + String.valueOf(applicationMapping.getMappingApplicationId());
                    applicationMapping.setMetricId(metricId);
                    applicationMappings.add(applicationMapping);
                }
            }
        }
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
    }

    @Override public void build() {
        logger.debug("application mapping listener build");
        Graph<ApplicationMapping> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.APPLICATION_MAPPING_GRAPH_ID, ApplicationMapping.class);
        applicationMappings.forEach(applicationMapping -> {
            applicationMapping.setId(timeBucket + Const.ID_SPLIT + applicationMapping.getMetricId());
            applicationMapping.setTimeBucket(timeBucket);
            logger.debug("push to application mapping aggregation worker, id: {}", applicationMapping.getId());
            graph.start(applicationMapping);
        });
    }

    public static class Factory implements SpanListenerFactory {
        @Override public SpanListener create(ModuleManager moduleManager) {
            return new ApplicationMappingSpanListener(moduleManager);
        }
    }
}
