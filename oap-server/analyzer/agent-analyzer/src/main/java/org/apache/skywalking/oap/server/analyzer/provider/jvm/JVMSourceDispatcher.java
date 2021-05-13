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

package org.apache.skywalking.oap.server.analyzer.provider.jvm;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.CPU;
import org.apache.skywalking.apm.network.language.agent.v3.GC;
import org.apache.skywalking.apm.network.language.agent.v3.JVMMetric;
import org.apache.skywalking.apm.network.language.agent.v3.Memory;
import org.apache.skywalking.apm.network.language.agent.v3.MemoryPool;
import org.apache.skywalking.apm.network.language.agent.v3.Thread;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.source.GCPhrase;
import org.apache.skywalking.oap.server.core.source.MemoryPoolType;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceJVMCPU;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceJVMGC;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceJVMMemory;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceJVMMemoryPool;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceJVMThread;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
public class JVMSourceDispatcher {
    private final SourceReceiver sourceReceiver;

    public JVMSourceDispatcher(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
    }

    public void sendMetric(String service, String serviceInstance, JVMMetric metrics) {
        long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(metrics.getTime());

        final String serviceId = IDManager.ServiceID.buildId(service, NodeType.Normal);
        final String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, serviceInstance);

        this.sendToCpuMetricProcess(
            service, serviceId, serviceInstance, serviceInstanceId, minuteTimeBucket, metrics.getCpu());
        this.sendToMemoryMetricProcess(
            service, serviceId, serviceInstance, serviceInstanceId, minuteTimeBucket, metrics.getMemoryList());
        this.sendToMemoryPoolMetricProcess(
            service, serviceId, serviceInstance, serviceInstanceId, minuteTimeBucket, metrics.getMemoryPoolList());
        this.sendToGCMetricProcess(
            service, serviceId, serviceInstance, serviceInstanceId, minuteTimeBucket, metrics.getGcList());
        this.sendToThreadMetricProcess(
                service, serviceId, serviceInstance, serviceInstanceId, minuteTimeBucket, metrics.getThread());
    }

    private void sendToCpuMetricProcess(String service,
                                        String serviceId,
                                        String serviceInstance,
                                        String serviceInstanceId,
                                        long timeBucket,
                                        CPU cpu) {
        ServiceInstanceJVMCPU serviceInstanceJVMCPU = new ServiceInstanceJVMCPU();
        serviceInstanceJVMCPU.setId(serviceInstanceId);
        serviceInstanceJVMCPU.setName(serviceInstance);
        serviceInstanceJVMCPU.setServiceId(serviceId);
        serviceInstanceJVMCPU.setServiceName(service);
        // If the cpu usage percent is less than 1, will set to 1
        double adjustedCpuUsagePercent = Math.max(cpu.getUsagePercent(), 1.0);
        serviceInstanceJVMCPU.setUsePercent(adjustedCpuUsagePercent);
        serviceInstanceJVMCPU.setTimeBucket(timeBucket);
        sourceReceiver.receive(serviceInstanceJVMCPU);
    }

    private void sendToGCMetricProcess(String service,
                                       String serviceId,
                                       String serviceInstance,
                                       String serviceInstanceId,
                                       long timeBucket,
                                       List<GC> gcs) {
        gcs.forEach(gc -> {
            ServiceInstanceJVMGC serviceInstanceJVMGC = new ServiceInstanceJVMGC();
            serviceInstanceJVMGC.setId(serviceInstanceId);
            serviceInstanceJVMGC.setName(serviceInstance);
            serviceInstanceJVMGC.setServiceId(serviceId);
            serviceInstanceJVMGC.setServiceName(service);

            switch (gc.getPhrase()) {
                case NEW:
                    serviceInstanceJVMGC.setPhrase(GCPhrase.NEW);
                    break;
                case OLD:
                    serviceInstanceJVMGC.setPhrase(GCPhrase.OLD);
                    break;
            }

            serviceInstanceJVMGC.setTime(gc.getTime());
            serviceInstanceJVMGC.setCount(gc.getCount());
            serviceInstanceJVMGC.setTimeBucket(timeBucket);
            sourceReceiver.receive(serviceInstanceJVMGC);
        });
    }

    private void sendToMemoryMetricProcess(String service,
                                           String serviceId,
                                           String serviceInstance,
                                           String serviceInstanceId,
                                           long timeBucket,
                                           List<Memory> memories) {
        memories.forEach(memory -> {
            ServiceInstanceJVMMemory serviceInstanceJVMMemory = new ServiceInstanceJVMMemory();
            serviceInstanceJVMMemory.setId(serviceInstanceId);
            serviceInstanceJVMMemory.setName(serviceInstance);
            serviceInstanceJVMMemory.setServiceId(serviceId);
            serviceInstanceJVMMemory.setServiceName(service);
            serviceInstanceJVMMemory.setHeapStatus(memory.getIsHeap());
            serviceInstanceJVMMemory.setInit(memory.getInit());
            serviceInstanceJVMMemory.setMax(memory.getMax());
            serviceInstanceJVMMemory.setUsed(memory.getUsed());
            serviceInstanceJVMMemory.setCommitted(memory.getCommitted());
            serviceInstanceJVMMemory.setTimeBucket(timeBucket);
            sourceReceiver.receive(serviceInstanceJVMMemory);
        });
    }

    private void sendToMemoryPoolMetricProcess(String service,
                                               String serviceId,
                                               String serviceInstance,
                                               String serviceInstanceId,
                                               long timeBucket,
                                               List<MemoryPool> memoryPools) {

        memoryPools.forEach(memoryPool -> {
            ServiceInstanceJVMMemoryPool serviceInstanceJVMMemoryPool = new ServiceInstanceJVMMemoryPool();
            serviceInstanceJVMMemoryPool.setId(serviceInstanceId);
            serviceInstanceJVMMemoryPool.setName(serviceInstance);
            serviceInstanceJVMMemoryPool.setServiceId(serviceId);
            serviceInstanceJVMMemoryPool.setServiceName(service);

            switch (memoryPool.getType()) {
                case NEWGEN_USAGE:
                    serviceInstanceJVMMemoryPool.setPoolType(MemoryPoolType.NEWGEN_USAGE);
                    break;
                case OLDGEN_USAGE:
                    serviceInstanceJVMMemoryPool.setPoolType(MemoryPoolType.OLDGEN_USAGE);
                    break;
                case PERMGEN_USAGE:
                    serviceInstanceJVMMemoryPool.setPoolType(MemoryPoolType.PERMGEN_USAGE);
                    break;
                case SURVIVOR_USAGE:
                    serviceInstanceJVMMemoryPool.setPoolType(MemoryPoolType.SURVIVOR_USAGE);
                    break;
                case METASPACE_USAGE:
                    serviceInstanceJVMMemoryPool.setPoolType(MemoryPoolType.METASPACE_USAGE);
                    break;
                case CODE_CACHE_USAGE:
                    serviceInstanceJVMMemoryPool.setPoolType(MemoryPoolType.CODE_CACHE_USAGE);
                    break;
            }

            serviceInstanceJVMMemoryPool.setInit(memoryPool.getInit());
            serviceInstanceJVMMemoryPool.setMax(memoryPool.getMax());
            serviceInstanceJVMMemoryPool.setUsed(memoryPool.getUsed());
            serviceInstanceJVMMemoryPool.setCommitted(memoryPool.getCommitted());
            serviceInstanceJVMMemoryPool.setTimeBucket(timeBucket);
            sourceReceiver.receive(serviceInstanceJVMMemoryPool);
        });
    }

    private void sendToThreadMetricProcess(String service,
            String serviceId,
            String serviceInstance,
            String serviceInstanceId,
            long timeBucket,
            Thread thread) {
        ServiceInstanceJVMThread serviceInstanceJVMThread = new ServiceInstanceJVMThread();
        serviceInstanceJVMThread.setId(serviceInstanceId);
        serviceInstanceJVMThread.setName(serviceInstance);
        serviceInstanceJVMThread.setServiceId(serviceId);
        serviceInstanceJVMThread.setServiceName(service);
        serviceInstanceJVMThread.setLiveCount(thread.getLiveCount());
        serviceInstanceJVMThread.setDaemonCount(thread.getDaemonCount());
        serviceInstanceJVMThread.setPeakCount(thread.getPeakCount());
        serviceInstanceJVMThread.setTimeBucket(timeBucket);
        sourceReceiver.receive(serviceInstanceJVMThread);
    }
}
