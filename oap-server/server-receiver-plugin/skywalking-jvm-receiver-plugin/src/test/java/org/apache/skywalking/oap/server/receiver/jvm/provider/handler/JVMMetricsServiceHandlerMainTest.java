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

package org.apache.skywalking.oap.server.receiver.jvm.provider.handler;

import io.grpc.*;
import java.util.concurrent.*;
import org.apache.skywalking.apm.network.common.CPU;
import org.apache.skywalking.apm.network.language.agent.*;

/**
 * @author peng-yongsheng
 */
public class JVMMetricsServiceHandlerMainTest {

    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();

        JVMMetricsServiceGrpc.JVMMetricsServiceBlockingStub stub = JVMMetricsServiceGrpc.newBlockingStub(channel);

        Executors.newSingleThreadScheduledExecutor().schedule(() -> send(stub), 1, TimeUnit.SECONDS);
    }

    private static void send(JVMMetricsServiceGrpc.JVMMetricsServiceBlockingStub stub) {
        JVMMetrics.Builder jvmMetrics = JVMMetrics.newBuilder();
        jvmMetrics.setApplicationInstanceId(2);

        JVMMetric.Builder jvmMetricBuilder = JVMMetric.newBuilder();
        jvmMetricBuilder.setTime(System.currentTimeMillis());

        buildCPUMetric(jvmMetricBuilder);
        buildGCMetric(jvmMetricBuilder);
        buildMemoryMetric(jvmMetricBuilder);
        buildMemoryPoolMetric(jvmMetricBuilder);

        jvmMetrics.addMetrics(jvmMetricBuilder);

        stub.collect(jvmMetrics.build());
    }

    private static void buildMemoryPoolMetric(JVMMetric.Builder metricBuilder) {
        MemoryPool.Builder codeCache = MemoryPool.newBuilder();
        codeCache.setInit(10);
        codeCache.setMax(100);
        codeCache.setCommited(10);
        codeCache.setUsed(50);
        codeCache.setType(PoolType.CODE_CACHE_USAGE);
        metricBuilder.addMemoryPool(codeCache);

        MemoryPool.Builder newGen = MemoryPool.newBuilder();
        newGen.setInit(10);
        newGen.setMax(100);
        newGen.setCommited(10);
        newGen.setUsed(50);
        newGen.setType(PoolType.NEWGEN_USAGE);
        metricBuilder.addMemoryPool(newGen);

        MemoryPool.Builder oldGen = MemoryPool.newBuilder();
        oldGen.setInit(10);
        oldGen.setMax(100);
        oldGen.setCommited(10);
        oldGen.setUsed(50);
        oldGen.setType(PoolType.OLDGEN_USAGE);
        metricBuilder.addMemoryPool(oldGen);

        MemoryPool.Builder survivor = MemoryPool.newBuilder();
        survivor.setInit(10);
        survivor.setMax(100);
        survivor.setCommited(10);
        survivor.setUsed(50);
        survivor.setType(PoolType.SURVIVOR_USAGE);
        metricBuilder.addMemoryPool(survivor);

        MemoryPool.Builder permGen = MemoryPool.newBuilder();
        permGen.setInit(10);
        permGen.setMax(100);
        permGen.setCommited(10);
        permGen.setUsed(50);
        permGen.setType(PoolType.PERMGEN_USAGE);
        metricBuilder.addMemoryPool(permGen);

        MemoryPool.Builder metaSpace = MemoryPool.newBuilder();
        metaSpace.setInit(10);
        metaSpace.setMax(100);
        metaSpace.setCommited(10);
        metaSpace.setUsed(50);
        metaSpace.setType(PoolType.METASPACE_USAGE);
        metricBuilder.addMemoryPool(metaSpace);
    }

    private static void buildMemoryMetric(JVMMetric.Builder metricBuilder) {
        Memory.Builder isHeap = Memory.newBuilder();
        isHeap.setInit(20);
        isHeap.setMax(100);
        isHeap.setCommitted(20);
        isHeap.setUsed(60);
        isHeap.setIsHeap(true);
        metricBuilder.addMemory(isHeap);

        Memory.Builder nonHeap = Memory.newBuilder();
        nonHeap.setInit(20);
        nonHeap.setMax(100);
        nonHeap.setCommitted(20);
        nonHeap.setUsed(60);
        nonHeap.setIsHeap(false);
        metricBuilder.addMemory(nonHeap);
    }

    private static void buildGCMetric(JVMMetric.Builder metricBuilder) {
        GC.Builder newGC = GC.newBuilder();
        newGC.setPhrase(GCPhrase.NEW);
        newGC.setCount(2);
        newGC.setTime(1000);
        metricBuilder.addGc(newGC);

        GC.Builder oldGC = GC.newBuilder();
        oldGC.setPhrase(GCPhrase.OLD);
        oldGC.setCount(4);
        oldGC.setTime(49);
        metricBuilder.addGc(oldGC);
    }

    private static void buildCPUMetric(JVMMetric.Builder metricBuilder) {
        CPU.Builder cpu = CPU.newBuilder();
        cpu.setUsagePercent(20);
        metricBuilder.setCpu(cpu.build());
    }
}
