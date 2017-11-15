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
import org.skywalking.apm.collector.storage.dao.INodeComponentPersistenceDAO;
import org.skywalking.apm.collector.storage.table.node.NodeComponent;
import org.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;

/**
 * @author peng-yongsheng
 */
public class NodeComponentPersistenceWorker extends PersistenceWorker<NodeComponent, NodeComponent> {

    public NodeComponentPersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return NodeComponentPersistenceWorker.class.hashCode();
    }

    @Override protected boolean needMergeDBData() {
        return true;
    }

    @Override protected IPersistenceDAO persistenceDAO() {
        return getModuleManager().find(StorageModule.NAME).getService(INodeComponentPersistenceDAO.class);
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeComponent, NodeComponent, NodeComponentPersistenceWorker> {

        public Factory(ModuleManager moduleManager, QueueCreatorService<NodeComponent> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public NodeComponentPersistenceWorker workerInstance(ModuleManager moduleManager) {
            return new NodeComponentPersistenceWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
