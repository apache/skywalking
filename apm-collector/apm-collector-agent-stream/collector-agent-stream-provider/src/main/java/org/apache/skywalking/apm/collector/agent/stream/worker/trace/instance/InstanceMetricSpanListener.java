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


package org.apache.skywalking.apm.collector.agent.stream.worker.trace.instance;

import org.apache.skywalking.apm.collector.agent.stream.graph.TraceStreamGraph;
import org.apache.skywalking.apm.collector.agent.stream.parser.EntrySpanListener;
import org.apache.skywalking.apm.collector.agent.stream.parser.FirstSpanListener;
import org.apache.skywalking.apm.collector.agent.stream.parser.standardization.SpanDecorator;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceMetricSpanListener implements EntrySpanListener, FirstSpanListener {

    private final Logger logger = LoggerFactory.getLogger(InstanceMetricSpanListener.class);

    private int applicationId;
    private int instanceId;
    private boolean isError;
    private long duration;
    private long timeBucket;

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        this.applicationId = applicationId;
        this.instanceId = instanceId;
        this.isError = spanDecorator.getIsError();
        this.duration = spanDecorator.getEndTime() - spanDecorator.getStartTime();
        timeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(spanDecorator.getStartTime());
    }

    @Override public void build() {
        InstanceMetric instanceMetric = new InstanceMetric(timeBucket + Const.ID_SPLIT + instanceId);
        instanceMetric.setApplicationId(applicationId);
        instanceMetric.setInstanceId(instanceId);
        instanceMetric.setTransactionCalls(1L);
        instanceMetric.setTransactionDurationSum(duration);

        if (isError) {
            instanceMetric.setTransactionErrorCalls(1L);
            instanceMetric.setTransactionErrorDurationSum(duration);
        }
        instanceMetric.setTimeBucket(timeBucket);

        Graph<InstanceMetric> graph = GraphManager.INSTANCE.createIfAbsent(TraceStreamGraph.INSTANCE_METRIC_GRAPH_ID, InstanceMetric.class);
        graph.start(instanceMetric);
    }
}
