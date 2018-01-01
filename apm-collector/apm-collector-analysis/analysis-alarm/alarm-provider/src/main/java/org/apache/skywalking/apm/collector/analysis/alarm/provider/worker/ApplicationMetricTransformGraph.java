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

package org.apache.skywalking.apm.collector.analysis.alarm.provider.worker;

import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.graph.Next;
import org.apache.skywalking.apm.collector.core.graph.NodeProcessor;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricTransformGraph {

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public ApplicationMetricTransformGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    public void create() {
        Graph<ApplicationMetric> graph = GraphManager.INSTANCE.createIfAbsent(AlarmGraphIdDefine.APPLICATION_METRIC_TRANSFORM_GRAPH_ID, ApplicationMetric.class);
        graph.addNode(new ApplicationMetricTransformWorker.Factory(moduleManager).create(workerCreateListener));
        link(graph);
    }

    private void link(Graph<ApplicationMetric> graph) {
        GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.APPLICATION_METRIC_GRAPH_ID, ApplicationMetric.class)
            .toFinder().findNode(MetricWorkerIdDefine.APPLICATION_MAPPING_AGGREGATION_WORKER_ID, ApplicationMetric.class)
            .addNext(new NodeProcessor<ApplicationMetric, ApplicationMetric>() {
                @Override public int id() {
                    return AlarmWorkerIdDefine.APPLICATION_METRIC_TRANSFORM_GRAPH_BRIDGE_WORKER_ID;
                }

                @Override public void process(ApplicationMetric applicationMetric,
                    Next<ApplicationMetric> next) {
                    graph.start(applicationMetric);
                }
            });
    }
}
