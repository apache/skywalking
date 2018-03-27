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

package org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.application;

import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.PersistenceWorker;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.PersistenceWorkerProvider;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmList;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricAlarmListHourPersistenceWorker extends PersistenceWorker<ApplicationAlarmList> {

    public ApplicationMetricAlarmListHourPersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return AlarmWorkerIdDefine.APPLICATION_METRIC_ALARM_LIST_HOUR_PERSISTENCE_WORKER_ID;
    }

    @Override protected boolean needMergeDBData() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override protected IPersistenceDAO<?, ?, ApplicationAlarmList> persistenceDAO() {
        return getModuleManager().find(StorageModule.NAME).getService(IApplicationAlarmListHourPersistenceDAO.class);
    }

    public static class Factory extends PersistenceWorkerProvider<ApplicationAlarmList, ApplicationMetricAlarmListHourPersistenceWorker> {
        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ApplicationMetricAlarmListHourPersistenceWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationMetricAlarmListHourPersistenceWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
