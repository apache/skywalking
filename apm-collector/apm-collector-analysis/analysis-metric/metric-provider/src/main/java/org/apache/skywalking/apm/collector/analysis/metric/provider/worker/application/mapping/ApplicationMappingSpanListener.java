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
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMapping;
import org.apache.skywalking.apm.network.proto.SpanLayer;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ApplicationMappingSpanListener implements EntrySpanListener {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationMappingSpanListener.class);

    private final ApplicationCacheService applicationCacheService;
    private List<ApplicationMapping> applicationMappings = new LinkedList<>();

    private ApplicationMappingSpanListener(ModuleManager moduleManager) {
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
    }

    @Override public boolean containsPoint(Point point) {
        return Point.Entry.equals(point);
    }

    @Override public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        if (logger.isDebugEnabled()) {
            logger.debug("application mapping listener parse reference");
        }

        if (!spanDecorator.getSpanLayer().equals(SpanLayer.MQ)) {
            if (spanDecorator.getRefsCount() > 0) {
                for (int i = 0; i < spanDecorator.getRefsCount(); i++) {
                    ApplicationMapping applicationMapping = new ApplicationMapping();
                    applicationMapping.setApplicationId(segmentCoreInfo.getApplicationId());

                    int addressId = spanDecorator.getRefs(i).getNetworkAddressId();
                    int mappingApplicationId = applicationCacheService.getApplicationIdByAddressId(addressId);
                    applicationMapping.setMappingApplicationId(mappingApplicationId);

                    String metricId = String.valueOf(segmentCoreInfo.getApplicationId()) + Const.ID_SPLIT + String.valueOf(applicationMapping.getMappingApplicationId());
                    applicationMapping.setMetricId(metricId);
                    applicationMapping.setId(segmentCoreInfo.getMinuteTimeBucket() + Const.ID_SPLIT + applicationMapping.getMetricId());
                    applicationMapping.setTimeBucket(segmentCoreInfo.getMinuteTimeBucket());
                    applicationMappings.add(applicationMapping);
                }
            }
        }
    }

    @Override public void build() {
        if (logger.isDebugEnabled()) {
            logger.debug("application mapping listener build");
        }

        Graph<ApplicationMapping> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.APPLICATION_MAPPING_GRAPH_ID, ApplicationMapping.class);
        applicationMappings.forEach(applicationMapping -> {
            if (logger.isDebugEnabled()) {
                logger.debug("push to application mapping aggregation worker, id: {}", applicationMapping.getId());
            }

            graph.start(applicationMapping);
        });
    }

    public static class Factory implements SpanListenerFactory {

        @GraphComputingMetric(name = "/segment/parse/createSpanListeners/applicationMappingSpanListener")
        @Override public SpanListener create(ModuleManager moduleManager) {
            return new ApplicationMappingSpanListener(moduleManager);
        }
    }
}
