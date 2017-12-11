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


package org.apache.skywalking.apm.collector.agent.stream.graph;

import org.apache.skywalking.apm.collector.agent.stream.service.graph.JvmMetricStreamGraphDefine;
import org.apache.skywalking.apm.collector.agent.stream.worker.jvm.CpuMetricPersistenceWorker;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.queue.QueueModule;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.apache.skywalking.apm.collector.stream.worker.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.agent.stream.worker.jvm.GCMetricPersistenceWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.jvm.InstHeartBeatPersistenceWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.jvm.MemoryMetricPersistenceWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.jvm.MemoryPoolMetricPersistenceWorker;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.storage.table.jvm.CpuMetric;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;

/**
 * @author peng-yongsheng
 */
public class JvmMetricStreamGraph {

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public JvmMetricStreamGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    @SuppressWarnings("unchecked")
    public void createGcMetricGraph() {
        QueueCreatorService<GCMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<GCMetric> graph = GraphManager.INSTANCE.createIfAbsent(JvmMetricStreamGraphDefine.GC_METRIC_GRAPH_ID, GCMetric.class);
        graph.addNode(new GCMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createCpuMetricGraph() {
        QueueCreatorService<CpuMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<CpuMetric> graph = GraphManager.INSTANCE.createIfAbsent(JvmMetricStreamGraphDefine.CPU_METRIC_GRAPH_ID, CpuMetric.class);
        graph.addNode(new CpuMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createMemoryMetricGraph() {
        QueueCreatorService<MemoryMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<MemoryMetric> graph = GraphManager.INSTANCE.createIfAbsent(JvmMetricStreamGraphDefine.MEMORY_METRIC_GRAPH_ID, MemoryMetric.class);
        graph.addNode(new MemoryMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createMemoryPoolMetricGraph() {
        QueueCreatorService<MemoryPoolMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<MemoryPoolMetric> graph = GraphManager.INSTANCE.createIfAbsent(JvmMetricStreamGraphDefine.MEMORY_POOL_METRIC_GRAPH_ID, MemoryPoolMetric.class);
        graph.addNode(new MemoryPoolMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createHeartBeatGraph() {
        QueueCreatorService<Instance> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<Instance> graph = GraphManager.INSTANCE.createIfAbsent(JvmMetricStreamGraphDefine.INST_HEART_BEAT_GRAPH_ID, Instance.class);
        graph.addNode(new InstHeartBeatPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }
}
