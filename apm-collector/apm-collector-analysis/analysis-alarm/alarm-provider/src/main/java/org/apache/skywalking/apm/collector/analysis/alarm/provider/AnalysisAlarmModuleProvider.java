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

import java.util.Properties;
import org.apache.skywalking.apm.collector.analysis.alarm.define.AnalysisAlarmModule;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.application.ApplicationMetricAlarmGraph;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.application.ApplicationReferenceMetricAlarmGraph;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.instance.InstanceMetricAlarmGraph;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.instance.InstanceReferenceMetricAlarmGraph;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.service.ServiceMetricAlarmGraph;
import org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.service.ServiceReferenceMetricAlarmGraph;
import org.apache.skywalking.apm.collector.analysis.metric.define.AnalysisMetricModule;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.analysis.worker.timer.PersistenceTimer;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmList;

/**
 * @author peng-yongsheng
 */
public class AnalysisAlarmModuleProvider extends ModuleProvider {

    @Override public String name() {
        return "default";
    }

    @Override public Class<? extends Module> module() {
        return AnalysisAlarmModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {

    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        WorkerCreateListener workerCreateListener = new WorkerCreateListener();

        ServiceMetricAlarmGraph serviceMetricAlarmGraph = new ServiceMetricAlarmGraph(getManager(), workerCreateListener);
        serviceMetricAlarmGraph.create();

        InstanceMetricAlarmGraph instanceMetricAlarmGraph = new InstanceMetricAlarmGraph(getManager(), workerCreateListener);
        instanceMetricAlarmGraph.create();

        ApplicationMetricAlarmGraph applicationMetricAlarmGraph = new ApplicationMetricAlarmGraph(getManager(), workerCreateListener);
        applicationMetricAlarmGraph.create();

        ServiceReferenceMetricAlarmGraph serviceReferenceMetricAlarmGraph = new ServiceReferenceMetricAlarmGraph(getManager(), workerCreateListener);
        serviceReferenceMetricAlarmGraph.create();

        InstanceReferenceMetricAlarmGraph instanceReferenceMetricAlarmGraph = new InstanceReferenceMetricAlarmGraph(getManager(), workerCreateListener);
        instanceReferenceMetricAlarmGraph.create();

        ApplicationReferenceMetricAlarmGraph applicationReferenceMetricAlarmGraph = new ApplicationReferenceMetricAlarmGraph(getManager(), workerCreateListener);
        applicationReferenceMetricAlarmGraph.create();

        registerRemoteData();

        PersistenceTimer persistenceTimer = new PersistenceTimer(AnalysisAlarmModule.NAME);
        persistenceTimer.start(getManager(), workerCreateListener.getPersistenceWorkers());
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[] {RemoteModule.NAME, AnalysisMetricModule.NAME, ConfigurationModule.NAME, StorageModule.NAME};
    }

    private void registerRemoteData() {
        RemoteDataRegisterService remoteDataRegisterService = getManager().find(RemoteModule.NAME).getService(RemoteDataRegisterService.class);
        remoteDataRegisterService.register(ApplicationAlarm.class, new ApplicationAlarm.InstanceCreator());
        remoteDataRegisterService.register(ApplicationAlarmList.class, new ApplicationAlarmList.InstanceCreator());
        remoteDataRegisterService.register(InstanceAlarm.class, new InstanceAlarm.InstanceCreator());
        remoteDataRegisterService.register(InstanceAlarmList.class, new InstanceAlarmList.InstanceCreator());
        remoteDataRegisterService.register(ServiceAlarm.class, new ServiceAlarm.InstanceCreator());
        remoteDataRegisterService.register(ServiceAlarmList.class, new ServiceAlarmList.InstanceCreator());

    }
}
