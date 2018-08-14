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

package org.apache.skywalking.apm.collector.analysis.jvm.provider.worker.gc;

import org.apache.skywalking.apm.collector.analysis.jvm.define.graph.GraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.graph.Node;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetric;

/**
 * @author peng-yongsheng
 */
public class GCMetricPersistenceGraph {

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public GCMetricPersistenceGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    public void create() {
        Node<GCMetric, GCMetric> bridgeNode = GraphManager.INSTANCE.createIfAbsent(GraphIdDefine.GC_METRIC_PERSISTENCE_GRAPH_ID, GCMetric.class)
            .addNode(new GCMetricBridgeNode());

        bridgeNode.addNext(new GCMinuteMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        bridgeNode.addNext(new GCHourMetricTransformNode())
            .addNext(new GCHourMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        bridgeNode.addNext(new GCDayMetricTransformNode())
            .addNext(new GCDayMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        bridgeNode.addNext(new GCMonthMetricTransformNode())
            .addNext(new GCMonthMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));
    }
}
