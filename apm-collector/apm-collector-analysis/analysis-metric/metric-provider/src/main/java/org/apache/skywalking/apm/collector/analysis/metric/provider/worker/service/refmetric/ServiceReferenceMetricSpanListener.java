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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.refmetric;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.ReferenceDecorator;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.SpanDecorator;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.EntrySpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.ExitSpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.FirstSpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.SpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.SpanListenerFactory;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;
import org.apache.skywalking.apm.network.proto.SpanLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceMetricSpanListener implements FirstSpanListener, EntrySpanListener, ExitSpanListener {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceMetricSpanListener.class);

    private final InstanceCacheService instanceCacheService;
    private final ApplicationCacheService applicationCacheService;
    private final List<ServiceReferenceMetric> entryReferenceMetric;
    private List<ServiceReferenceMetric> exitReferenceMetric;
    private SpanDecorator entrySpanDecorator;
    private long timeBucket;

    ServiceReferenceMetricSpanListener(ModuleManager moduleManager) {
        this.entryReferenceMetric = new LinkedList<>();
        this.exitReferenceMetric = new LinkedList<>();
        this.instanceCacheService = moduleManager.find(CacheModule.NAME).getService(InstanceCacheService.class);
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        this.timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        if (spanDecorator.getRefsCount() > 0) {
            for (int i = 0; i < spanDecorator.getRefsCount(); i++) {
                ReferenceDecorator reference = spanDecorator.getRefs(i);
                ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric();
                serviceReferenceMetric.setFrontServiceId(reference.getParentServiceId());

                if (spanDecorator.getSpanLayer().equals(SpanLayer.MQ)) {
                    int applicationIdByPeerId = applicationCacheService.getApplicationIdByAddressId(reference.getNetworkAddressId());
                    int instanceIdByPeerId = instanceCacheService.getInstanceIdByAddressId(applicationIdByPeerId, reference.getNetworkAddressId());
                    serviceReferenceMetric.setFrontInstanceId(instanceIdByPeerId);
                    serviceReferenceMetric.setFrontApplicationId(applicationIdByPeerId);
                } else {
                    serviceReferenceMetric.setFrontInstanceId(reference.getParentApplicationInstanceId());
                    serviceReferenceMetric.setFrontApplicationId(instanceCacheService.getApplicationId(reference.getParentApplicationInstanceId()));
                }
                serviceReferenceMetric.setBehindServiceId(spanDecorator.getOperationNameId());
                serviceReferenceMetric.setBehindInstanceId(instanceId);
                serviceReferenceMetric.setBehindApplicationId(applicationId);
                serviceReferenceMetric.setSourceValue(MetricSource.Callee.getValue());
                calculateDuration(serviceReferenceMetric, spanDecorator);
                entryReferenceMetric.add(serviceReferenceMetric);
            }
        } else {
            ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric();
            serviceReferenceMetric.setFrontServiceId(Const.NONE_SERVICE_ID);
            serviceReferenceMetric.setFrontInstanceId(Const.NONE_INSTANCE_ID);
            serviceReferenceMetric.setFrontApplicationId(Const.NONE_APPLICATION_ID);
            serviceReferenceMetric.setBehindServiceId(spanDecorator.getOperationNameId());
            serviceReferenceMetric.setBehindInstanceId(instanceId);
            serviceReferenceMetric.setBehindApplicationId(applicationId);
            serviceReferenceMetric.setSourceValue(MetricSource.Callee.getValue());

            calculateDuration(serviceReferenceMetric, spanDecorator);
            entryReferenceMetric.add(serviceReferenceMetric);
        }
        this.entrySpanDecorator = spanDecorator;
    }

    @Override public void parseExit(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric();

        int peerId = spanDecorator.getPeerId();
        int behindApplicationId = applicationCacheService.getApplicationIdByAddressId(peerId);
        int behindInstanceId = instanceCacheService.getInstanceIdByAddressId(behindApplicationId, peerId);

        serviceReferenceMetric.setFrontServiceId(Const.NONE_SERVICE_ID);
        serviceReferenceMetric.setFrontInstanceId(instanceId);
        serviceReferenceMetric.setFrontApplicationId(applicationId);
        serviceReferenceMetric.setBehindServiceId(spanDecorator.getOperationNameId());
        serviceReferenceMetric.setBehindInstanceId(behindInstanceId);
        serviceReferenceMetric.setBehindApplicationId(behindApplicationId);
        serviceReferenceMetric.setSourceValue(MetricSource.Caller.getValue());
        calculateDuration(serviceReferenceMetric, spanDecorator);
        exitReferenceMetric.add(serviceReferenceMetric);
    }

    private void calculateDuration(ServiceReferenceMetric serviceReferenceMetric, SpanDecorator spanDecorator) {
        long duration = spanDecorator.getEndTime() - spanDecorator.getStartTime();

        if (spanDecorator.getIsError()) {
            serviceReferenceMetric.setTransactionErrorCalls(1L);
            serviceReferenceMetric.setTransactionErrorDurationSum(duration);
        }
        serviceReferenceMetric.setTransactionCalls(1L);
        serviceReferenceMetric.setTransactionDurationSum(duration);

        if (SpanLayer.MQ.equals(spanDecorator.getSpanLayer())) {
            if (spanDecorator.getIsError()) {
                serviceReferenceMetric.setMqTransactionErrorCalls(1L);
                serviceReferenceMetric.setMqTransactionErrorDurationSum(duration);
            }
            serviceReferenceMetric.setMqTransactionCalls(1L);
            serviceReferenceMetric.setMqTransactionDurationSum(duration);
        } else {
            if (spanDecorator.getIsError()) {
                serviceReferenceMetric.setBusinessTransactionErrorCalls(1L);
                serviceReferenceMetric.setBusinessTransactionErrorDurationSum(duration);
            }
            serviceReferenceMetric.setBusinessTransactionCalls(1L);
            serviceReferenceMetric.setBusinessTransactionDurationSum(duration);
        }
    }

    @Override public void build() {
        logger.debug("service reference listener build");
        Graph<ServiceReferenceMetric> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.SERVICE_REFERENCE_METRIC_GRAPH_ID, ServiceReferenceMetric.class);
        entryReferenceMetric.forEach(serviceReferenceMetric -> {
            String metricId = serviceReferenceMetric.getFrontServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getBehindServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getSourceValue();
            String id = timeBucket + Const.ID_SPLIT + metricId;

            serviceReferenceMetric.setId(id);
            serviceReferenceMetric.setMetricId(metricId);
            serviceReferenceMetric.setTimeBucket(timeBucket);
            logger.debug("push to service reference aggregation worker, id: {}", serviceReferenceMetric.getId());

            graph.start(serviceReferenceMetric);
        });

        exitReferenceMetric.forEach(serviceReferenceMetric -> {
            if (ObjectUtils.isNotEmpty(entrySpanDecorator)) {
                serviceReferenceMetric.setFrontServiceId(entrySpanDecorator.getOperationNameId());
            } else {
                serviceReferenceMetric.setFrontServiceId(Const.NONE_SERVICE_ID);
            }

            String metricId = serviceReferenceMetric.getFrontServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getBehindServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getSourceValue();
            String id = timeBucket + Const.ID_SPLIT + metricId;
            serviceReferenceMetric.setId(id);
            serviceReferenceMetric.setMetricId(metricId);
            serviceReferenceMetric.setTimeBucket(timeBucket);

            graph.start(serviceReferenceMetric);
        });
    }

    public static class Factory implements SpanListenerFactory {
        @Override public SpanListener create(ModuleManager moduleManager) {
            return new ServiceReferenceMetricSpanListener(moduleManager);
        }
    }
}
