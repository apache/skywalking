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

package org.apache.skywalking.apm.collector.analysis.alarm.provider;

import org.apache.skywalking.apm.collector.analysis.alarm.define.AnalysisAlarmModule;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.application.*;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.instance.*;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.service.*;
import org.apache.skywalking.apm.collector.analysis.metric.define.AnalysisMetricModule;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.analysis.worker.timer.PersistenceTimer;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.table.alarm.*;

/**
 * @author peng-yongsheng
 */
public class AnalysisAlarmModuleProvider extends ModuleProvider {

    private final AnalysisAlarmModuleConfig config;

    public AnalysisAlarmModuleProvider() {
        super();
        this.config = new AnalysisAlarmModuleConfig();
    }

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return AnalysisAlarmModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() {
    }

    @Override public void start() {
        WorkerCreateListener workerCreateListener = new WorkerCreateListener();

        new ServiceMetricAlarmGraph(getManager(), workerCreateListener).create();
        new InstanceMetricAlarmGraph(getManager(), workerCreateListener).create();
        new ApplicationMetricAlarmGraph(getManager(), workerCreateListener).create();
        new ServiceReferenceMetricAlarmGraph(getManager(), workerCreateListener).create();
        new InstanceReferenceMetricAlarmGraph(getManager(), workerCreateListener).create();
        new ApplicationReferenceMetricAlarmGraph(getManager(), workerCreateListener).create();

        registerRemoteData();

        PersistenceTimer.INSTANCE.start(getManager(), workerCreateListener.getPersistenceWorkers());
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override public String[] requiredModules() {
        return new String[] {RemoteModule.NAME, AnalysisMetricModule.NAME, ConfigurationModule.NAME, StorageModule.NAME};
    }

    private void registerRemoteData() {
        RemoteDataRegisterService remoteDataRegisterService = getManager().find(RemoteModule.NAME).getService(RemoteDataRegisterService.class);
        remoteDataRegisterService.register(ApplicationAlarm.class, new ApplicationAlarm.InstanceCreator());
        remoteDataRegisterService.register(ApplicationAlarmList.class, new ApplicationAlarmList.InstanceCreator());
        remoteDataRegisterService.register(ApplicationReferenceAlarm.class, new ApplicationReferenceAlarm.InstanceCreator());
        remoteDataRegisterService.register(ApplicationReferenceAlarmList.class, new ApplicationReferenceAlarmList.InstanceCreator());
        remoteDataRegisterService.register(InstanceAlarm.class, new InstanceAlarm.InstanceCreator());
        remoteDataRegisterService.register(InstanceAlarmList.class, new InstanceAlarmList.InstanceCreator());
        remoteDataRegisterService.register(InstanceReferenceAlarm.class, new InstanceReferenceAlarm.InstanceCreator());
        remoteDataRegisterService.register(InstanceReferenceAlarmList.class, new InstanceReferenceAlarmList.InstanceCreator());
        remoteDataRegisterService.register(ServiceAlarm.class, new ServiceAlarm.InstanceCreator());
        remoteDataRegisterService.register(ServiceAlarmList.class, new ServiceAlarmList.InstanceCreator());
        remoteDataRegisterService.register(ServiceReferenceAlarm.class, new ServiceReferenceAlarm.InstanceCreator());
        remoteDataRegisterService.register(ServiceReferenceAlarmList.class, new ServiceReferenceAlarmList.InstanceCreator());
    }
}
