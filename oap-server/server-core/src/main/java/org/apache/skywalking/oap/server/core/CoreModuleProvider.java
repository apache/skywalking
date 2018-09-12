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

import java.io.IOException;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorTypeListener;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.cluster.*;
import org.apache.skywalking.oap.server.core.config.*;
import org.apache.skywalking.oap.server.core.query.TopologyQueryService;
import org.apache.skywalking.oap.server.core.register.annotation.InventoryTypeListener;
import org.apache.skywalking.oap.server.core.register.service.*;
import org.apache.skywalking.oap.server.core.remote.*;
import org.apache.skywalking.oap.server.core.remote.annotation.*;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.server.*;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.core.storage.PersistenceTimer;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageAnnotationListener;
import org.apache.skywalking.oap.server.core.storage.model.IModelGetter;
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
    private RemoteClientManager remoteClientManager;
    private final AnnotationScan annotationScan;
    private final StorageAnnotationListener storageAnnotationListener;
    private final StreamAnnotationListener streamAnnotationListener;
    private final StreamDataAnnotationContainer streamDataAnnotationContainer;

    public CoreModuleProvider() {
        super();
        this.moduleConfig = new CoreModuleConfig();
        this.annotationScan = new AnnotationScan();
        this.storageAnnotationListener = new StorageAnnotationListener();
        this.streamAnnotationListener = new StreamAnnotationListener();
        this.streamDataAnnotationContainer = new StreamDataAnnotationContainer();
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

        this.registerServiceImplementation(IComponentLibraryCatalogService.class, new ComponentLibraryCatalogService());

        this.registerServiceImplementation(SourceReceiver.class, new SourceReceiverImpl());

        this.registerServiceImplementation(StreamDataClassGetter.class, streamDataAnnotationContainer);

        this.registerServiceImplementation(RemoteSenderService.class, new RemoteSenderService(getManager()));
        this.registerServiceImplementation(IModelGetter.class, storageAnnotationListener);

        this.registerServiceImplementation(ServiceInventoryCache.class, new ServiceInventoryCache(getManager()));
        this.registerServiceImplementation(IServiceInventoryRegister.class, new ServiceInventoryRegister(getManager()));

        this.registerServiceImplementation(ServiceInstanceInventoryCache.class, new ServiceInstanceInventoryCache(getManager()));
        this.registerServiceImplementation(IServiceInstanceInventoryRegister.class, new ServiceInstanceInventoryRegister(getManager()));

        this.registerServiceImplementation(EndpointInventoryCache.class, new EndpointInventoryCache(getManager()));
        this.registerServiceImplementation(IEndpointInventoryRegister.class, new EndpointInventoryRegister(getManager()));

        this.registerServiceImplementation(NetworkAddressInventoryCache.class, new NetworkAddressInventoryCache(getManager()));
        this.registerServiceImplementation(INetworkAddressInventoryRegister.class, new NetworkAddressInventoryRegister(getManager()));

        this.registerServiceImplementation(TopologyQueryService.class, new TopologyQueryService(getManager()));

        annotationScan.registerListener(storageAnnotationListener);
        annotationScan.registerListener(streamAnnotationListener);
        annotationScan.registerListener(new IndicatorTypeListener(getManager()));
        annotationScan.registerListener(new InventoryTypeListener(getManager()));

        this.remoteClientManager = new RemoteClientManager(getManager());
        this.registerServiceImplementation(RemoteClientManager.class, remoteClientManager);
    }

    @Override public void start() throws ModuleStartException {
        grpcServer.addHandler(new RemoteServiceHandler(getManager()));
        remoteClientManager.start();

        try {
            annotationScan.scan(() -> {
                streamDataAnnotationContainer.generate(streamAnnotationListener.getStreamClasses());
            });
        } catch (IOException e) {
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

        PersistenceTimer.INSTANCE.start(getManager());
    }

    @Override
    public String[] requiredModules() {
        return new String[0];
    }
}
