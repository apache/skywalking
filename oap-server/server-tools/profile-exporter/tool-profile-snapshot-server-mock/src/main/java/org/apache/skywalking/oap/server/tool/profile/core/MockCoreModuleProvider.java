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

package org.apache.skywalking.oap.server.tool.profile.core;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.cache.AsyncProfilerTaskCache;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.cache.ProfileTaskCache;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.config.HierarchyDefinitionService;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGroupService;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.hierarchy.HierarchyService;
import org.apache.skywalking.oap.server.core.management.ui.menu.UIMenuManagementService;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplateManagementService;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.AsyncProfilerMutationService;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.AsyncProfilerQueryService;
import org.apache.skywalking.oap.server.core.profiling.continuous.ContinuousProfilingMutationService;
import org.apache.skywalking.oap.server.core.profiling.continuous.ContinuousProfilingQueryService;
import org.apache.skywalking.oap.server.core.profiling.ebpf.EBPFProfilingMutationService;
import org.apache.skywalking.oap.server.core.profiling.ebpf.EBPFProfilingQueryService;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskMutationService;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskQueryService;
import org.apache.skywalking.oap.server.core.query.AggregationQueryService;
import org.apache.skywalking.oap.server.core.query.AlarmQueryService;
import org.apache.skywalking.oap.server.core.query.BrowserLogQueryService;
import org.apache.skywalking.oap.server.core.query.EventQueryService;
import org.apache.skywalking.oap.server.core.query.HierarchyQueryService;
import org.apache.skywalking.oap.server.core.query.LogQueryService;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsMetadataQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsQueryService;
import org.apache.skywalking.oap.server.core.query.RecordQueryService;
import org.apache.skywalking.oap.server.core.query.TTLStatusQuery;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.query.TopNRecordsQueryService;
import org.apache.skywalking.oap.server.core.query.TopologyQueryService;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.remote.RemoteSenderService;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.status.ServerStatusService;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.IModelManager;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.model.ModelManipulator;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceGetter;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.tool.profile.core.mock.MockComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.tool.profile.core.mock.MockGRPCHandlerRegister;
import org.apache.skywalking.oap.server.tool.profile.core.mock.MockHTTPHandlerRegister;
import org.apache.skywalking.oap.server.tool.profile.core.mock.MockRemoteClientManager;
import org.apache.skywalking.oap.server.tool.profile.core.mock.MockSourceReceiver;
import org.apache.skywalking.oap.server.tool.profile.core.mock.MockStreamAnnotationListener;
import org.apache.skywalking.oap.server.tool.profile.core.mock.MockWorkerInstancesService;

import java.io.IOException;
import java.util.Collections;

public class MockCoreModuleProvider extends CoreModuleProvider {

    private final StorageModels storageModels;
    private final AnnotationScan annotationScan;

    public MockCoreModuleProvider() {
        this.storageModels = new StorageModels();
        this.annotationScan = new AnnotationScan();
    }

    @Override
    public String name() {
        return "tool-profile-mock-core";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return CoreModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return null;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        this.registerServiceImplementation(
                NamingControl.class,
                new NamingControl(50, 50, 150, new EndpointNameGrouping())
        );

        MockStreamAnnotationListener streamAnnotationListener = new MockStreamAnnotationListener(getManager());
        annotationScan.registerListener(streamAnnotationListener);

        AnnotationScan scopeScan = new AnnotationScan();
        scopeScan.registerListener(new DefaultScopeDefine.Listener());
        try {
            scopeScan.scan();
        } catch (Exception e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        this.registerServiceImplementation(MeterSystem.class, new MeterSystem(getManager()));

        CoreModuleConfig moduleConfig = new CoreModuleConfig();
        this.registerServiceImplementation(ConfigService.class, new ConfigService(moduleConfig, this));
        this.registerServiceImplementation(ServerStatusService.class, new ServerStatusService(getManager(), moduleConfig));
        moduleConfig.setEnableHierarchy(false);
        this.registerServiceImplementation(HierarchyDefinitionService.class, new HierarchyDefinitionService(moduleConfig));
        this.registerServiceImplementation(HierarchyService.class, new HierarchyService(getManager(), moduleConfig));
        this.registerServiceImplementation(
                DownSamplingConfigService.class, new DownSamplingConfigService(Collections.emptyList()));

        this.registerServiceImplementation(GRPCHandlerRegister.class, new MockGRPCHandlerRegister());
        this.registerServiceImplementation(HTTPHandlerRegister.class, new MockHTTPHandlerRegister());

        this.registerServiceImplementation(
                IComponentLibraryCatalogService.class, new MockComponentLibraryCatalogService());

        this.registerServiceImplementation(SourceReceiver.class, new MockSourceReceiver());

        MockWorkerInstancesService instancesService = new MockWorkerInstancesService();
        this.registerServiceImplementation(IWorkerInstanceGetter.class, instancesService);
        this.registerServiceImplementation(IWorkerInstanceSetter.class, instancesService);

        this.registerServiceImplementation(RemoteSenderService.class, new RemoteSenderService(getManager()));
        this.registerServiceImplementation(ModelCreator.class, storageModels);
        this.registerServiceImplementation(IModelManager.class, storageModels);
        this.registerServiceImplementation(ModelManipulator.class, storageModels);

        this.registerServiceImplementation(
                NetworkAddressAliasCache.class, new NetworkAddressAliasCache(moduleConfig));

        this.registerServiceImplementation(TopologyQueryService.class, new TopologyQueryService(getManager(), storageModels));
        this.registerServiceImplementation(MetricsMetadataQueryService.class, new MetricsMetadataQueryService());
        this.registerServiceImplementation(MetricsQueryService.class, new MetricsQueryService(getManager()));
        this.registerServiceImplementation(TraceQueryService.class, new TraceQueryService(getManager()));
        this.registerServiceImplementation(BrowserLogQueryService.class, new BrowserLogQueryService(getManager()));
        this.registerServiceImplementation(LogQueryService.class, new LogQueryService(getManager()));
        this.registerServiceImplementation(MetadataQueryService.class, new MetadataQueryService(getManager(), moduleConfig));
        this.registerServiceImplementation(AggregationQueryService.class, new AggregationQueryService(getManager()));
        this.registerServiceImplementation(AlarmQueryService.class, new AlarmQueryService(getManager()));
        this.registerServiceImplementation(TopNRecordsQueryService.class, new TopNRecordsQueryService(getManager()));

        // add profile service implementations
        this.registerServiceImplementation(
                ProfileTaskMutationService.class, new ProfileTaskMutationService(getManager()));
        this.registerServiceImplementation(
                ProfileTaskQueryService.class, new ProfileTaskQueryService(getManager(), moduleConfig));
        this.registerServiceImplementation(ProfileTaskCache.class, new ProfileTaskCache(getManager(), moduleConfig));

        this.registerServiceImplementation(CommandService.class, new CommandService(getManager()));

        this.registerServiceImplementation(RemoteClientManager.class, new MockRemoteClientManager(getManager(), 0));

        // add oal engine loader service implementations
        this.registerServiceImplementation(OALEngineLoaderService.class, new OALEngineLoaderService(getManager()));

        // Management
        this.registerServiceImplementation(UITemplateManagementService.class, new UITemplateManagementService(getManager()));
        this.registerServiceImplementation(
            UIMenuManagementService.class, new UIMenuManagementService(getManager(), moduleConfig));

        this.registerServiceImplementation(EventQueryService.class, new EventQueryService(getManager()));
        this.registerServiceImplementation(RecordQueryService.class, new RecordQueryService(getManager()));
        this.registerServiceImplementation(HierarchyQueryService.class, new HierarchyQueryService(getManager(), moduleConfig));
        this.registerServiceImplementation(
            TTLStatusQuery.class, new TTLStatusQuery(
                getManager(),
                moduleConfig.getMetricsDataTTL(),
                moduleConfig.getRecordDataTTL()
            )
        );
        this.registerServiceImplementation(
            TagAutoCompleteQueryService.class, new TagAutoCompleteQueryService(getManager(), moduleConfig));
        this.registerServiceImplementation(
            AsyncProfilerMutationService.class, new AsyncProfilerMutationService(getManager()));
        this.registerServiceImplementation(
            AsyncProfilerQueryService.class, new AsyncProfilerQueryService(getManager()));
        this.registerServiceImplementation(
            AsyncProfilerTaskCache.class, new AsyncProfilerTaskCache(getManager(), moduleConfig));
        this.registerServiceImplementation(
            EBPFProfilingMutationService.class, new EBPFProfilingMutationService(getManager()));
        this.registerServiceImplementation(
            EBPFProfilingQueryService.class,
            new EBPFProfilingQueryService(getManager(), moduleConfig, this.storageModels)
        );
        this.registerServiceImplementation(
            ContinuousProfilingMutationService.class, new ContinuousProfilingMutationService(getManager()));
        this.registerServiceImplementation(
            ContinuousProfilingQueryService.class, new ContinuousProfilingQueryService(getManager()));
        this.registerServiceImplementation(EndpointNameGroupService.class, new EndpointNameGrouping());

    }

    @Override
    public void start() throws ModuleStartException {
        try {
            annotationScan.scan();
        } catch (IOException | StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() {
    }

    @Override
    public String[] requiredModules() {
        return new String[]{
                TelemetryModule.NAME
        };
    }
}
