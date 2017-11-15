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

package org.skywalking.apm.collector.agent.stream.graph;

import org.skywalking.apm.collector.agent.stream.worker.jvm.CpuMetricPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.jvm.GCMetricPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.jvm.InstHeartBeatPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.jvm.MemoryMetricPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.jvm.MemoryPoolMetricPersistenceWorker;
import org.skywalking.apm.collector.core.graph.Graph;
import org.skywalking.apm.collector.core.graph.GraphManager;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.queue.QueueModule;
import org.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.skywalking.apm.collector.storage.table.jvm.CpuMetric;
import org.skywalking.apm.collector.storage.table.jvm.GCMetric;
import org.skywalking.apm.collector.storage.table.jvm.MemoryMetric;
import org.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.skywalking.apm.collector.storage.table.register.Instance;
import org.skywalking.apm.collector.stream.worker.base.WorkerCreateListener;

/**
 * @author peng-yongsheng
 */
public class JvmMetricStreamGraph {

    public static final int GC_METRIC_GRAPH_ID = 100;
    public static final int MEMORY_METRIC_GRAPH_ID = 101;
    public static final int MEMORY_POOL_METRIC_GRAPH_ID = 102;
    public static final int CPU_METRIC_GRAPH_ID = 103;
    public static final int INST_HEART_BEAT_GRAPH_ID = 104;

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public JvmMetricStreamGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    @SuppressWarnings("unchecked")
    public Graph<GCMetric> createGcMetricGraph() {
        QueueCreatorService<GCMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<GCMetric> graph = GraphManager.INSTANCE.createIfAbsent(GC_METRIC_GRAPH_ID, GCMetric.class);
        graph.addNode(new GCMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<CpuMetric> createCpuMetricGraph() {
        QueueCreatorService<CpuMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<CpuMetric> graph = GraphManager.INSTANCE.createIfAbsent(CPU_METRIC_GRAPH_ID, CpuMetric.class);
        graph.addNode(new CpuMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<MemoryMetric> createMemoryMetricGraph() {
        QueueCreatorService<MemoryMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<MemoryMetric> graph = GraphManager.INSTANCE.createIfAbsent(MEMORY_METRIC_GRAPH_ID, MemoryMetric.class);
        graph.addNode(new MemoryMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<MemoryPoolMetric> createMemoryPoolMetricGraph() {
        QueueCreatorService<MemoryPoolMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<MemoryPoolMetric> graph = GraphManager.INSTANCE.createIfAbsent(MEMORY_POOL_METRIC_GRAPH_ID, MemoryPoolMetric.class);
        graph.addNode(new MemoryPoolMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<Instance> createHeartBeatGraph() {
        QueueCreatorService<Instance> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<Instance> graph = GraphManager.INSTANCE.createIfAbsent(INST_HEART_BEAT_GRAPH_ID, Instance.class);
        graph.addNode(new InstHeartBeatPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }
}
