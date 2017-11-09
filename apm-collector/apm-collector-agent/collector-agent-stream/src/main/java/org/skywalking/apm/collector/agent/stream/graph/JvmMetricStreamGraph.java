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

import org.skywalking.apm.collector.core.graph.Graph;
import org.skywalking.apm.collector.core.graph.GraphManager;
import org.skywalking.apm.collector.storage.table.jvm.CpuMetric;
import org.skywalking.apm.collector.storage.table.jvm.GCMetric;
import org.skywalking.apm.collector.storage.table.jvm.MemoryMetric;
import org.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.skywalking.apm.collector.storage.table.register.Instance;
import org.skywalking.apm.collector.stream.worker.base.ProviderNotFoundException;

/**
 * @author peng-yongsheng
 */
public class JvmMetricStreamGraph {

    public static final int GC_METRIC_GRAPH_ID = 100;
    public static final int MEMORY_METRIC_GRAPH_ID = 101;
    public static final int MEMORY_POOL_METRIC_GRAPH_ID = 102;
    public static final int CPU_METRIC_GRAPH_ID = 103;
    public static final int INST_HEART_BEAT_GRAPH_ID = 104;

    public Graph<GCMetric> createGcMetricGraph() {
        Graph<GCMetric> graph = GraphManager.INSTANCE.createIfAbsent(GC_METRIC_GRAPH_ID, GCMetric.class);
        return graph;
    }

    public Graph<CpuMetric> createCpuMetricGraph() throws ProviderNotFoundException {
        Graph<CpuMetric> graph = GraphManager.INSTANCE.createIfAbsent(CPU_METRIC_GRAPH_ID, CpuMetric.class);
        return graph;
    }

    public Graph<MemoryMetric> createMemoryMetricGraph() {
        Graph<MemoryMetric> graph = GraphManager.INSTANCE.createIfAbsent(MEMORY_METRIC_GRAPH_ID, MemoryMetric.class);
        return graph;
    }

    public Graph<MemoryPoolMetric> createMemoryPoolMetricGraph() {
        Graph<MemoryPoolMetric> graph = GraphManager.INSTANCE.createIfAbsent(MEMORY_POOL_METRIC_GRAPH_ID, MemoryPoolMetric.class);
        return graph;
    }

    public Graph<Instance> createHeartBeatGraph() {
        Graph<Instance> graph = GraphManager.INSTANCE.createIfAbsent(INST_HEART_BEAT_GRAPH_ID, Instance.class);
        return graph;
    }
}
