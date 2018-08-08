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

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler;

import io.grpc.stub.StreamObserver;
import java.util.List;
import org.apache.skywalking.apm.collector.analysis.jvm.define.AnalysisJVMModule;
import org.apache.skywalking.apm.collector.analysis.jvm.define.service.*;
import org.apache.skywalking.apm.collector.analysis.metric.define.AnalysisMetricModule;
import org.apache.skywalking.apm.collector.analysis.metric.define.service.IInstanceHeartBeatService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.apache.skywalking.apm.network.proto.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class JVMMetricsServiceHandler extends JVMMetricsServiceGrpc.JVMMetricsServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(JVMMetricsServiceHandler.class);

    private final ICpuMetricService cpuMetricService;
    private final IGCMetricService gcMetricService;
    private final IMemoryMetricService memoryMetricService;
    private final IMemoryPoolMetricService memoryPoolMetricService;
    private final IInstanceHeartBeatService instanceHeartBeatService;

    public JVMMetricsServiceHandler(ModuleManager moduleManager) {
        this.cpuMetricService = moduleManager.find(AnalysisJVMModule.NAME).getService(ICpuMetricService.class);
        this.gcMetricService = moduleManager.find(AnalysisJVMModule.NAME).getService(IGCMetricService.class);
        this.memoryMetricService = moduleManager.find(AnalysisJVMModule.NAME).getService(IMemoryMetricService.class);
        this.memoryPoolMetricService = moduleManager.find(AnalysisJVMModule.NAME).getService(IMemoryPoolMetricService.class);
        this.instanceHeartBeatService = moduleManager.find(AnalysisMetricModule.NAME).getService(IInstanceHeartBeatService.class);
    }

    @Override public void collect(JVMMetrics request, StreamObserver<Downstream> responseObserver) {
        int instanceId = request.getApplicationInstanceId();

        if (logger.isDebugEnabled()) {
            logger.debug("receive the jvm metric from application instance, id: {}", instanceId);
        }

        request.getMetricsList().forEach(metric -> {
            long minuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(metric.getTime());
            sendToCpuMetricService(instanceId, minuteTimeBucket, metric.getCpu());
            sendToMemoryMetricService(instanceId, minuteTimeBucket, metric.getMemoryList());
            sendToMemoryPoolMetricService(instanceId, minuteTimeBucket, metric.getMemoryPoolList());
            sendToGCMetricService(instanceId, minuteTimeBucket, metric.getGcList());
            sendToInstanceHeartBeatService(instanceId, metric.getTime());
        });

        responseObserver.onNext(Downstream.newBuilder().build());
        responseObserver.onCompleted();
    }

    private void sendToMemoryMetricService(int instanceId, long timeBucket, List<Memory> memories) {
        memories.forEach(memory -> memoryMetricService.send(instanceId, timeBucket, memory.getIsHeap(), memory.getInit(), memory.getMax(), memory.getUsed(), memory.getCommitted()));
    }

    private void sendToMemoryPoolMetricService(int instanceId, long timeBucket,
        List<MemoryPool> memoryPools) {

        memoryPools.forEach(memoryPool -> memoryPoolMetricService.send(instanceId, timeBucket, memoryPool.getType().getNumber(), memoryPool.getInit(), memoryPool.getMax(), memoryPool.getUsed(), memoryPool.getCommited()));
    }

    private void sendToCpuMetricService(int instanceId, long timeBucket, CPU cpu) {
        cpuMetricService.send(instanceId, timeBucket, cpu.getUsagePercent());
    }

    private void sendToGCMetricService(int instanceId, long timeBucket, List<GC> gcs) {
        gcs.forEach(gc -> gcMetricService.send(instanceId, timeBucket, gc.getPhraseValue(), gc.getCount(), gc.getTime()));
    }

    private void sendToInstanceHeartBeatService(int instanceId, long heartBeatTime) {
        instanceHeartBeatService.heartBeat(instanceId, heartBeatTime);
    }
}
