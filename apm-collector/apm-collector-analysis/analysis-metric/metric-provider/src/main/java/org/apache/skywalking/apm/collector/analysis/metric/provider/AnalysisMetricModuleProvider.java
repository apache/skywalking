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
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.component.*;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.mapping.*;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.metric.ApplicationMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.refmetric.ApplicationReferenceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.*;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.std.*;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.heartbeat.InstanceHeartBeatPersistenceGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.mapping.*;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.metric.InstanceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.refmetric.InstanceReferenceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.segment.*;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.heartbeat.*;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.metric.ServiceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.refmetric.*;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.AnalysisSegmentParserModule;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.service.ISegmentParserListenerRegister;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.analysis.worker.timer.PersistenceTimer;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.table.application.*;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistribution;
import org.apache.skywalking.apm.collector.storage.table.instance.*;
import org.apache.skywalking.apm.collector.storage.table.service.*;

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

    @Override public Class<? extends ModuleDefine> module() {
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
        segmentParserListenerRegister.register(new ServiceNameSpanListener.Factory());
    }

    private void graphCreate(WorkerCreateListener workerCreateListener) {
        new ServiceReferenceMetricGraph(getManager(), workerCreateListener).create();
        new ServiceMetricGraph(getManager(), workerCreateListener).create();
        new ServiceNameHeartBeatGraph(getManager(), workerCreateListener).create();

        new InstanceHeartBeatPersistenceGraph(getManager(), workerCreateListener).create();
        new InstanceMappingGraph(getManager(), workerCreateListener).create();
        new InstanceReferenceMetricGraph(getManager(), workerCreateListener).create();
        new InstanceMetricGraph(getManager(), workerCreateListener).create();

        new ApplicationComponentGraph(getManager(), workerCreateListener).create();
        new ApplicationMappingGraph(getManager(), workerCreateListener).create();
        new ApplicationReferenceMetricGraph(getManager(), workerCreateListener).create();
        new ApplicationMetricGraph(getManager(), workerCreateListener).create();

        new GlobalTraceGraph(getManager(), workerCreateListener).create();
        new ResponseTimeDistributionGraph(getManager(), workerCreateListener).create();
        new SegmentDurationGraph(getManager(), workerCreateListener).create();
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
