/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agent.stream.worker.trace.service;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.agent.stream.graph.TraceStreamGraph;
import org.skywalking.apm.collector.agent.stream.parser.EntrySpanListener;
import org.skywalking.apm.collector.agent.stream.parser.FirstSpanListener;
import org.skywalking.apm.collector.agent.stream.parser.RefsListener;
import org.skywalking.apm.collector.agent.stream.parser.standardization.ReferenceDecorator;
import org.skywalking.apm.collector.agent.stream.parser.standardization.SpanDecorator;
import org.skywalking.apm.collector.core.graph.Graph;
import org.skywalking.apm.collector.core.graph.GraphManager;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReferenceMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceMetricSpanListener implements FirstSpanListener, EntrySpanListener, RefsListener {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceMetricSpanListener.class);

    private List<ReferenceDecorator> referenceServices = new LinkedList<>();
    private int serviceId = 0;
    private long startTime = 0;
    private long endTime = 0;
    private boolean isError = false;
    private long timeBucket;
    private boolean hasEntry = false;

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
    }

    @Override public void parseRef(ReferenceDecorator referenceDecorator, int applicationId, int applicationInstanceId,
        String segmentId) {
        referenceServices.add(referenceDecorator);
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        serviceId = spanDecorator.getOperationNameId();
        startTime = spanDecorator.getStartTime();
        endTime = spanDecorator.getEndTime();
        isError = spanDecorator.getIsError();
        this.hasEntry = true;
    }

    private void calculateCost(ServiceReferenceMetric serviceReferenceMetric, long startTime,
        long endTime, boolean isError) {
        long cost = endTime - startTime;
        if (cost <= 1000 && !isError) {
            serviceReferenceMetric.setS1Lte(1L);
        } else if (1000 < cost && cost <= 3000 && !isError) {
            serviceReferenceMetric.setS3Lte(1L);
        } else if (3000 < cost && cost <= 5000 && !isError) {
            serviceReferenceMetric.setS5Lte(1L);
        } else if (5000 < cost && !isError) {
            serviceReferenceMetric.setS5Gt(1L);
        } else {
            serviceReferenceMetric.setError(1L);
        }
        serviceReferenceMetric.setSummary(1L);
        serviceReferenceMetric.setCostSummary(cost);
    }

    @Override public void build() {
        logger.debug("service reference listener build");
        if (hasEntry) {
            if (referenceServices.size() > 0) {
                referenceServices.forEach(reference -> {
                    ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric(Const.EMPTY_STRING);
                    int entryServiceId = reference.getEntryServiceId();
                    int frontServiceId = reference.getParentServiceId();
                    int behindServiceId = serviceId;
                    calculateCost(serviceReferenceMetric, startTime, endTime, isError);

                    logger.debug("has reference, entryServiceId: {}", entryServiceId);
                    sendToAggregationWorker(serviceReferenceMetric, entryServiceId, frontServiceId, behindServiceId);
                });
            } else {
                ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric(Const.EMPTY_STRING);
                int entryServiceId = serviceId;
                int frontServiceId = Const.NONE_SERVICE_ID;
                int behindServiceId = serviceId;

                calculateCost(serviceReferenceMetric, startTime, endTime, isError);
                sendToAggregationWorker(serviceReferenceMetric, entryServiceId, frontServiceId, behindServiceId);
            }
        }
    }

    private void sendToAggregationWorker(ServiceReferenceMetric serviceReferenceMetric, int entryServiceId, int frontServiceId,
        int behindServiceId) {
        StringBuilder idBuilder = new StringBuilder();
        idBuilder.append(timeBucket).append(Const.ID_SPLIT);

        idBuilder.append(entryServiceId).append(Const.ID_SPLIT);
        serviceReferenceMetric.setEntryServiceId(entryServiceId);

        idBuilder.append(frontServiceId).append(Const.ID_SPLIT);
        serviceReferenceMetric.setFrontServiceId(frontServiceId);

        idBuilder.append(behindServiceId);
        serviceReferenceMetric.setBehindServiceId(behindServiceId);

        serviceReferenceMetric.setId(idBuilder.toString());
        serviceReferenceMetric.setTimeBucket(timeBucket);
        logger.debug("push to service reference aggregation worker, id: {}", serviceReferenceMetric.getId());

        Graph<ServiceReferenceMetric> graph = GraphManager.INSTANCE.createIfAbsent(TraceStreamGraph.SERVICE_REFERENCE_GRAPH_ID, ServiceReferenceMetric.class);
        graph.start(serviceReferenceMetric);
    }
}
