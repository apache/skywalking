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

package org.skywalking.apm.collector.agentregister.worker.instance;

import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorker;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.selector.ForeverFirstSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceRegisterRemoteWorker extends AbstractRemoteWorker {

    private final Logger logger = LoggerFactory.getLogger(InstanceRegisterRemoteWorker.class);

    protected InstanceRegisterRemoteWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
    }

    @Override protected void onWork(Object message) throws WorkerException {
        InstanceDataDefine.Instance instance = (InstanceDataDefine.Instance)message;
        logger.debug("application id: {}, agentUUID: {}, register time: {}", instance.getApplicationId(), instance.getAgentUUID(), instance.getRegisterTime());
        getClusterContext().lookup(InstanceRegisterSerialWorker.WorkerRole.INSTANCE).tell(instance);
    }

    public static class Factory extends AbstractRemoteWorkerProvider<InstanceRegisterRemoteWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public InstanceRegisterRemoteWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new InstanceRegisterRemoteWorker(role(), clusterContext);
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return InstanceRegisterRemoteWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new ForeverFirstSelector();
        }

        @Override public DataDefine dataDefine() {
            return new InstanceDataDefine();
        }
    }
}
