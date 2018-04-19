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
import org.apache.skywalking.apm.collector.analysis.jvm.define.service.IMemoryPoolMetricService;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class MemoryPoolMetricService implements IMemoryPoolMetricService {

    private final Logger logger = LoggerFactory.getLogger(MemoryPoolMetricService.class);

    private Graph<MemoryPoolMetric> memoryPoolMetricGraph;

    private Graph<MemoryPoolMetric> getMemoryPoolMetricGraph() {
        if (isNull(memoryPoolMetricGraph)) {
            this.memoryPoolMetricGraph = GraphManager.INSTANCE.findGraph(GraphIdDefine.MEMORY_POOL_METRIC_PERSISTENCE_GRAPH_ID, MemoryPoolMetric.class);
        }
        return memoryPoolMetricGraph;
    }

    @Override
    public void send(int instanceId, long timeBucket, int poolType, long init, long max, long used, long committed) {
        String metricId = instanceId + Const.ID_SPLIT + String.valueOf(poolType);
        String id = timeBucket + Const.ID_SPLIT + metricId;

        MemoryPoolMetric memoryPoolMetric = new MemoryPoolMetric();
        memoryPoolMetric.setId(id);
        memoryPoolMetric.setMetricId(metricId);
        memoryPoolMetric.setInstanceId(instanceId);
        memoryPoolMetric.setPoolType(poolType);
        memoryPoolMetric.setInit(init);
        memoryPoolMetric.setMax(max);
        memoryPoolMetric.setUsed(used);
        memoryPoolMetric.setCommitted(committed);
        memoryPoolMetric.setTimes(1L);
        memoryPoolMetric.setTimeBucket(timeBucket);

        logger.debug("push to memory pool metric graph, id: {}", memoryPoolMetric.getId());
        getMemoryPoolMetricGraph().start(memoryPoolMetric);
    }
}
