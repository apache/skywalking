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

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.cache.AsyncProfilerTaskCache;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.cache.ProfileTaskCache;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.HierarchyDefinitionService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
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
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.status.ServerStatusService;
import org.apache.skywalking.oap.server.core.storage.model.IModelManager;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.model.ModelManipulator;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceGetter;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * Core module definition. Define all open services to other modules.
 */
public class CoreModule extends ModuleDefine {
    public static final String NAME = "core";

    public CoreModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        List<Class> classes = new ArrayList<>();
        classes.add(ConfigService.class);
        classes.add(ServerStatusService.class);
        classes.add(DownSamplingConfigService.class);
        classes.add(NamingControl.class);
        classes.add(IComponentLibraryCatalogService.class);
        classes.add(HierarchyDefinitionService.class);

        classes.add(IWorkerInstanceGetter.class);
        classes.add(IWorkerInstanceSetter.class);

        classes.add(MeterSystem.class);

        addServerInterface(classes);
        addReceiverInterface(classes);
        addInternalServices(classes);
        addCacheService(classes);
        addQueryService(classes);
        addProfileService(classes);
        addOALService(classes);
        addManagementService(classes);
        addEBPFProfilingService(classes);
        addAsyncProfilerService(classes);

        classes.add(CommandService.class);
        classes.add(HierarchyService.class);
        return classes.toArray(new Class[]{});
    }

    private void addEBPFProfilingService(List<Class> classes) {
        classes.add(EBPFProfilingMutationService.class);
        classes.add(EBPFProfilingQueryService.class);
        classes.add(ContinuousProfilingMutationService.class);
        classes.add(ContinuousProfilingQueryService.class);
    }

    private void addManagementService(List<Class> classes) {
        classes.add(UITemplateManagementService.class);
        classes.add(UIMenuManagementService.class);
    }

    private void addProfileService(List<Class> classes) {
        classes.add(ProfileTaskMutationService.class);
        classes.add(ProfileTaskQueryService.class);
        classes.add(ProfileTaskCache.class);
    }

    private void addAsyncProfilerService(List<Class> classes) {
        classes.add(AsyncProfilerMutationService.class);
        classes.add(AsyncProfilerQueryService.class);
        classes.add(AsyncProfilerTaskCache.class);
    }

    private void addOALService(List<Class> classes) {
        classes.add(OALEngineLoaderService.class);
    }

    private void addQueryService(List<Class> classes) {
        classes.add(TopologyQueryService.class);
        classes.add(MetricsMetadataQueryService.class);
        classes.add(MetricsQueryService.class);
        classes.add(TraceQueryService.class);
        classes.add(LogQueryService.class);
        classes.add(MetadataQueryService.class);
        classes.add(AggregationQueryService.class);
        classes.add(AlarmQueryService.class);
        classes.add(TopNRecordsQueryService.class);
        classes.add(BrowserLogQueryService.class);
        classes.add(EventQueryService.class);
        classes.add(TagAutoCompleteQueryService.class);
        classes.add(RecordQueryService.class);
        classes.add(HierarchyQueryService.class);
        classes.add(TTLStatusQuery.class);
    }

    private void addServerInterface(List<Class> classes) {
        classes.add(GRPCHandlerRegister.class);
        classes.add(HTTPHandlerRegister.class);
    }

    private void addInternalServices(List<Class> classes) {
        classes.add(ModelCreator.class);
        classes.add(IModelManager.class);
        classes.add(ModelManipulator.class);
        classes.add(RemoteClientManager.class);
        classes.add(RemoteSenderService.class);
    }

    private void addCacheService(List<Class> classes) {
        classes.add(NetworkAddressAliasCache.class);
    }

    private void addReceiverInterface(List<Class> classes) {
        classes.add(SourceReceiver.class);
    }
}
