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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.metric;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.graph.Next;
import org.apache.skywalking.apm.collector.core.graph.Node;
import org.apache.skywalking.apm.collector.core.graph.NodeProcessor;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class InstanceMetricGraph {

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public InstanceMetricGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    public void create() {
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<InstanceReferenceMetric> graph = GraphManager.INSTANCE.createIfAbsent(MetricGraphIdDefine.INSTANCE_METRIC_GRAPH_ID, InstanceReferenceMetric.class);

        Node<InstanceMetric, InstanceMetric> remoteNode = graph.addNode(new InstanceMinuteMetricAggregationWorker.Factory(moduleManager).create(workerCreateListener))
            .addNext(new InstanceMinuteMetricRemoteWorker.Factory(moduleManager, remoteSenderService, MetricGraphIdDefine.INSTANCE_METRIC_GRAPH_ID).create(workerCreateListener));

        remoteNode.addNext(new InstanceMinuteMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        remoteNode.addNext(new InstanceHourMetricTransformNode())
            .addNext(new InstanceHourMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        remoteNode.addNext(new InstanceDayMetricTransformNode())
            .addNext(new InstanceDayMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        remoteNode.addNext(new InstanceMonthMetricTransformNode())
            .addNext(new InstanceMonthMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        link(graph);
    }

    private void link(Graph<InstanceReferenceMetric> graph) {
        GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.INSTANCE_REFERENCE_METRIC_GRAPH_ID, InstanceReferenceMetric.class)
            .toFinder().findNode(MetricWorkerIdDefine.INSTANCE_REFERENCE_MINUTE_METRIC_AGGREGATION_WORKER_ID, InstanceReferenceMetric.class)
            .addNext(new NodeProcessor<InstanceReferenceMetric, InstanceReferenceMetric>() {

                @Override public int id() {
                    return MetricWorkerIdDefine.INSTANCE_METRIC_GRAPH_BRIDGE_WORKER_ID;
                }

                @Override
                public void process(InstanceReferenceMetric instanceReferenceMetric,
                    Next<InstanceReferenceMetric> next) {
                    graph.start(instanceReferenceMetric);
                }
            });
    }
}
