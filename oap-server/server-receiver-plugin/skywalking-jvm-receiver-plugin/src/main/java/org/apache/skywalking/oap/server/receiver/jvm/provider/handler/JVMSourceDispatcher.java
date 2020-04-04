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

import java.util.List;
import org.apache.skywalking.apm.network.common.v3.CPU;
import org.apache.skywalking.apm.network.language.agent.v3.GC;
import org.apache.skywalking.apm.network.language.agent.v3.JVMMetric;
import org.apache.skywalking.apm.network.language.agent.v3.Memory;
import org.apache.skywalking.apm.network.language.agent.v3.MemoryPool;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.source.GCPhrase;
import org.apache.skywalking.oap.server.core.source.MemoryPoolType;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceJVMCPU;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceJVMGC;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceJVMMemory;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceJVMMemoryPool;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JVMSourceDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(JVMSourceDispatcher.class);
    private final SourceReceiver sourceReceiver;

    public JVMSourceDispatcher(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
    }

    void sendMetric(String service, String serviceInstance, JVMMetric metrics) {
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
    }

    private void sendToCpuMetricProcess(String service,
                                        String serviceId,
                                        String serviceInstance,
                                        String serviceInstanceId,
                                        long timeBucket,
                                        CPU cpu) {
        ServiceInstanceJVMCPU serviceInstanceJVMCPU = new ServiceInstanceJVMCPU();
        serviceInstanceJVMCPU.setId(serviceInstanceId);
        serviceInstanceJVMCPU.setName(service);
        serviceInstanceJVMCPU.setServiceId(serviceId);
        serviceInstanceJVMCPU.setServiceName(serviceInstance);
        serviceInstanceJVMCPU.setUsePercent(cpu.getUsagePercent());
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
            serviceInstanceJVMGC.setName(service);
            serviceInstanceJVMGC.setServiceId(serviceId);
            serviceInstanceJVMGC.setServiceName(serviceInstance);

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
            serviceInstanceJVMMemory.setName(service);
            serviceInstanceJVMMemory.setServiceId(serviceId);
            serviceInstanceJVMMemory.setServiceName(serviceInstance);
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
            serviceInstanceJVMMemoryPool.setName(service);
            serviceInstanceJVMMemoryPool.setServiceId(serviceId);
            serviceInstanceJVMMemoryPool.setServiceName(serviceInstance);

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
}
