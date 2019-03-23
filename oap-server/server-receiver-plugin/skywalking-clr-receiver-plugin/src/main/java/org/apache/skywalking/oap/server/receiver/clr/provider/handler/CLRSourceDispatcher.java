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

package org.apache.skywalking.oap.server.receiver.clr.provider.handler;

import org.apache.skywalking.apm.network.common.CPU;
import org.apache.skywalking.apm.network.language.agent.CLRMetric;
import org.apache.skywalking.apm.network.language.agent.ClrGC;
import org.apache.skywalking.apm.network.language.agent.ClrThread;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceCLRCPU;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceCLRGC;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceCLRThread;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author liuhaoyang
 **/
public class CLRSourceDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(CLRSourceDispatcher.class);
    private final SourceReceiver sourceReceiver;
    private final ServiceInstanceInventoryCache instanceInventoryCache;

    public CLRSourceDispatcher(ModuleManager moduleManager) {
        sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        instanceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class);
    }

    void sendMetric(int serviceInstanceId, long minuteTimeBucket, CLRMetric metric) {
        ServiceInstanceInventory serviceInstanceInventory = instanceInventoryCache.get(serviceInstanceId);
        int serviceId;
        if (serviceInstanceInventory == null) {
            serviceId = serviceInstanceInventory.getServiceId();
        } else {
            logger.warn("Can't found service by service instance id from cache, service instance id is: {}", serviceInstanceId);
            return;
        }

        CPU cpu = metric.getCpu();
        ServiceInstanceCLRCPU serviceInstanceCLRCPU = new ServiceInstanceCLRCPU();
        serviceInstanceCLRCPU.setUsePercent(cpu.getUsagePercent());
        serviceInstanceCLRCPU.setTimeBucket(minuteTimeBucket);
        serviceInstanceCLRCPU.setId(serviceInstanceId);
        serviceInstanceCLRCPU.setName(Const.EMPTY_STRING);
        serviceInstanceCLRCPU.setServiceId(serviceId);
        serviceInstanceCLRCPU.setServiceName(Const.EMPTY_STRING);
        sourceReceiver.receive(serviceInstanceCLRCPU);

        ClrGC gc = metric.getGc();
        ServiceInstanceCLRGC serviceInstanceCLRGC = new ServiceInstanceCLRGC();
        serviceInstanceCLRGC.setGen0CollectCount(gc.getGen0CollectCount());
        serviceInstanceCLRGC.setGen1CollectCount(gc.getGen1CollectCount());
        serviceInstanceCLRGC.setGen2CollectCount(gc.getGen2CollectCount());
        serviceInstanceCLRGC.setHeapMemory(gc.getHeapMemory());
        serviceInstanceCLRGC.setTimeBucket(minuteTimeBucket);
        serviceInstanceCLRGC.setId(serviceInstanceId);
        serviceInstanceCLRGC.setName(Const.EMPTY_STRING);
        serviceInstanceCLRGC.setServiceId(serviceId);
        serviceInstanceCLRGC.setServiceName(Const.EMPTY_STRING);
        sourceReceiver.receive(serviceInstanceCLRGC);

        ClrThread thread = metric.getThread();
        ServiceInstanceCLRThread serviceInstanceCLRThread = new ServiceInstanceCLRThread();
        serviceInstanceCLRThread.setAvailableCompletionPortThreads(thread.getAvailableCompletionPortThreads());
        serviceInstanceCLRThread.setAvailableWorkerThreads(thread.getAvailableWorkerThreads());
        serviceInstanceCLRThread.setMaxCompletionPortThreads(thread.getMaxCompletionPortThreads());
        serviceInstanceCLRThread.setMaxWorkerThreads(thread.getMaxWorkerThreads());
        serviceInstanceCLRThread.setTimeBucket(minuteTimeBucket);
        serviceInstanceCLRThread.setId(serviceInstanceId);
        serviceInstanceCLRThread.setName(Const.EMPTY_STRING);
        serviceInstanceCLRThread.setServiceId(serviceId);
        serviceInstanceCLRThread.setServiceName(Const.EMPTY_STRING);
        sourceReceiver.receive(serviceInstanceCLRThread);
    }
}
