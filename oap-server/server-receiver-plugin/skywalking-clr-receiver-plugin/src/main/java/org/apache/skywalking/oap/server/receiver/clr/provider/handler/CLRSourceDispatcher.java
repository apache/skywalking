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

import org.apache.skywalking.apm.network.common.v3.CPU;
import org.apache.skywalking.apm.network.language.agent.v3.CLRMetric;
import org.apache.skywalking.apm.network.language.agent.v3.ClrGC;
import org.apache.skywalking.apm.network.language.agent.v3.ClrThread;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceCLRCPU;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceCLRGC;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceCLRThread;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 **/
public class CLRSourceDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CLRSourceDispatcher.class);
    private final SourceReceiver sourceReceiver;

    public CLRSourceDispatcher(ModuleManager moduleManager) {
        sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
    }

    void sendMetric(String service, String serviceInstance, long minuteTimeBucket, CLRMetric metrics) {
        final String serviceId = IDManager.ServiceID.buildId(service, NodeType.Normal);
        final String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, serviceInstance);

        CPU cpu = metrics.getCpu();
        ServiceInstanceCLRCPU serviceInstanceCLRCPU = new ServiceInstanceCLRCPU();
        serviceInstanceCLRCPU.setUsePercent(cpu.getUsagePercent());
        serviceInstanceCLRCPU.setTimeBucket(minuteTimeBucket);
        serviceInstanceCLRCPU.setId(serviceInstanceId);
        serviceInstanceCLRCPU.setName(Const.EMPTY_STRING);
        serviceInstanceCLRCPU.setServiceId(serviceId);
        serviceInstanceCLRCPU.setServiceName(service);
        sourceReceiver.receive(serviceInstanceCLRCPU);

        ClrGC gc = metrics.getGc();
        ServiceInstanceCLRGC serviceInstanceCLRGC = new ServiceInstanceCLRGC();
        serviceInstanceCLRGC.setGen0CollectCount(gc.getGen0CollectCount());
        serviceInstanceCLRGC.setGen1CollectCount(gc.getGen1CollectCount());
        serviceInstanceCLRGC.setGen2CollectCount(gc.getGen2CollectCount());
        serviceInstanceCLRGC.setHeapMemory(gc.getHeapMemory());
        serviceInstanceCLRGC.setTimeBucket(minuteTimeBucket);
        serviceInstanceCLRGC.setId(serviceInstanceId);
        serviceInstanceCLRGC.setName(serviceInstance);
        serviceInstanceCLRGC.setServiceId(serviceId);
        serviceInstanceCLRGC.setServiceName(service);
        sourceReceiver.receive(serviceInstanceCLRGC);

        ClrThread thread = metrics.getThread();
        ServiceInstanceCLRThread serviceInstanceCLRThread = new ServiceInstanceCLRThread();
        serviceInstanceCLRThread.setAvailableCompletionPortThreads(thread.getAvailableCompletionPortThreads());
        serviceInstanceCLRThread.setAvailableWorkerThreads(thread.getAvailableWorkerThreads());
        serviceInstanceCLRThread.setMaxCompletionPortThreads(thread.getMaxCompletionPortThreads());
        serviceInstanceCLRThread.setMaxWorkerThreads(thread.getMaxWorkerThreads());
        serviceInstanceCLRThread.setTimeBucket(minuteTimeBucket);
        serviceInstanceCLRThread.setId(serviceInstanceId);
        serviceInstanceCLRThread.setName(service);
        serviceInstanceCLRThread.setServiceId(serviceId);
        serviceInstanceCLRThread.setServiceName(serviceInstance);
        sourceReceiver.receive(serviceInstanceCLRThread);
    }
}
