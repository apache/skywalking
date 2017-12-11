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


package org.apache.skywalking.apm.collector.agent.stream.worker.trace.service;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.agent.stream.graph.TraceStreamGraph;
import org.apache.skywalking.apm.collector.agent.stream.parser.EntrySpanListener;
import org.apache.skywalking.apm.collector.agent.stream.parser.ExitSpanListener;
import org.apache.skywalking.apm.collector.agent.stream.parser.FirstSpanListener;
import org.apache.skywalking.apm.collector.agent.stream.parser.standardization.SpanDecorator;
import org.apache.skywalking.apm.collector.agent.stream.service.trace.MetricSource;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.agent.stream.parser.standardization.ReferenceDecorator;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;
import org.apache.skywalking.apm.network.proto.SpanLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceMetricSpanListener implements FirstSpanListener, EntrySpanListener, ExitSpanListener {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceMetricSpanListener.class);

    private List<ServiceReferenceMetric> entryReferenceMetric = new LinkedList<>();
    private List<ServiceReferenceMetric> exitReferenceMetric = new LinkedList<>();
    private SpanDecorator entrySpanDecorator;
    private long timeBucket;

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        if (spanDecorator.getRefsCount() > 0) {
            for (int i = 0; i < spanDecorator.getRefsCount(); i++) {
                ReferenceDecorator reference = spanDecorator.getRefs(i);
                ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric(Const.EMPTY_STRING);
                serviceReferenceMetric.setEntryServiceId(reference.getEntryServiceId());
                serviceReferenceMetric.setEntryInstanceId(reference.getEntryApplicationInstanceId());
                serviceReferenceMetric.setFrontServiceId(reference.getParentServiceId());
                serviceReferenceMetric.setFrontInstanceId(reference.getParentApplicationInstanceId());
                serviceReferenceMetric.setBehindServiceId(spanDecorator.getOperationNameId());
                serviceReferenceMetric.setBehindInstanceId(instanceId);
                serviceReferenceMetric.setSourceValue(MetricSource.Entry.ordinal());
                calculateCost(serviceReferenceMetric, spanDecorator, true);
                entryReferenceMetric.add(serviceReferenceMetric);
            }
        } else {
            ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric(Const.EMPTY_STRING);
            serviceReferenceMetric.setEntryServiceId(spanDecorator.getOperationNameId());
            serviceReferenceMetric.setEntryInstanceId(instanceId);
            serviceReferenceMetric.setFrontServiceId(Const.NONE_SERVICE_ID);
            serviceReferenceMetric.setFrontInstanceId(instanceId);
            serviceReferenceMetric.setBehindServiceId(spanDecorator.getOperationNameId());
            serviceReferenceMetric.setBehindServiceId(instanceId);
            serviceReferenceMetric.setSourceValue(MetricSource.Entry.ordinal());

            calculateCost(serviceReferenceMetric, spanDecorator, false);
            entryReferenceMetric.add(serviceReferenceMetric);
        }
        this.entrySpanDecorator = spanDecorator;
    }

    @Override public void parseExit(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric(Const.EMPTY_STRING);

        serviceReferenceMetric.setFrontInstanceId(instanceId);
        serviceReferenceMetric.setBehindServiceId(spanDecorator.getOperationNameId());
        serviceReferenceMetric.setSourceValue(MetricSource.Exit.ordinal());
        calculateCost(serviceReferenceMetric, spanDecorator, true);
        exitReferenceMetric.add(serviceReferenceMetric);
    }

    private void calculateCost(ServiceReferenceMetric serviceReferenceMetric, SpanDecorator spanDecorator,
        boolean hasReference) {
        long duration = spanDecorator.getStartTime() - spanDecorator.getEndTime();

        if (spanDecorator.getIsError()) {
            serviceReferenceMetric.setTransactionErrorCalls(1L);
            serviceReferenceMetric.setTransactionErrorDurationSum(duration);
        } else {
            serviceReferenceMetric.setTransactionCalls(1L);
            serviceReferenceMetric.setTransactionDurationSum(duration);
        }

        if (hasReference) {
            if (spanDecorator.getIsError()) {
                serviceReferenceMetric.setBusinessTransactionErrorCalls(1L);
                serviceReferenceMetric.setBusinessTransactionErrorDurationSum(duration);
            } else {
                serviceReferenceMetric.setBusinessTransactionCalls(1L);
                serviceReferenceMetric.setBusinessTransactionDurationSum(duration);
            }
        }

        if (SpanLayer.MQ.equals(spanDecorator.getSpanLayer())) {
            if (spanDecorator.getIsError()) {
                serviceReferenceMetric.setMqTransactionErrorCalls(1L);
                serviceReferenceMetric.setMqTransactionErrorDurationSum(duration);
            } else {
                serviceReferenceMetric.setMqTransactionCalls(1L);
                serviceReferenceMetric.setMqTransactionDurationSum(duration);
            }
        }
    }

    @Override public void build() {
        logger.debug("service reference listener build");
        Graph<ServiceReferenceMetric> graph = GraphManager.INSTANCE.createIfAbsent(TraceStreamGraph.SERVICE_REFERENCE_GRAPH_ID, ServiceReferenceMetric.class);
        entryReferenceMetric.forEach(serviceReferenceMetric -> {
            String id = timeBucket + Const.ID_SPLIT + serviceReferenceMetric.getEntryServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getFrontServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getBehindServiceId();

            serviceReferenceMetric.setId(id);
            serviceReferenceMetric.setTimeBucket(timeBucket);
            logger.debug("push to service reference aggregation worker, id: {}", serviceReferenceMetric.getId());

            graph.start(serviceReferenceMetric);
        });

        exitReferenceMetric.forEach(serviceReferenceMetric -> {
            serviceReferenceMetric.setEntryInstanceId(Const.NONE_INSTANCE_ID);
            if (ObjectUtils.isNotEmpty(entrySpanDecorator)) {
                serviceReferenceMetric.setEntryServiceId(entrySpanDecorator.getOperationNameId());
                serviceReferenceMetric.setFrontServiceId(entrySpanDecorator.getOperationNameId());
            } else {
                serviceReferenceMetric.setEntryServiceId(Const.NONE_SERVICE_ID);
                serviceReferenceMetric.setFrontServiceId(Const.NONE_SERVICE_ID);
            }

            String id = timeBucket + Const.ID_SPLIT + serviceReferenceMetric.getEntryServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getFrontServiceId() + Const.ID_SPLIT + serviceReferenceMetric.getBehindServiceId();
            serviceReferenceMetric.setId(id);
            serviceReferenceMetric.setTimeBucket(timeBucket);

            graph.start(serviceReferenceMetric);
        });
    }
}
