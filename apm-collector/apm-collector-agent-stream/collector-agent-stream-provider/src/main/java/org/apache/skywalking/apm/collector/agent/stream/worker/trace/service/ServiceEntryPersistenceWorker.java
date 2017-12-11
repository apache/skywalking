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


package org.apache.skywalking.apm.collector.agent.stream.worker.trace.service;

import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.IServiceEntryPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceEntry;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;

/**
 * @author peng-yongsheng
 */
public class ServiceEntryPersistenceWorker extends PersistenceWorker<ServiceEntry, ServiceEntry> {

    public ServiceEntryPersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return 121;
    }

    @Override protected boolean needMergeDBData() {
        return true;
    }

    @Override protected IPersistenceDAO persistenceDAO() {
        return getModuleManager().find(StorageModule.NAME).getService(IServiceEntryPersistenceDAO.class);
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceEntry, ServiceEntry, ServiceEntryPersistenceWorker> {
        public Factory(ModuleManager moduleManager, QueueCreatorService<ServiceEntry> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public ServiceEntryPersistenceWorker workerInstance(ModuleManager moduleManager) {
            return new ServiceEntryPersistenceWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
