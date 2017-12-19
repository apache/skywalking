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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.GraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.WorkerIdDefine;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.graph.Next;
import org.apache.skywalking.apm.collector.core.graph.NodeProcessor;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class ServiceMetricGraph {

    private final ModuleManager moduleManager;

    public ServiceMetricGraph(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public void create() {
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ServiceReferenceMetric> graph = GraphManager.INSTANCE.createIfAbsent(GraphIdDefine.SERVICE_METRIC_GRAPH_ID, ServiceReferenceMetric.class);

        graph.addNode(new ServiceMetricAggregationWorker.Factory(moduleManager).create(null))
            .addNext(new ServiceMetricRemoteWorker.Factory(moduleManager, remoteSenderService, GraphIdDefine.SERVICE_METRIC_GRAPH_ID).create(null))
            .addNext(new ServiceMetricPersistenceWorker.Factory(moduleManager).create(null));

        link(graph);
    }

    private void link(Graph<ServiceReferenceMetric> graph) {
        GraphManager.INSTANCE.findGraph(GraphIdDefine.SERVICE_REFERENCE_METRIC_GRAPH_ID, ServiceReferenceMetric.class)
            .toFinder().findNode(WorkerIdDefine.SERVICE_REFERENCE_METRIC_AGGREGATION_WORKER_ID, ServiceReferenceMetric.class)
            .addNext(new NodeProcessor<ServiceReferenceMetric, ServiceReferenceMetric>() {
                @Override public int id() {
                    return WorkerIdDefine.SERVICE_METRIC_GRAPH_BRIDGE_WORKER_ID;
                }

                @Override
                public void process(ServiceReferenceMetric serviceReferenceMetric, Next<ServiceReferenceMetric> next) {
                    graph.start(serviceReferenceMetric);
                }
            });
    }
}
