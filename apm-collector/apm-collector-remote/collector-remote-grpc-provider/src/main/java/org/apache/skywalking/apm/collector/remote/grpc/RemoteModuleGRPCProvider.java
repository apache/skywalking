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

package org.apache.skywalking.apm.collector.remote.grpc;

import org.apache.skywalking.apm.collector.cluster.ClusterModule;
import org.apache.skywalking.apm.collector.cluster.service.ModuleListenerService;
import org.apache.skywalking.apm.collector.cluster.service.ModuleRegisterService;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.core.module.ModuleDefine;
import org.apache.skywalking.apm.collector.grpc.manager.GRPCManagerModule;
import org.apache.skywalking.apm.collector.grpc.manager.service.GRPCManagerService;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.grpc.handler.RemoteCommonServiceHandler;
import org.apache.skywalking.apm.collector.remote.grpc.service.GRPCRemoteSenderService;
import org.apache.skywalking.apm.collector.remote.service.CommonRemoteDataRegisterService;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.apache.skywalking.apm.collector.server.grpc.GRPCServer;

/**
 * @author peng-yongsheng
 */
public class RemoteModuleGRPCProvider extends ModuleProvider {

    private final RemoteModuleGRPCConfig config;
    public static final String NAME = "gRPC";

    private GRPCRemoteSenderService remoteSenderService;
    private CommonRemoteDataRegisterService remoteDataRegisterService;

    public RemoteModuleGRPCProvider() {
        super();
        this.config = new RemoteModuleGRPCConfig();
    }

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends ModuleDefine> module() {
        return RemoteModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        Integer channelSize = config.getChannelSize() == 0 ? 5 : config.getChannelSize();
        Integer bufferSize = config.getBufferSize() == 0 ? 1000 : config.getBufferSize();

        remoteDataRegisterService = new CommonRemoteDataRegisterService();
        remoteSenderService = new GRPCRemoteSenderService(config.getHost(), config.getPort(), channelSize, bufferSize, remoteDataRegisterService);
        this.registerServiceImplementation(RemoteSenderService.class, remoteSenderService);
        this.registerServiceImplementation(RemoteDataRegisterService.class, remoteDataRegisterService);
    }

    @Override public void start() throws ServiceNotProvidedException {
        GRPCManagerService managerService = getManager().find(GRPCManagerModule.NAME).getService(GRPCManagerService.class);
        GRPCServer gRPCServer = managerService.createIfAbsent(config.getHost(), config.getPort());
        gRPCServer.addHandler(new RemoteCommonServiceHandler(remoteDataRegisterService));

        ModuleRegisterService moduleRegisterService = getManager().find(ClusterModule.NAME).getService(ModuleRegisterService.class);
        moduleRegisterService.register(RemoteModule.NAME, this.name(), new RemoteModuleGRPCRegistration(config.getHost(), config.getPort()));

        ModuleListenerService moduleListenerService = getManager().find(ClusterModule.NAME).getService(ModuleListenerService.class);
        moduleListenerService.addListener(remoteSenderService);
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override public String[] requiredModules() {
        return new String[] {ClusterModule.NAME, GRPCManagerModule.NAME};
    }
}
