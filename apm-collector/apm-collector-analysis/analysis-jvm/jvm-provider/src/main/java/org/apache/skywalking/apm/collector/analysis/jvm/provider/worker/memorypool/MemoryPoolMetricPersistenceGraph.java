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

package org.apache.skywalking.apm.collector.analysis.jvm.provider.worker.memorypool;

import org.apache.skywalking.apm.collector.analysis.jvm.define.graph.GraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.graph.Node;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;

/**
 * @author peng-yongsheng
 */
public class MemoryPoolMetricPersistenceGraph {

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public MemoryPoolMetricPersistenceGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    public void create() {
        Node<MemoryPoolMetric, MemoryPoolMetric> bridgeNode = GraphManager.INSTANCE.createIfAbsent(GraphIdDefine.MEMORY_POOL_METRIC_PERSISTENCE_GRAPH_ID, MemoryPoolMetric.class)
            .addNode(new MemoryPoolMetricBridgeNode());

        bridgeNode.addNext(new MemoryPoolMinuteMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        bridgeNode.addNext(new MemoryPoolHourMetricTransformNode())
            .addNext(new MemoryPoolHourMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        bridgeNode.addNext(new MemoryPoolDayMetricTransformNode())
            .addNext(new MemoryPoolDayMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        bridgeNode.addNext(new MemoryPoolMonthMetricTransformNode())
            .addNext(new MemoryPoolMonthMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));
    }
}
