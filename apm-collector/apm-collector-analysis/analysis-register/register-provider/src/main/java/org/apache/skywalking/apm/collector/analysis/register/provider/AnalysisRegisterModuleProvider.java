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

package org.apache.skywalking.apm.collector.analysis.register.provider;

import org.apache.skywalking.apm.collector.analysis.register.define.AnalysisRegisterModule;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IApplicationIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IInstanceIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.INetworkAddressIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IServiceNameService;
import org.apache.skywalking.apm.collector.analysis.register.provider.register.ApplicationRegisterGraph;
import org.apache.skywalking.apm.collector.analysis.register.provider.register.InstanceRegisterGraph;
import org.apache.skywalking.apm.collector.analysis.register.provider.register.NetworkAddressRegisterGraph;
import org.apache.skywalking.apm.collector.analysis.register.provider.register.ServiceNameRegisterGraph;
import org.apache.skywalking.apm.collector.analysis.register.provider.service.ApplicationIDService;
import org.apache.skywalking.apm.collector.analysis.register.provider.service.InstanceIDService;
import org.apache.skywalking.apm.collector.analysis.register.provider.service.NetworkAddressIDService;
import org.apache.skywalking.apm.collector.analysis.register.provider.service.ServiceNameService;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.analysis.worker.timer.PersistenceTimer;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;

/**
 * @author peng-yongsheng
 */
public class AnalysisRegisterModuleProvider extends ModuleProvider {

    public static final String NAME = "default";
    private final AnalysisRegisterModuleConfig config;

    public AnalysisRegisterModuleProvider() {
        super();
        this.config = new AnalysisRegisterModuleConfig();
    }

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends Module> module() {
        return AnalysisRegisterModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IApplicationIDService.class, new ApplicationIDService(getManager()));
        this.registerServiceImplementation(IInstanceIDService.class, new InstanceIDService(getManager()));
        this.registerServiceImplementation(IServiceNameService.class, new ServiceNameService(getManager()));
        this.registerServiceImplementation(INetworkAddressIDService.class, new NetworkAddressIDService(getManager()));
    }

    @Override public void start() {
        WorkerCreateListener workerCreateListener = new WorkerCreateListener();

        graphCreate(workerCreateListener);

        registerRemoteData();

        PersistenceTimer.INSTANCE.start(getManager(), workerCreateListener.getPersistenceWorkers());
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override public String[] requiredModules() {
        return new String[] {StorageModule.NAME, RemoteModule.NAME, CacheModule.NAME};
    }

    private void graphCreate(WorkerCreateListener workerCreateListener) {
        ApplicationRegisterGraph applicationRegisterGraph = new ApplicationRegisterGraph(getManager(), workerCreateListener);
        applicationRegisterGraph.create();

        InstanceRegisterGraph instanceRegisterGraph = new InstanceRegisterGraph(getManager(), workerCreateListener);
        instanceRegisterGraph.create();

        ServiceNameRegisterGraph serviceNameRegisterGraph = new ServiceNameRegisterGraph(getManager(), workerCreateListener);
        serviceNameRegisterGraph.create();

        NetworkAddressRegisterGraph networkAddressRegisterGraph = new NetworkAddressRegisterGraph(getManager(), workerCreateListener);
        networkAddressRegisterGraph.create();
    }

    private void registerRemoteData() {
        RemoteDataRegisterService remoteDataRegisterService = getManager().find(RemoteModule.NAME).getService(RemoteDataRegisterService.class);
        remoteDataRegisterService.register(Application.class, new Application.InstanceCreator());
        remoteDataRegisterService.register(Instance.class, new Instance.InstanceCreator());
        remoteDataRegisterService.register(NetworkAddress.class, new NetworkAddress.InstanceCreator());
        remoteDataRegisterService.register(ServiceName.class, new ServiceName.InstanceCreator());
    }
}
