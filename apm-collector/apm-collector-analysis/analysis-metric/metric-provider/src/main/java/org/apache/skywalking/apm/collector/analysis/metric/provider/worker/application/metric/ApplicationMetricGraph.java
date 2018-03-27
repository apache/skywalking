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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.metric;

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
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricGraph {

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public ApplicationMetricGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    public void create() {
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ApplicationReferenceMetric> graph = GraphManager.INSTANCE.createIfAbsent(MetricGraphIdDefine.APPLICATION_METRIC_GRAPH_ID, ApplicationReferenceMetric.class);

        Node<ApplicationMetric, ApplicationMetric> remoteNode = graph.addNode(new ApplicationMinuteMetricAggregationWorker.Factory(moduleManager).create(workerCreateListener))
            .addNext(new ApplicationMinuteMetricRemoteWorker.Factory(moduleManager, remoteSenderService, MetricGraphIdDefine.APPLICATION_METRIC_GRAPH_ID).create(workerCreateListener));

        remoteNode.addNext(new ApplicationMinuteMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        remoteNode.addNext(new ApplicationHourMetricTransformNode())
            .addNext(new ApplicationHourMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        remoteNode.addNext(new ApplicationDayMetricTransformNode())
            .addNext(new ApplicationDayMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        remoteNode.addNext(new ApplicationMonthMetricTransformNode())
            .addNext(new ApplicationMonthMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        link(graph);
    }

    private void link(Graph<ApplicationReferenceMetric> graph) {
        GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.APPLICATION_REFERENCE_METRIC_GRAPH_ID, ApplicationReferenceMetric.class)
            .toFinder().findNode(MetricWorkerIdDefine.APPLICATION_REFERENCE_MINUTE_METRIC_AGGREGATION_WORKER_ID, ApplicationReferenceMetric.class)
            .addNext(new NodeProcessor<ApplicationReferenceMetric, ApplicationReferenceMetric>() {

                @Override public int id() {
                    return MetricWorkerIdDefine.APPLICATION_METRIC_GRAPH_BRIDGE_WORKER_ID;
                }

                @Override
                public void process(ApplicationReferenceMetric applicationReferenceMetric,
                    Next<ApplicationReferenceMetric> next) {
                    graph.start(applicationReferenceMetric);
                }
            });
    }
}
