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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.configuration.api.DynamicConfigurationService;
import org.apache.skywalking.oap.server.core.analysis.ApdexThresholdConfig;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.StreamAnnotationListener;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.metrics.ApdexMetrics;
import org.apache.skywalking.oap.server.core.analysis.worker.ManagementStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.TopNStreamProcessor;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.cache.CacheUpdateTimer;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.cache.ProfileTaskCache;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.OAPNodeChecker;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.config.ComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGroupingRuleWatcher;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplateInitializer;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplateManagementService;
import org.apache.skywalking.oap.server.core.oal.rt.DisableOALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskMutationService;
import org.apache.skywalking.oap.server.core.query.AggregationQueryService;
import org.apache.skywalking.oap.server.core.query.AlarmQueryService;
import org.apache.skywalking.oap.server.core.query.BrowserLogQueryService;
import org.apache.skywalking.oap.server.core.query.EventQueryService;
import org.apache.skywalking.oap.server.core.query.LogQueryService;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsMetadataQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsQueryService;
import org.apache.skywalking.oap.server.core.query.ProfileTaskQueryService;
import org.apache.skywalking.oap.server.core.query.TopNRecordsQueryService;
import org.apache.skywalking.oap.server.core.query.TopologyQueryService;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.remote.RemoteSenderService;
import org.apache.skywalking.oap.server.core.remote.RemoteServiceHandler;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.remote.health.HealthCheckServiceHandler;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.SourceReceiverImpl;
import org.apache.skywalking.oap.server.core.storage.PersistenceTimer;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.IModelManager;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.model.ModelManipulator;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.ttl.DataTTLKeeperTimer;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceGetter;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.core.worker.WorkerInstancesService;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServer;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServerConfig;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.TelemetryRelatedContext;

/**
 * Core module provider includes the recommended and default implementations of {@link CoreModule#services()}. All
 * services with these default implementations are widely used including data receiver, data analysis, streaming
 * process, storage and query.
 *
 * NOTICE. In our experiences, no one should re-implement the core module service implementations, unless we are very
 * familiar with all mechanisms of SkyWalking.
 */
public class CoreModuleProvider extends ModuleProvider {

    private final CoreModuleConfig moduleConfig;
    private GRPCServer grpcServer;
    private JettyServer jettyServer;
    private RemoteClientManager remoteClientManager;
    private final AnnotationScan annotationScan;
    private final StorageModels storageModels;
    private final SourceReceiverImpl receiver;
    private ApdexThresholdConfig apdexThresholdConfig;
    private EndpointNameGroupingRuleWatcher endpointNameGroupingRuleWatcher;
    private OALEngineLoaderService oalEngineLoaderService;

    public CoreModuleProvider() {
        super();
        this.moduleConfig = new CoreModuleConfig();
        this.annotationScan = new AnnotationScan();
        this.storageModels = new StorageModels();
        this.receiver = new SourceReceiverImpl();
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return CoreModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return moduleConfig;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        if (moduleConfig.isActiveExtraModelColumns()) {
            DefaultScopeDefine.activeExtraModelColumns();
        }
        EndpointNameGrouping endpointNameGrouping = new EndpointNameGrouping();
        this.registerServiceImplementation(NamingControl.class, new NamingControl(
            moduleConfig.getServiceNameMaxLength(),
            moduleConfig.getInstanceNameMaxLength(),
            moduleConfig.getEndpointNameMaxLength(),
            endpointNameGrouping
        ));
        try {
            endpointNameGroupingRuleWatcher = new EndpointNameGroupingRuleWatcher(
                this, endpointNameGrouping);
        } catch (FileNotFoundException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        AnnotationScan scopeScan = new AnnotationScan();
        scopeScan.registerListener(new DefaultScopeDefine.Listener());
        try {
            scopeScan.scan();
        } catch (Exception e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        this.registerServiceImplementation(MeterSystem.class, new MeterSystem(getManager()));

        AnnotationScan oalDisable = new AnnotationScan();
        oalDisable.registerListener(DisableRegister.INSTANCE);
        oalDisable.registerListener(new DisableRegister.SingleDisableScanListener());
        try {
            oalDisable.scan();
        } catch (IOException | StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        if (moduleConfig.isGRPCSslEnabled()) {
            grpcServer = new GRPCServer(moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort(),
                                        moduleConfig.getGRPCSslCertChainPath(),
                                        moduleConfig.getGRPCSslKeyPath()
            );
        } else {
            grpcServer = new GRPCServer(moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort());
        }
        if (moduleConfig.getMaxConcurrentCallsPerConnection() > 0) {
            grpcServer.setMaxConcurrentCallsPerConnection(moduleConfig.getMaxConcurrentCallsPerConnection());
        }
        if (moduleConfig.getMaxMessageSize() > 0) {
            grpcServer.setMaxMessageSize(moduleConfig.getMaxMessageSize());
        }
        if (moduleConfig.getGRPCThreadPoolQueueSize() > 0) {
            grpcServer.setThreadPoolQueueSize(moduleConfig.getGRPCThreadPoolQueueSize());
        }
        if (moduleConfig.getGRPCThreadPoolSize() > 0) {
            grpcServer.setThreadPoolSize(moduleConfig.getGRPCThreadPoolSize());
        }
        grpcServer.initialize();

        JettyServerConfig jettyServerConfig = JettyServerConfig.builder()
                                                               .host(moduleConfig.getRestHost())
                                                               .port(moduleConfig.getRestPort())
                                                               .contextPath(moduleConfig.getRestContextPath())
                                                               .jettyIdleTimeOut(moduleConfig.getRestIdleTimeOut())
                                                               .jettyAcceptorPriorityDelta(
                                                                   moduleConfig.getRestAcceptorPriorityDelta())
                                                               .jettyMinThreads(moduleConfig.getRestMinThreads())
                                                               .jettyMaxThreads(moduleConfig.getRestMaxThreads())
                                                               .jettyAcceptQueueSize(
                                                                   moduleConfig.getRestAcceptQueueSize())
                                                               .build();
        jettyServer = new JettyServer(jettyServerConfig);
        jettyServer.initialize();

        this.registerServiceImplementation(ConfigService.class, new ConfigService(moduleConfig));
        this.registerServiceImplementation(
            DownSamplingConfigService.class, new DownSamplingConfigService(moduleConfig.getDownsampling()));

        this.registerServiceImplementation(GRPCHandlerRegister.class, new GRPCHandlerRegisterImpl(grpcServer));
        this.registerServiceImplementation(JettyHandlerRegister.class, new JettyHandlerRegisterImpl(jettyServer));

        this.registerServiceImplementation(IComponentLibraryCatalogService.class, new ComponentLibraryCatalogService());

        this.registerServiceImplementation(SourceReceiver.class, receiver);

        WorkerInstancesService instancesService = new WorkerInstancesService();
        this.registerServiceImplementation(IWorkerInstanceGetter.class, instancesService);
        this.registerServiceImplementation(IWorkerInstanceSetter.class, instancesService);

        this.registerServiceImplementation(RemoteSenderService.class, new RemoteSenderService(getManager()));
        this.registerServiceImplementation(ModelCreator.class, storageModels);
        this.registerServiceImplementation(IModelManager.class, storageModels);
        this.registerServiceImplementation(ModelManipulator.class, storageModels);

        this.registerServiceImplementation(
            NetworkAddressAliasCache.class, new NetworkAddressAliasCache(moduleConfig));

        this.registerServiceImplementation(TopologyQueryService.class, new TopologyQueryService(getManager()));
        this.registerServiceImplementation(MetricsMetadataQueryService.class, new MetricsMetadataQueryService());
        this.registerServiceImplementation(MetricsQueryService.class, new MetricsQueryService(getManager()));
        this.registerServiceImplementation(TraceQueryService.class, new TraceQueryService(getManager()));
        this.registerServiceImplementation(BrowserLogQueryService.class, new BrowserLogQueryService(getManager()));
        this.registerServiceImplementation(LogQueryService.class, new LogQueryService(getManager()));
        this.registerServiceImplementation(MetadataQueryService.class, new MetadataQueryService(getManager()));
        this.registerServiceImplementation(AggregationQueryService.class, new AggregationQueryService(getManager()));
        this.registerServiceImplementation(AlarmQueryService.class, new AlarmQueryService(getManager()));
        this.registerServiceImplementation(TopNRecordsQueryService.class, new TopNRecordsQueryService(getManager()));
        this.registerServiceImplementation(EventQueryService.class, new EventQueryService(getManager()));

        // add profile service implementations
        this.registerServiceImplementation(
            ProfileTaskMutationService.class, new ProfileTaskMutationService(getManager()));
        this.registerServiceImplementation(
            ProfileTaskQueryService.class, new ProfileTaskQueryService(getManager(), moduleConfig));
        this.registerServiceImplementation(ProfileTaskCache.class, new ProfileTaskCache(getManager(), moduleConfig));

        this.registerServiceImplementation(CommandService.class, new CommandService(getManager()));

        // add oal engine loader service implementations
        oalEngineLoaderService = new OALEngineLoaderService(getManager());
        this.registerServiceImplementation(OALEngineLoaderService.class, oalEngineLoaderService);

        annotationScan.registerListener(new StreamAnnotationListener(getManager()));

        if (moduleConfig.isGRPCSslEnabled()) {
            this.remoteClientManager = new RemoteClientManager(getManager(), moduleConfig.getRemoteTimeout(),
                                                               moduleConfig.getGRPCSslTrustedCAPath()
            );
        } else {
            this.remoteClientManager = new RemoteClientManager(getManager(), moduleConfig.getRemoteTimeout());
        }
        this.registerServiceImplementation(RemoteClientManager.class, remoteClientManager);

        // Management
        this.registerServiceImplementation(
            UITemplateManagementService.class, new UITemplateManagementService(getManager()));

        MetricsStreamProcessor.getInstance().setEnableDatabaseSession(moduleConfig.isEnableDatabaseSession());
        TopNStreamProcessor.getInstance().setTopNWorkerReportCycle(moduleConfig.getTopNReportPeriod());
        apdexThresholdConfig = new ApdexThresholdConfig(this);
        ApdexMetrics.setDICT(apdexThresholdConfig);
    }

    @Override
    public void start() throws ModuleStartException {
        grpcServer.addHandler(new RemoteServiceHandler(getManager()));
        grpcServer.addHandler(new HealthCheckServiceHandler());
        remoteClientManager.start();

        // Disable OAL script has higher priority
        oalEngineLoaderService.load(DisableOALDefine.INSTANCE);

        try {
            receiver.scan();
            annotationScan.scan();
        } catch (IOException | IllegalAccessException | InstantiationException | StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        Address gRPCServerInstanceAddress = new Address(moduleConfig.getGRPCHost(), moduleConfig.getGRPCPort(), true);
        TelemetryRelatedContext.INSTANCE.setId(gRPCServerInstanceAddress.toString());
        if (CoreModuleConfig.Role.Mixed.name()
                                       .equalsIgnoreCase(
                                           moduleConfig.getRole())
            || CoreModuleConfig.Role.Aggregator.name()
                                               .equalsIgnoreCase(
                                                   moduleConfig.getRole())) {
            RemoteInstance gRPCServerInstance = new RemoteInstance(gRPCServerInstanceAddress);
            this.getManager()
                .find(ClusterModule.NAME)
                .provider()
                .getService(ClusterRegister.class)
                .registerRemote(gRPCServerInstance);
        }

        OAPNodeChecker.setROLE(CoreModuleConfig.Role.fromName(moduleConfig.getRole()));

        DynamicConfigurationService dynamicConfigurationService = getManager().find(ConfigurationModule.NAME)
                                                                              .provider()
                                                                              .getService(
                                                                                  DynamicConfigurationService.class);
        dynamicConfigurationService.registerConfigChangeWatcher(apdexThresholdConfig);
        dynamicConfigurationService.registerConfigChangeWatcher(endpointNameGroupingRuleWatcher);
    }

    @Override
    public void notifyAfterCompleted() throws ModuleStartException {
        try {
            grpcServer.start();
            jettyServer.start();
        } catch (ServerException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        PersistenceTimer.INSTANCE.start(getManager(), moduleConfig);

        if (moduleConfig.isEnableDataKeeperExecutor()) {
            DataTTLKeeperTimer.INSTANCE.start(getManager(), moduleConfig);
        }

        CacheUpdateTimer.INSTANCE.start(getManager(), moduleConfig.getMetricsDataTTL());

        try {
            final File[] templateFiles = ResourceUtils.getPathFiles("ui-initialized-templates");
            for (final File templateFile : templateFiles) {
                new UITemplateInitializer(new FileInputStream(templateFile))
                    .read()
                    .forEach(uiTemplate -> {
                        ManagementStreamProcessor.getInstance().in(uiTemplate);
                    });
            }

        } catch (FileNotFoundException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            TelemetryModule.NAME,
            ConfigurationModule.NAME
        };
    }
}
