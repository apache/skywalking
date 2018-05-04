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
 */

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.collector.analysis.jvm.define.service.ICpuMetricService;
import org.apache.skywalking.apm.collector.analysis.jvm.define.service.IGCMetricService;
import org.apache.skywalking.apm.collector.analysis.jvm.define.service.IMemoryMetricService;
import org.apache.skywalking.apm.collector.analysis.jvm.define.service.IMemoryPoolMetricService;
import org.apache.skywalking.apm.collector.analysis.metric.define.service.IInstanceHeartBeatService;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.network.proto.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.management.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class JVMMetricsServiceHandlerTest {

    @Mock
    private ICpuMetricService cpuMetricService;
    @Mock
    private IGCMetricService gcMetricService;
    @Mock
    private IMemoryMetricService memoryMetricService;
    @Mock
    private IMemoryPoolMetricService memoryPoolMetricService;
    @Mock
    private IInstanceHeartBeatService instanceHeartBeatService;

    private JVMMetricsServiceHandler jvmMetricsServiceHandler;

    @Before
    public void setUp() {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        jvmMetricsServiceHandler = new JVMMetricsServiceHandler(moduleManager);
        Whitebox.setInternalState(jvmMetricsServiceHandler, "cpuMetricService", cpuMetricService);
        Whitebox.setInternalState(jvmMetricsServiceHandler, "gcMetricService", gcMetricService);
        Whitebox.setInternalState(jvmMetricsServiceHandler, "memoryMetricService", memoryMetricService);
        Whitebox.setInternalState(jvmMetricsServiceHandler, "memoryPoolMetricService", memoryPoolMetricService);
        Whitebox.setInternalState(jvmMetricsServiceHandler, "instanceHeartBeatService", instanceHeartBeatService);
    }


    @Test
    public void collect() {
        List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
        List<GC> gcList = new ArrayList<>();
        for (GarbageCollectorMXBean bean : beans) {
            gcList.add(GC.newBuilder().setPhrase(GCPhrase.NEW)
                    .setCount(10)
                    .setTime(100)
                    .build());
        }
        CPU cpu = CPU.newBuilder()
                .setUsagePercent(80.0d)
                .build();

        List<Memory> memoryList = new LinkedList<Memory>();

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        Memory.Builder heapMemoryBuilder = Memory.newBuilder();
        heapMemoryBuilder.setIsHeap(true);
        heapMemoryBuilder.setInit(heapMemoryUsage.getInit());
        heapMemoryBuilder.setUsed(heapMemoryUsage.getUsed());
        heapMemoryBuilder.setCommitted(heapMemoryUsage.getCommitted());
        heapMemoryBuilder.setMax(heapMemoryUsage.getMax());
        memoryList.add(heapMemoryBuilder.build());

        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        Memory.Builder nonHeapMemoryBuilder = Memory.newBuilder();
        nonHeapMemoryBuilder.setIsHeap(false);
        nonHeapMemoryBuilder.setInit(nonHeapMemoryUsage.getInit());
        nonHeapMemoryBuilder.setUsed(nonHeapMemoryUsage.getUsed());
        nonHeapMemoryBuilder.setCommitted(nonHeapMemoryUsage.getCommitted());
        nonHeapMemoryBuilder.setMax(nonHeapMemoryUsage.getMax());
        memoryList.add(nonHeapMemoryBuilder.build());

        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        List<MemoryPool> poolList = new LinkedList<MemoryPool>();
        for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
            MemoryUsage usage = memoryPoolMXBean.getUsage();
            poolList.add(MemoryPool.newBuilder().setType(PoolType.CODE_CACHE_USAGE)
                    .setInit(usage.getInit())
                    .setMax(usage.getMax())
                    .setCommited(usage.getCommitted())
                    .setUsed(usage.getUsed())
                    .build());
        }

        JVMMetric jvmMetric = JVMMetric.newBuilder()
                .addAllGc(gcList)
                .setCpu(cpu)
                .addAllMemory(memoryList)
                .addAllMemoryPool(poolList)
                .setTime(System.currentTimeMillis())
                .build();
        JVMMetrics request = JVMMetrics.newBuilder()
                .addMetrics(jvmMetric)
                .setApplicationInstanceId(120)
                .build();

        jvmMetricsServiceHandler.collect(request, new StreamObserver<Downstream>() {
            @Override
            public void onNext(Downstream downstream) {
                Assert.assertEquals(downstream.isInitialized(), true);
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        });
    }
}