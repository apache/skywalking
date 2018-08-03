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

package org.apache.skywalking.oap.server.core;

import org.apache.skywalking.oap.server.core.analysis.indicator.define.*;
import org.apache.skywalking.oap.server.core.analysis.worker.define.*;
import org.apache.skywalking.oap.server.core.cluster.*;
import org.apache.skywalking.oap.server.core.receiver.*;
import org.apache.skywalking.oap.server.core.remote.*;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.server.*;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServer;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class CoreModuleProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(CoreModuleProvider.class);

    private final CoreModuleConfig moduleConfig;
    private GRPCServer grpcServer;
    private JettyServer jettyServer;
    private final IndicatorMapper indicatorMapper;
    private final WorkerMapper workerMapper;

    public CoreModuleProvider() {
        super();
        this.moduleConfig = new CoreModuleConfig();
        this.indicatorMapper = new IndicatorMapper();
        this.workerMapper = new WorkerMapper();
    }

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return CoreModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return moduleConfig;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        grpcServer = new GRPCServer(moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort());
        grpcServer.initialize();

        jettyServer = new JettyServer(moduleConfig.getRestHost(), moduleConfig.getRestPort(), moduleConfig.getRestContextPath());
        jettyServer.initialize();

        this.registerServiceImplementation(GRPCHandlerRegister.class, new GRPCHandlerRegisterImpl(grpcServer));
        this.registerServiceImplementation(JettyHandlerRegister.class, new JettyHandlerRegisterImpl(jettyServer));

        this.registerServiceImplementation(SourceReceiver.class, new SourceReceiverImpl(getManager()));

        this.registerServiceImplementation(IndicatorMapper.class, indicatorMapper);
        this.registerServiceImplementation(WorkerMapper.class, workerMapper);

        this.registerServiceImplementation(RemoteClientManager.class, new RemoteClientManager(getManager()));
        this.registerServiceImplementation(RemoteSenderService.class, new RemoteSenderService(getManager()));
    }

    @Override public void start() throws ModuleStartException {
        grpcServer.addHandler(new RemoteServiceHandler(getManager()));

        try {
            indicatorMapper.load();
            workerMapper.load(getManager());
        } catch (IndicatorDefineLoadException | WorkerDefineLoadException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override public void notifyAfterCompleted() throws ModuleStartException {
        try {
            grpcServer.start();
            jettyServer.start();
        } catch (ServerException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        RemoteInstance gRPCServerInstance = new RemoteInstance(moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort(), true);
        this.getManager().find(ClusterModule.NAME).getService(ClusterRegister.class).registerRemote(gRPCServerInstance);
    }

    @Override
    public String[] requiredModules() {
        return new String[0];
    }
}
