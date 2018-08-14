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
import org.apache.skywalking.apm.collector.analysis.register.define.service.*;
import org.apache.skywalking.apm.collector.analysis.register.provider.register.*;
import org.apache.skywalking.apm.collector.analysis.register.provider.service.*;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.analysis.worker.timer.PersistenceTimer;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.table.register.*;

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

    @Override public Class<? extends ModuleDefine> module() {
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
        new ApplicationRegisterGraph(getManager(), workerCreateListener).create();
        new InstanceRegisterGraph(getManager(), workerCreateListener).create();
        new ServiceNameRegisterGraph(getManager(), workerCreateListener).create();
        new NetworkAddressRegisterGraph(getManager(), workerCreateListener).create();
    }

    private void registerRemoteData() {
        RemoteDataRegisterService remoteDataRegisterService = getManager().find(RemoteModule.NAME).getService(RemoteDataRegisterService.class);
        remoteDataRegisterService.register(Application.class, new Application.InstanceCreator());
        remoteDataRegisterService.register(Instance.class, new Instance.InstanceCreator());
        remoteDataRegisterService.register(NetworkAddress.class, new NetworkAddress.InstanceCreator());
        remoteDataRegisterService.register(ServiceName.class, new ServiceName.InstanceCreator());
    }
}
