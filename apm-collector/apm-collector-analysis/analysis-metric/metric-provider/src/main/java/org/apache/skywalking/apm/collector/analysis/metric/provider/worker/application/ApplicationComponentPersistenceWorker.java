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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.WorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.PersistenceWorker;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponent;

/**
 * @author peng-yongsheng
 */
public class ApplicationComponentPersistenceWorker extends PersistenceWorker<ApplicationComponent, ApplicationComponent> {

    public ApplicationComponentPersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return WorkerIdDefine.APPLICATION_COMPONENT_PERSISTENCE_WORKER_ID;
    }

    @Override protected boolean needMergeDBData() {
        return true;
    }

    @Override protected IPersistenceDAO persistenceDAO() {
        return getModuleManager().find(StorageModule.NAME).getService(IApplicationComponentPersistenceDAO.class);
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ApplicationComponent, ApplicationComponent, ApplicationComponentPersistenceWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ApplicationComponentPersistenceWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationComponentPersistenceWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
