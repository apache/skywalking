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


package org.apache.skywalking.apm.collector.agent.stream;

import java.util.Properties;
import org.apache.skywalking.apm.collector.agent.stream.buffer.BufferFileConfig;
import org.apache.skywalking.apm.collector.agent.stream.service.jvm.IGCMetricService;
import org.apache.skywalking.apm.collector.agent.stream.service.jvm.IInstanceHeartBeatService;
import org.apache.skywalking.apm.collector.agent.stream.service.jvm.IMemoryMetricService;
import org.apache.skywalking.apm.collector.agent.stream.service.jvm.IMemoryPoolMetricService;
import org.apache.skywalking.apm.collector.agent.stream.service.register.IApplicationIDService;
import org.apache.skywalking.apm.collector.agent.stream.service.register.IInstanceIDService;
import org.apache.skywalking.apm.collector.agent.stream.service.trace.ITraceSegmentService;
import org.apache.skywalking.apm.collector.agent.stream.worker.AgentStreamRemoteDataRegister;
import org.apache.skywalking.apm.collector.agent.stream.worker.jvm.CpuMetricService;
import org.apache.skywalking.apm.collector.agent.stream.worker.jvm.GCMetricService;
import org.apache.skywalking.apm.collector.agent.stream.worker.jvm.InstanceHeartBeatService;
import org.apache.skywalking.apm.collector.agent.stream.worker.jvm.MemoryMetricService;
import org.apache.skywalking.apm.collector.agent.stream.worker.jvm.MemoryPoolMetricService;
import org.apache.skywalking.apm.collector.agent.stream.worker.register.ApplicationIDService;
import org.apache.skywalking.apm.collector.agent.stream.worker.register.InstanceIDService;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.TraceSegmentService;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;
import org.apache.skywalking.apm.collector.agent.stream.service.jvm.ICpuMetricService;
import org.apache.skywalking.apm.collector.agent.stream.service.register.IServiceNameService;
import org.apache.skywalking.apm.collector.agent.stream.worker.register.ServiceNameService;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.storage.StorageModule;

/**
 * @author peng-yongsheng
 */
public class AgentStreamModuleProvider extends ModuleProvider {

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends Module> module() {
        return AgentStreamModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        this.registerServiceImplementation(IApplicationIDService.class, new ApplicationIDService(getManager()));
        this.registerServiceImplementation(IInstanceIDService.class, new InstanceIDService(getManager()));
        this.registerServiceImplementation(IServiceNameService.class, new ServiceNameService(getManager()));

        this.registerServiceImplementation(ICpuMetricService.class, new CpuMetricService());
        this.registerServiceImplementation(IGCMetricService.class, new GCMetricService());
        this.registerServiceImplementation(IMemoryMetricService.class, new MemoryMetricService());
        this.registerServiceImplementation(IMemoryPoolMetricService.class, new MemoryPoolMetricService());
        this.registerServiceImplementation(IInstanceHeartBeatService.class, new InstanceHeartBeatService());

        this.registerServiceImplementation(ITraceSegmentService.class, new TraceSegmentService(getManager()));

        BufferFileConfig.Parser parser = new BufferFileConfig.Parser();
        parser.parse(config);
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        RemoteDataRegisterService remoteDataRegisterService = getManager().find(RemoteModule.NAME).getService(RemoteDataRegisterService.class);
        AgentStreamRemoteDataRegister agentStreamRemoteDataRegister = new AgentStreamRemoteDataRegister(remoteDataRegisterService);
        agentStreamRemoteDataRegister.register();

        AgentStreamBootStartup bootStartup = new AgentStreamBootStartup(getManager());
        bootStartup.start();
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[] {StorageModule.NAME, CacheModule.NAME, ConfigurationModule.NAME};
    }
}
