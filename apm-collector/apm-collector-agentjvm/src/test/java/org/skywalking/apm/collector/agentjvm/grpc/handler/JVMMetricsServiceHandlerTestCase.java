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

package org.skywalking.apm.collector.agentjvm.grpc.handler;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.skywalking.apm.network.proto.CPU;
import org.skywalking.apm.network.proto.GC;
import org.skywalking.apm.network.proto.GCPhrase;
import org.skywalking.apm.network.proto.JVMMetric;
import org.skywalking.apm.network.proto.JVMMetrics;
import org.skywalking.apm.network.proto.JVMMetricsServiceGrpc;
import org.skywalking.apm.network.proto.Memory;
import org.skywalking.apm.network.proto.MemoryPool;
import org.skywalking.apm.network.proto.PoolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class JVMMetricsServiceHandlerTestCase {

    private final Logger logger = LoggerFactory.getLogger(JVMMetricsServiceHandlerTestCase.class);

    private static JVMMetricsServiceGrpc.JVMMetricsServiceBlockingStub STUB;

    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();
        STUB = JVMMetricsServiceGrpc.newBlockingStub(channel);

        final long timeInterval = 1;
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> multiInstanceJvmSend(), 1, timeInterval, TimeUnit.SECONDS);
    }

    public static void multiInstanceJvmSend() {
        buildJvmMetric(2);
        buildJvmMetric(3);
    }

    private static void buildJvmMetric(int instanceId) {
        JVMMetrics.Builder jvmMetricsBuilder = JVMMetrics.newBuilder();
        jvmMetricsBuilder.setApplicationInstanceId(instanceId);

        JVMMetric.Builder jvmMetric = JVMMetric.newBuilder();
        jvmMetric.setTime(System.currentTimeMillis());
        buildCpuMetric(jvmMetric);
        buildMemoryMetric(jvmMetric);
        buildMemoryPoolMetric(jvmMetric);
        buildGcMetric(jvmMetric);

        jvmMetricsBuilder.addMetrics(jvmMetric.build());
        STUB.collect(jvmMetricsBuilder.build());
    }

    private static void buildCpuMetric(JVMMetric.Builder jvmMetric) {
        CPU.Builder cpuBuilder = CPU.newBuilder();
        cpuBuilder.setUsagePercent(70);
        jvmMetric.setCpu(cpuBuilder);
    }

    private static void buildMemoryMetric(JVMMetric.Builder jvmMetric) {
        Memory.Builder builderHeap = Memory.newBuilder();
        builderHeap.setIsHeap(true);
        builderHeap.setInit(20);
        builderHeap.setMax(100);
        builderHeap.setUsed(50);
        builderHeap.setCommitted(30);
        jvmMetric.addMemory(builderHeap.build());

        Memory.Builder builderNonHeap = Memory.newBuilder();
        builderNonHeap.setIsHeap(false);
        builderNonHeap.setInit(200);
        builderNonHeap.setMax(1000);
        builderNonHeap.setUsed(500);
        builderNonHeap.setCommitted(300);
        jvmMetric.addMemory(builderNonHeap.build());
    }

    private static void buildMemoryPoolMetric(JVMMetric.Builder jvmMetric) {
        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.NEWGEN_USAGE, true).build());
        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.NEWGEN_USAGE, false).build());

        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.OLDGEN_USAGE, true).build());
        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.OLDGEN_USAGE, false).build());

        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.METASPACE_USAGE, true).build());
        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.METASPACE_USAGE, false).build());

        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.PERMGEN_USAGE, true).build());
        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.PERMGEN_USAGE, false).build());

        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.SURVIVOR_USAGE, true).build());
        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.SURVIVOR_USAGE, false).build());

        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.CODE_CACHE_USAGE, true).build());
        jvmMetric.addMemoryPool(buildMemoryPoolMetric(PoolType.CODE_CACHE_USAGE, false).build());
    }

    private static MemoryPool.Builder buildMemoryPoolMetric(PoolType poolType, boolean isHeap) {
        MemoryPool.Builder builder = MemoryPool.newBuilder();
        builder.setType(poolType);
        builder.setInit(20);
        builder.setMax(100);
        builder.setUsed(50);
        builder.setCommited(30);
        return builder;
    }

    private static void buildGcMetric(JVMMetric.Builder jvmMetric) {
        GC.Builder newGcBuilder = GC.newBuilder();
        newGcBuilder.setPhrase(GCPhrase.NEW);
        newGcBuilder.setCount(2);
        newGcBuilder.setTime(100);
        jvmMetric.addGc(newGcBuilder.build());

        GC.Builder oldGcBuilder = GC.newBuilder();
        oldGcBuilder.setPhrase(GCPhrase.OLD);
        oldGcBuilder.setCount(2);
        oldGcBuilder.setTime(100);
        jvmMetric.addGc(oldGcBuilder.build());
    }
}
