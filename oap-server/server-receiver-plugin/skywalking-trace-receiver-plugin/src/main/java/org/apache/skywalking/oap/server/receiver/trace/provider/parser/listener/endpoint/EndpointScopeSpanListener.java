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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.endpoint;

import java.util.*;
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.*;
import org.slf4j.*;

import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public class EndpointScopeSpanListener implements EntrySpanListener, ExitSpanListener {

    private static final Logger logger = LoggerFactory.getLogger(EndpointScopeSpanListener.class);

    private final ServiceInstanceInventoryCache instanceInventoryCache;
    private final ServiceInventoryCache serviceInventoryCache;
    private final List<ServiceReferenceMetric> entryReferenceMetric;
    private List<ServiceReferenceMetric> exitReferenceMetric;
    private SpanDecorator entrySpanDecorator;
    private long minuteTimeBucket;

    private EndpointScopeSpanListener(ModuleManager moduleManager) {
        this.entryReferenceMetric = new LinkedList<>();
        this.exitReferenceMetric = new LinkedList<>();
        this.instanceInventoryCache = moduleManager.find(CoreModule.NAME).getService(ServiceInstanceInventoryCache.class);
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).getService(ServiceInventoryCache.class);
    }

    @Override public boolean containsPoint(Point point) {
        return Point.Entry.equals(point) || Point.Exit.equals(point);
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        this.minuteTimeBucket = segmentCoreInfo.getMinuteTimeBucket();

        if (spanDecorator.getRefsCount() > 0) {
            for (int i = 0; i < spanDecorator.getRefsCount(); i++) {
                ReferenceDecorator reference = spanDecorator.getRefs(i);
                ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric();
                serviceReferenceMetric.setFrontServiceId(reference.getParentServiceId());

                if (spanDecorator.getSpanLayer().equals(SpanLayer.MQ)) {
                    int applicationIdByPeerId = serviceInventoryCache.getApplicationIdByAddressId(reference.getNetworkAddressId());
                    int instanceIdByPeerId = instanceInventoryCache.getInstanceIdByAddressId(applicationIdByPeerId, reference.getNetworkAddressId());
                    serviceReferenceMetric.setFrontInstanceId(instanceIdByPeerId);
                    serviceReferenceMetric.setFrontApplicationId(applicationIdByPeerId);
                } else {
                    serviceReferenceMetric.setFrontInstanceId(reference.getParentApplicationInstanceId());
                    serviceReferenceMetric.setFrontApplicationId(instanceInventoryCache.getApplicationId(reference.getParentApplicationInstanceId()));
                }
                serviceReferenceMetric.setBehindServiceId(spanDecorator.getOperationNameId());
                serviceReferenceMetric.setBehindInstanceId(segmentCoreInfo.getApplicationInstanceId());
                serviceReferenceMetric.setBehindApplicationId(segmentCoreInfo.getApplicationId());
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
            serviceReferenceMetric.setBehindInstanceId(segmentCoreInfo.getApplicationInstanceId());
            serviceReferenceMetric.setBehindApplicationId(segmentCoreInfo.getApplicationId());
            serviceReferenceMetric.setSourceValue(MetricSource.Callee.getValue());

            calculateDuration(serviceReferenceMetric, spanDecorator);
            entryReferenceMetric.add(serviceReferenceMetric);
        }
        this.entrySpanDecorator = spanDecorator;
    }

    @Override public void parseExit(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        if (this.minuteTimeBucket == 0) {
            this.minuteTimeBucket = segmentCoreInfo.getMinuteTimeBucket();
        }

        ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric();

        int peerId = spanDecorator.getPeerId();
        int behindApplicationId = serviceInventoryCache.getApplicationIdByAddressId(peerId);
        int behindInstanceId = instanceInventoryCache.getInstanceIdByAddressId(behindApplicationId, peerId);

        serviceReferenceMetric.setFrontServiceId(Const.NONE_SERVICE_ID);
        serviceReferenceMetric.setFrontInstanceId(segmentCoreInfo.getApplicationInstanceId());
        serviceReferenceMetric.setFrontApplicationId(segmentCoreInfo.getApplicationId());
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
        if (logger.isDebugEnabled()) {
            logger.debug("service reference listener build");
        }

        Graph<ServiceReferenceMetric> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.SERVICE_REFERENCE_METRIC_GRAPH_ID, ServiceReferenceMetric.class);
        entryReferenceMetric.forEach(serviceReferenceMetric -> {
            String metricId = serviceReferenceMetric.getFrontServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getBehindServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getSourceValue();
            String id = minuteTimeBucket + Const.ID_SPLIT + metricId;

            serviceReferenceMetric.setId(id);
            serviceReferenceMetric.setMetricId(metricId);
            serviceReferenceMetric.setTimeBucket(minuteTimeBucket);

            if (logger.isDebugEnabled()) {
                logger.debug("push to service reference aggregation worker, id: {}", serviceReferenceMetric.getId());
            }

            graph.start(serviceReferenceMetric);
        });

        exitReferenceMetric.forEach(serviceReferenceMetric -> {
            if (nonNull(entrySpanDecorator)) {
                serviceReferenceMetric.setFrontServiceId(entrySpanDecorator.getOperationNameId());
            } else {
                serviceReferenceMetric.setFrontServiceId(Const.NONE_SERVICE_ID);
            }

            String metricId = serviceReferenceMetric.getFrontServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getBehindServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getSourceValue();
            String id = minuteTimeBucket + Const.ID_SPLIT + metricId;
            serviceReferenceMetric.setId(id);
            serviceReferenceMetric.setMetricId(metricId);
            serviceReferenceMetric.setTimeBucket(minuteTimeBucket);

            graph.start(serviceReferenceMetric);
        });
    }

    public static class Factory implements SpanListenerFactory {

        @Override public SpanListener create(ModuleManager moduleManager) {
            return new EndpointScopeSpanListener(moduleManager);
        }
    }
}
