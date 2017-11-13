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

package org.skywalking.apm.collector.agent.stream.worker.trace.node;

import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.INodeMappingPersistenceDAO;
import org.skywalking.apm.collector.storage.service.DAOService;
import org.skywalking.apm.collector.storage.table.node.NodeMapping;
import org.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;

/**
 * @author peng-yongsheng
 */
public class NodeMappingPersistenceWorker extends PersistenceWorker<NodeMapping, NodeMapping> {

    private final DAOService daoService;

    public NodeMappingPersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.daoService = getModuleManager().find(StorageModule.NAME).getService(DAOService.class);
    }

    @Override public int id() {
        return NodeMappingPersistenceWorker.class.hashCode();
    }

    @Override protected boolean needMergeDBData() {
        return true;
    }

    @Override protected IPersistenceDAO persistenceDAO() {
        return (IPersistenceDAO)daoService.get(INodeMappingPersistenceDAO.class);
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeMapping, NodeMapping, NodeMappingPersistenceWorker> {

        public Factory(ModuleManager moduleManager, QueueCreatorService<NodeMapping> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public NodeMappingPersistenceWorker workerInstance(ModuleManager moduleManager) {
            return new NodeMappingPersistenceWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
