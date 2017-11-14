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

package org.skywalking.apm.collector.agent.grpc.handler;

import io.grpc.stub.StreamObserver;
import java.util.List;
import org.skywalking.apm.collector.agent.stream.graph.JvmMetricStreamGraph;
import org.skywalking.apm.collector.core.graph.Graph;
import org.skywalking.apm.collector.core.graph.GraphManager;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.collector.storage.table.jvm.CpuMetric;
import org.skywalking.apm.collector.storage.table.jvm.GCMetric;
import org.skywalking.apm.collector.storage.table.jvm.MemoryMetric;
import org.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.skywalking.apm.collector.storage.table.register.Instance;
import org.skywalking.apm.network.proto.CPU;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.GC;
import org.skywalking.apm.network.proto.JVMMetrics;
import org.skywalking.apm.network.proto.JVMMetricsServiceGrpc;
import org.skywalking.apm.network.proto.Memory;
import org.skywalking.apm.network.proto.MemoryPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class JVMMetricsServiceHandler extends JVMMetricsServiceGrpc.JVMMetricsServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(JVMMetricsServiceHandler.class);

    private final Graph<MemoryMetric> memoryMetricGraph;
    private final Graph<MemoryPoolMetric> memoryPoolMetricGraph;
    private final Graph<GCMetric> gcMetricGraph;
    private final Graph<CpuMetric> cpuMetricGraph;
    private final Graph<Instance> heartBeatGraph;

    public JVMMetricsServiceHandler() {
        memoryMetricGraph = GraphManager.INSTANCE.createIfAbsent(JvmMetricStreamGraph.MEMORY_METRIC_GRAPH_ID, MemoryMetric.class);
        memoryPoolMetricGraph = GraphManager.INSTANCE.createIfAbsent(JvmMetricStreamGraph.MEMORY_POOL_METRIC_GRAPH_ID, MemoryPoolMetric.class);
        gcMetricGraph = GraphManager.INSTANCE.createIfAbsent(JvmMetricStreamGraph.GC_METRIC_GRAPH_ID, GCMetric.class);
        cpuMetricGraph = GraphManager.INSTANCE.createIfAbsent(JvmMetricStreamGraph.CPU_METRIC_GRAPH_ID, CpuMetric.class);
        heartBeatGraph = GraphManager.INSTANCE.createIfAbsent(JvmMetricStreamGraph.INST_HEART_BEAT_GRAPH_ID, Instance.class);
    }

    @Override public void collect(JVMMetrics request, StreamObserver<Downstream> responseObserver) {
        int instanceId = request.getApplicationInstanceId();
        logger.debug("receive the jvm metric from application instance, id: {}", instanceId);

        request.getMetricsList().forEach(metric -> {
            long time = TimeBucketUtils.INSTANCE.getSecondTimeBucket(metric.getTime());
            sendToInstanceHeartBeatPersistenceWorker(instanceId, metric.getTime());
            sendToCpuMetricPersistenceWorker(instanceId, time, metric.getCpu());
            sendToMemoryMetricPersistenceWorker(instanceId, time, metric.getMemoryList());
            sendToMemoryPoolMetricPersistenceWorker(instanceId, time, metric.getMemoryPoolList());
            sendToGCMetricPersistenceWorker(instanceId, time, metric.getGcList());
        });

        responseObserver.onNext(Downstream.newBuilder().build());
        responseObserver.onCompleted();
    }

    private void sendToInstanceHeartBeatPersistenceWorker(int instanceId, long heartBeatTime) {
        Instance instance = new Instance(String.valueOf(instanceId));
        instance.setHeartBeatTime(TimeBucketUtils.INSTANCE.getSecondTimeBucket(heartBeatTime));
        instance.setInstanceId(instanceId);

        logger.debug("send to instance heart beat persistence worker, id: {}", instance.getId());
        heartBeatGraph.start(instance);
    }

    private void sendToCpuMetricPersistenceWorker(int instanceId, long timeBucket, CPU cpu) {
        CpuMetric cpuMetric = new CpuMetric(timeBucket + Const.ID_SPLIT + instanceId);
        cpuMetric.setInstanceId(instanceId);
        cpuMetric.setUsagePercent(cpu.getUsagePercent());
        cpuMetric.setTimeBucket(timeBucket);

        logger.debug("send to cpu metric graph, id: {}", cpuMetric.getId());
        cpuMetricGraph.start(cpuMetric);
    }

    private void sendToMemoryMetricPersistenceWorker(int instanceId, long timeBucket, List<Memory> memories) {
        memories.forEach(memory -> {
            MemoryMetric memoryMetric = new MemoryMetric(timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + String.valueOf(memory.getIsHeap()));
            memoryMetric.setInstanceId(instanceId);
            memoryMetric.setIsHeap(memory.getIsHeap());
            memoryMetric.setInit(memory.getInit());
            memoryMetric.setMax(memory.getMax());
            memoryMetric.setUsed(memory.getUsed());
            memoryMetric.setCommitted(memory.getCommitted());
            memoryMetric.setTimeBucket(timeBucket);

            logger.debug("send to memory metric graph, id: {}", memoryMetric.getId());
            memoryMetricGraph.start(memoryMetric);
        });
    }

    private void sendToMemoryPoolMetricPersistenceWorker(int instanceId, long timeBucket,
        List<MemoryPool> memoryPools) {

        memoryPools.forEach(memoryPool -> {
            MemoryPoolMetric memoryPoolMetric = new MemoryPoolMetric(timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + String.valueOf(memoryPool.getType().getNumber()));
            memoryPoolMetric.setInstanceId(instanceId);
            memoryPoolMetric.setPoolType(memoryPool.getType().getNumber());
            memoryPoolMetric.setInit(memoryPool.getInit());
            memoryPoolMetric.setMax(memoryPool.getMax());
            memoryPoolMetric.setUsed(memoryPool.getUsed());
            memoryPoolMetric.setCommitted(memoryPool.getCommited());
            memoryPoolMetric.setTimeBucket(timeBucket);

            logger.debug("send to memory pool metric graph, id: {}", memoryPoolMetric.getId());
            memoryPoolMetricGraph.start(memoryPoolMetric);
        });
    }

    private void sendToGCMetricPersistenceWorker(int instanceId, long timeBucket, List<GC> gcs) {
        gcs.forEach(gc -> {
            GCMetric gcMetric = new GCMetric(timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + String.valueOf(gc.getPhraseValue()));
            gcMetric.setInstanceId(instanceId);
            gcMetric.setPhrase(gc.getPhraseValue());
            gcMetric.setCount(gc.getCount());
            gcMetric.setTime(gc.getTime());
            gcMetric.setTimeBucket(timeBucket);

            logger.debug("send to gc metric graph, id: {}", gcMetric.getId());
            gcMetricGraph.start(gcMetric);
        });
    }
}
