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


package org.apache.skywalking.apm.collector.agent.stream.worker.jvm;

import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryPoolMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;

/**
 * @author peng-yongsheng
 */
public class MemoryPoolMetricPersistenceWorker extends PersistenceWorker<MemoryPoolMetric, MemoryPoolMetric> {

    @Override public int id() {
        return 122;
    }

    public MemoryPoolMetricPersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override protected boolean needMergeDBData() {
        return false;
    }

    @Override protected IPersistenceDAO persistenceDAO() {
        return getModuleManager().find(StorageModule.NAME).getService(IMemoryPoolMetricPersistenceDAO.class);
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<MemoryPoolMetric, MemoryPoolMetric, MemoryPoolMetricPersistenceWorker> {

        public Factory(ModuleManager moduleManager, QueueCreatorService<MemoryPoolMetric> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public MemoryPoolMetricPersistenceWorker workerInstance(ModuleManager moduleManager) {
            return new MemoryPoolMetricPersistenceWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
