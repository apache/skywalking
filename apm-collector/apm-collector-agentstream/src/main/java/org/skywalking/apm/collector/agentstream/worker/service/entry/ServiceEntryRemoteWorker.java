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

package org.skywalking.apm.collector.agentstream.worker.service.entry;

import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.service.ServiceEntryDataDefine;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorker;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.selector.HashCodeSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;

/**
 * @author peng-yongsheng
 */
public class ServiceEntryRemoteWorker extends AbstractRemoteWorker {

    protected ServiceEntryRemoteWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {

    }

    @Override protected void onWork(Object message) throws WorkerException {
        getClusterContext().lookup(ServiceEntryPersistenceWorker.WorkerRole.INSTANCE).tell(message);
    }

    public static class Factory extends AbstractRemoteWorkerProvider<ServiceEntryRemoteWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public ServiceEntryRemoteWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new ServiceEntryRemoteWorker(role(), clusterContext);
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ServiceEntryRemoteWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }

        @Override public DataDefine dataDefine() {
            return new ServiceEntryDataDefine();
        }
    }
}
