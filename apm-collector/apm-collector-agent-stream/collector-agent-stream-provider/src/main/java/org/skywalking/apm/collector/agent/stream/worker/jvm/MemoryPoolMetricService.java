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

package org.skywalking.apm.collector.agent.stream.worker.jvm;

import org.skywalking.apm.collector.agent.stream.graph.JvmMetricStreamGraph;
import org.skywalking.apm.collector.agent.stream.service.jvm.IMemoryPoolMetricService;
import org.skywalking.apm.collector.core.graph.Graph;
import org.skywalking.apm.collector.core.graph.GraphManager;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class MemoryPoolMetricService implements IMemoryPoolMetricService {

    private final Logger logger = LoggerFactory.getLogger(MemoryPoolMetricService.class);

    private final Graph<MemoryPoolMetric> memoryPoolMetricGraph;

    public MemoryPoolMetricService() {
        this.memoryPoolMetricGraph = GraphManager.INSTANCE.createIfAbsent(JvmMetricStreamGraph.MEMORY_POOL_METRIC_GRAPH_ID, MemoryPoolMetric.class);
    }

    @Override
    public void send(int instanceId, long timeBucket, int poolType, long init, long max, long used, long commited) {
        MemoryPoolMetric memoryPoolMetric = new MemoryPoolMetric(timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + String.valueOf(poolType));
        memoryPoolMetric.setInstanceId(instanceId);
        memoryPoolMetric.setPoolType(poolType);
        memoryPoolMetric.setInit(init);
        memoryPoolMetric.setMax(max);
        memoryPoolMetric.setUsed(used);
        memoryPoolMetric.setCommitted(commited);
        memoryPoolMetric.setTimeBucket(timeBucket);

        logger.debug("send to memory pool metric graph, id: {}", memoryPoolMetric.getId());
        memoryPoolMetricGraph.start(memoryPoolMetric);
    }
}
