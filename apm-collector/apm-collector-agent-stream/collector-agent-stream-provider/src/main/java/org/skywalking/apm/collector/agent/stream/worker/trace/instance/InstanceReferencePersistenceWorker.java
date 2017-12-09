/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agent.stream.worker.trace.instance;

import org.skywalking.apm.collector.agent.stream.service.graph.InstanceGraphNodeIdDefine;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IInstanceReferenceMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;

/**
 * @author peng-yongsheng
 */
public class InstanceReferencePersistenceWorker extends PersistenceWorker<InstanceReferenceMetric, InstanceReferenceMetric> {

    public InstanceReferencePersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return InstanceGraphNodeIdDefine.INSTANCE_REFERENCE_METRIC_PERSISTENCE_NODE_ID;
    }

    @Override protected IPersistenceDAO persistenceDAO() {
        return getModuleManager().find(StorageModule.NAME).getService(IInstanceReferenceMetricPersistenceDAO.class);
    }

    @Override protected boolean needMergeDBData() {
        return true;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<InstanceReferenceMetric, InstanceReferenceMetric, InstanceReferencePersistenceWorker> {

        public Factory(ModuleManager moduleManager, QueueCreatorService<InstanceReferenceMetric> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public InstanceReferencePersistenceWorker workerInstance(ModuleManager moduleManager) {
            return new InstanceReferencePersistenceWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
