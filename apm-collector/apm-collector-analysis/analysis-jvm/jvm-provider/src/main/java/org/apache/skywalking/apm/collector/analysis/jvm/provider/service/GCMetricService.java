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

package org.apache.skywalking.apm.collector.analysis.jvm.provider.service;

import org.apache.skywalking.apm.collector.analysis.jvm.define.graph.GraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.jvm.define.service.IGCMetricService;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class GCMetricService implements IGCMetricService {

    private static final Logger logger = LoggerFactory.getLogger(GCMetricService.class);

    private Graph<GCMetric> gcMetricGraph;

    private Graph<GCMetric> getGcMetricGraph() {
        if (isNull(gcMetricGraph)) {
            gcMetricGraph = GraphManager.INSTANCE.findGraph(GraphIdDefine.GC_METRIC_PERSISTENCE_GRAPH_ID, GCMetric.class);
        }
        return gcMetricGraph;
    }

    @Override public void send(int instanceId, long timeBucket, int phraseValue, long count, long duration) {
        String metricId = instanceId + Const.ID_SPLIT + String.valueOf(phraseValue);
        String id = timeBucket + Const.ID_SPLIT + metricId;

        GCMetric gcMetric = new GCMetric();
        gcMetric.setId(id);
        gcMetric.setMetricId(metricId);
        gcMetric.setInstanceId(instanceId);
        gcMetric.setPhrase(phraseValue);
        gcMetric.setCount(count);
        gcMetric.setDuration(duration);
        gcMetric.setTimes(1L);
        gcMetric.setTimeBucket(timeBucket);

        if (logger.isDebugEnabled()) {
            logger.debug("push to gc metric graph, id: {}", gcMetric.getId());
        }
        getGcMetricGraph().start(gcMetric);
    }
}
