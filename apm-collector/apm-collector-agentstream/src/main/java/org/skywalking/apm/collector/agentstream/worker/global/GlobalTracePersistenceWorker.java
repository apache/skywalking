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

package org.skywalking.apm.collector.agentstream.worker.global;

import org.skywalking.apm.collector.agentstream.worker.global.dao.IGlobalTraceDAO;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.global.GlobalTraceDataDefine;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.stream.worker.selector.RollingSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;

/**
 * @author peng-yongsheng
 */
public class GlobalTracePersistenceWorker extends PersistenceWorker {

    public GlobalTracePersistenceWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected boolean needMergeDBData() {
        return false;
    }

    @Override protected IPersistenceDAO persistenceDAO() {
        return (IPersistenceDAO)DAOContainer.INSTANCE.get(IGlobalTraceDAO.class.getName());
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<GlobalTracePersistenceWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public GlobalTracePersistenceWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new GlobalTracePersistenceWorker(role(), clusterContext);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return GlobalTracePersistenceWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }

        @Override public DataDefine dataDefine() {
            return new GlobalTraceDataDefine();
        }
    }
}
