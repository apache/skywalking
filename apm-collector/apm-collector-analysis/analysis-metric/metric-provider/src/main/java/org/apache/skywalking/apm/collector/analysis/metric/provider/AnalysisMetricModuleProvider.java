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

package org.apache.skywalking.apm.collector.analysis.metric.provider;

import org.apache.skywalking.apm.collector.analysis.metric.define.AnalysisMetricModule;
import org.apache.skywalking.apm.collector.analysis.metric.define.service.IInstanceHeartBeatService;
import org.apache.skywalking.apm.collector.analysis.metric.provider.service.InstanceHeartBeatService;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.component.ApplicationComponentGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.component.ApplicationComponentSpanListener;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.mapping.ApplicationMappingGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.mapping.ApplicationMappingSpanListener;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.metric.ApplicationMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.refmetric.ApplicationReferenceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.GlobalTraceGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.GlobalTraceSpanListener;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.std.ResponseTimeDistributionGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.std.ResponseTimeDistributionSpanListener;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.heartbeat.InstanceHeartBeatPersistenceGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.mapping.InstanceMappingGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.mapping.InstanceMappingSpanListener;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.metric.InstanceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.refmetric.InstanceReferenceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.segment.SegmentDurationGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.segment.SegmentDurationSpanListener;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.metric.ServiceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.refmetric.ServiceReferenceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.refmetric.ServiceReferenceMetricSpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.AnalysisSegmentParserModule;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.service.ISegmentParserListenerRegister;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.analysis.worker.timer.PersistenceTimer;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponent;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMapping;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistribution;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMapping;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class AnalysisMetricModuleProvider extends ModuleProvider {

    public static final String NAME = "default";
    private final AnalysisMetricModuleConfig config;

    public AnalysisMetricModuleProvider() {
        super();
        this.config = new AnalysisMetricModuleConfig();
    }

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends Module> module() {
        return AnalysisMetricModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IInstanceHeartBeatService.class, new InstanceHeartBeatService());
    }

    @Override public void start() {
        segmentParserListenerRegister();

        WorkerCreateListener workerCreateListener = new WorkerCreateListener();

        graphCreate(workerCreateListener);

        registerRemoteData();

        PersistenceTimer.INSTANCE.start(getManager(), workerCreateListener.getPersistenceWorkers());
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override public String[] requiredModules() {
        return new String[] {AnalysisSegmentParserModule.NAME, ConfigurationModule.NAME, CacheModule.NAME, StorageModule.NAME};
    }

    private void segmentParserListenerRegister() {
        ISegmentParserListenerRegister segmentParserListenerRegister = getManager().find(AnalysisSegmentParserModule.NAME).getService(ISegmentParserListenerRegister.class);
        segmentParserListenerRegister.register(new ServiceReferenceMetricSpanListener.Factory());
        segmentParserListenerRegister.register(new ApplicationComponentSpanListener.Factory());
        segmentParserListenerRegister.register(new ApplicationMappingSpanListener.Factory());
        segmentParserListenerRegister.register(new InstanceMappingSpanListener.Factory());
        segmentParserListenerRegister.register(new GlobalTraceSpanListener.Factory());
        segmentParserListenerRegister.register(new SegmentDurationSpanListener.Factory());
        segmentParserListenerRegister.register(new ResponseTimeDistributionSpanListener.Factory());
    }

    private void graphCreate(WorkerCreateListener workerCreateListener) {
        ServiceReferenceMetricGraph serviceReferenceMetricGraph = new ServiceReferenceMetricGraph(getManager(), workerCreateListener);
        serviceReferenceMetricGraph.create();

        InstanceReferenceMetricGraph instanceReferenceMetricGraph = new InstanceReferenceMetricGraph(getManager(), workerCreateListener);
        instanceReferenceMetricGraph.create();

        ApplicationReferenceMetricGraph applicationReferenceMetricGraph = new ApplicationReferenceMetricGraph(getManager(), workerCreateListener);
        applicationReferenceMetricGraph.create();

        ServiceMetricGraph serviceMetricGraph = new ServiceMetricGraph(getManager(), workerCreateListener);
        serviceMetricGraph.create();

        InstanceMetricGraph instanceMetricGraph = new InstanceMetricGraph(getManager(), workerCreateListener);
        instanceMetricGraph.create();

        ApplicationMetricGraph applicationMetricGraph = new ApplicationMetricGraph(getManager(), workerCreateListener);
        applicationMetricGraph.create();

        ApplicationComponentGraph applicationComponentGraph = new ApplicationComponentGraph(getManager(), workerCreateListener);
        applicationComponentGraph.create();

        ApplicationMappingGraph applicationMappingGraph = new ApplicationMappingGraph(getManager(), workerCreateListener);
        applicationMappingGraph.create();

        InstanceMappingGraph instanceMappingGraph = new InstanceMappingGraph(getManager(), workerCreateListener);
        instanceMappingGraph.create();

        GlobalTraceGraph globalTraceGraph = new GlobalTraceGraph(getManager(), workerCreateListener);
        globalTraceGraph.create();

        ResponseTimeDistributionGraph responseTimeDistributionGraph = new ResponseTimeDistributionGraph(getManager(), workerCreateListener);
        responseTimeDistributionGraph.create();

        SegmentDurationGraph segmentDurationGraph = new SegmentDurationGraph(getManager(), workerCreateListener);
        segmentDurationGraph.create();

        InstanceHeartBeatPersistenceGraph instanceHeartBeatPersistenceGraph = new InstanceHeartBeatPersistenceGraph(getManager(), workerCreateListener);
        instanceHeartBeatPersistenceGraph.create();
    }

    private void registerRemoteData() {
        RemoteDataRegisterService remoteDataRegisterService = getManager().find(RemoteModule.NAME).getService(RemoteDataRegisterService.class);
        remoteDataRegisterService.register(ApplicationComponent.class, new ApplicationComponent.InstanceCreator());
        remoteDataRegisterService.register(ApplicationMapping.class, new ApplicationMapping.InstanceCreator());
        remoteDataRegisterService.register(ApplicationMetric.class, new ApplicationMetric.InstanceCreator());
        remoteDataRegisterService.register(ApplicationReferenceMetric.class, new ApplicationReferenceMetric.InstanceCreator());
        remoteDataRegisterService.register(InstanceMapping.class, new InstanceMapping.InstanceCreator());
        remoteDataRegisterService.register(InstanceMetric.class, new InstanceMetric.InstanceCreator());
        remoteDataRegisterService.register(InstanceReferenceMetric.class, new InstanceReferenceMetric.InstanceCreator());
        remoteDataRegisterService.register(ServiceMetric.class, new ServiceMetric.InstanceCreator());
        remoteDataRegisterService.register(ServiceReferenceMetric.class, new ServiceReferenceMetric.InstanceCreator());
        remoteDataRegisterService.register(ResponseTimeDistribution.class, new ResponseTimeDistribution.InstanceCreator());
    }
}
