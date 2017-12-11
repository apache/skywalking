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


package org.apache.skywalking.apm.collector.agent.stream.worker.trace.application;

import org.apache.skywalking.apm.collector.agent.stream.service.graph.ApplicationGraphNodeIdDefine;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMetricPersistenceWorker extends PersistenceWorker<ApplicationReferenceMetric, ApplicationReferenceMetric> {

    public ApplicationReferenceMetricPersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return ApplicationGraphNodeIdDefine.APPLICATION_REFERENCE_METRIC_PERSISTENCE_NODE_ID;
    }

    @Override protected boolean needMergeDBData() {
        return true;
    }

    @Override protected IPersistenceDAO persistenceDAO() {
        return getModuleManager().find(StorageModule.NAME).getService(IApplicationReferenceMetricPersistenceDAO.class);
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ApplicationReferenceMetric, ApplicationReferenceMetric, ApplicationReferenceMetricPersistenceWorker> {

        public Factory(ModuleManager moduleManager,
            QueueCreatorService<ApplicationReferenceMetric> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public ApplicationReferenceMetricPersistenceWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationReferenceMetricPersistenceWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
