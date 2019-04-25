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

import java.util.*;
import org.apache.skywalking.apm.network.common.CPU;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.source.GCPhrase;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class JVMSourceDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(JVMSourceDispatcher.class);
    private final SourceReceiver sourceReceiver;
    private final ServiceInstanceInventoryCache instanceInventoryCache;

    public JVMSourceDispatcher(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        instanceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class);
    }

    void sendMetric(int serviceInstanceId, long minuteTimeBucket, JVMMetric metric) {
        ServiceInstanceInventory serviceInstanceInventory = instanceInventoryCache.get(serviceInstanceId);
        int serviceId;
        if (Objects.nonNull(serviceInstanceInventory)) {
            serviceId = serviceInstanceInventory.getServiceId();
        } else {
            logger.warn("Can't found service by service instance id from cache, service instance id is: {}", serviceInstanceId);
            return;
        }

        this.sendToCpuMetricProcess(serviceId, serviceInstanceId, minuteTimeBucket, metric.getCpu());
        this.sendToMemoryMetricProcess(serviceId, serviceInstanceId, minuteTimeBucket, metric.getMemoryList());
        this.sendToMemoryPoolMetricProcess(serviceId, serviceInstanceId, minuteTimeBucket, metric.getMemoryPoolList());
        this.sendToGCMetricProcess(serviceId, serviceInstanceId, minuteTimeBucket, metric.getGcList());
    }

    private void sendToCpuMetricProcess(int serviceId, int serviceInstanceId, long timeBucket, CPU cpu) {
        ServiceInstanceJVMCPU serviceInstanceJVMCPU = new ServiceInstanceJVMCPU();
        serviceInstanceJVMCPU.setId(serviceInstanceId);
        serviceInstanceJVMCPU.setName(Const.EMPTY_STRING);
        serviceInstanceJVMCPU.setServiceId(serviceId);
        serviceInstanceJVMCPU.setServiceName(Const.EMPTY_STRING);
        serviceInstanceJVMCPU.setUsePercent(cpu.getUsagePercent());
        serviceInstanceJVMCPU.setTimeBucket(timeBucket);
        sourceReceiver.receive(serviceInstanceJVMCPU);
    }

    private void sendToGCMetricProcess(int serviceId, int serviceInstanceId, long timeBucket, List<GC> gcs) {
        gcs.forEach(gc -> {
            ServiceInstanceJVMGC serviceInstanceJVMGC = new ServiceInstanceJVMGC();
            serviceInstanceJVMGC.setId(serviceInstanceId);
            serviceInstanceJVMGC.setName(Const.EMPTY_STRING);
            serviceInstanceJVMGC.setServiceId(serviceId);
            serviceInstanceJVMGC.setServiceName(Const.EMPTY_STRING);

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

    private void sendToMemoryMetricProcess(int serviceId, int serviceInstanceId, long timeBucket,
        List<Memory> memories) {
        memories.forEach(memory -> {
            ServiceInstanceJVMMemory serviceInstanceJVMMemory = new ServiceInstanceJVMMemory();
            serviceInstanceJVMMemory.setId(serviceInstanceId);
            serviceInstanceJVMMemory.setName(Const.EMPTY_STRING);
            serviceInstanceJVMMemory.setServiceId(serviceId);
            serviceInstanceJVMMemory.setServiceName(Const.EMPTY_STRING);
            serviceInstanceJVMMemory.setHeapStatus(memory.getIsHeap());
            serviceInstanceJVMMemory.setInit(memory.getInit());
            serviceInstanceJVMMemory.setMax(memory.getMax());
            serviceInstanceJVMMemory.setUsed(memory.getUsed());
            serviceInstanceJVMMemory.setCommitted(memory.getCommitted());
            serviceInstanceJVMMemory.setTimeBucket(timeBucket);
            sourceReceiver.receive(serviceInstanceJVMMemory);
        });
    }

    private void sendToMemoryPoolMetricProcess(int serviceId, int serviceInstanceId, long timeBucket,
        List<MemoryPool> memoryPools) {

        memoryPools.forEach(memoryPool -> {
            ServiceInstanceJVMMemoryPool serviceInstanceJVMMemoryPool = new ServiceInstanceJVMMemoryPool();
            serviceInstanceJVMMemoryPool.setId(serviceInstanceId);
            serviceInstanceJVMMemoryPool.setName(Const.EMPTY_STRING);
            serviceInstanceJVMMemoryPool.setServiceId(serviceId);
            serviceInstanceJVMMemoryPool.setServiceName(Const.EMPTY_STRING);

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
            serviceInstanceJVMMemoryPool.setCommitted(memoryPool.getCommited());
            serviceInstanceJVMMemoryPool.setTimeBucket(timeBucket);
            sourceReceiver.receive(serviceInstanceJVMMemoryPool);
        });
    }
}
