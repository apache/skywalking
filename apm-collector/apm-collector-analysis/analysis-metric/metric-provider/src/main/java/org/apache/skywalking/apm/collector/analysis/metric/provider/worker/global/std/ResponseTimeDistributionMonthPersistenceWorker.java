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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.std;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.PersistenceWorker;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.PersistenceWorkerProvider;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.rtd.IResponseTimeDistributionMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistribution;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistributionTable;

/**
 * @author peng-yongsheng
 */
public class ResponseTimeDistributionMonthPersistenceWorker extends PersistenceWorker<ResponseTimeDistribution> {

    private ResponseTimeDistributionMonthPersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return MetricWorkerIdDefine.RESPONSE_TIME_DISTRIBUTION_MONTH_PERSISTENCE_WORKER_ID;
    }

    @Override protected boolean needMergeDBData() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override protected IPersistenceDAO<?, ?, ResponseTimeDistribution> persistenceDAO() {
        return getModuleManager().find(StorageModule.NAME).getService(IResponseTimeDistributionMonthPersistenceDAO.class);
    }

    public static class Factory extends PersistenceWorkerProvider<ResponseTimeDistribution, ResponseTimeDistributionMonthPersistenceWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ResponseTimeDistributionMonthPersistenceWorker workerInstance(ModuleManager moduleManager) {
            return new ResponseTimeDistributionMonthPersistenceWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    @GraphComputingMetric(name = "/persistence/onWork/" + ResponseTimeDistributionTable.TABLE + "/month")
    @Override protected void onWork(ResponseTimeDistribution input) {
        super.onWork(input);
    }
}
