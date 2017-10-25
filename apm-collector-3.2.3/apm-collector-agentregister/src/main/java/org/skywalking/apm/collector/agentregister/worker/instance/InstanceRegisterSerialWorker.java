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

import org.skywalking.apm.collector.agentregister.worker.instance.dao.IInstanceDAO;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.register.ApplicationDataDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
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
public class InstanceRegisterSerialWorker extends AbstractLocalAsyncWorker {

    private final Logger logger = LoggerFactory.getLogger(InstanceRegisterSerialWorker.class);

    public InstanceRegisterSerialWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected void onWork(Object message) throws WorkerException {
        if (message instanceof InstanceDataDefine.Instance) {
            InstanceDataDefine.Instance instance = (InstanceDataDefine.Instance)message;
            logger.debug("register instance, application id: {}, agentUUID: {}", instance.getApplicationId(), instance.getAgentUUID());

            IInstanceDAO dao = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
            int instanceId = dao.getInstanceId(instance.getApplicationId(), instance.getAgentUUID());
            if (instanceId == 0) {
//                int min = dao.getMinInstanceId();
//                if (min == 0) {
//                    instance.setId("1");
//                    instance.setInstanceId(1);
//                } else {
//                    int max = dao.getMaxInstanceId();
//                    instanceId = IdAutoIncrement.INSTANCE.increment(min, max);
//                    instance.setId(String.valueOf(instanceId));
//                    instance.setInstanceId(instanceId);
//                }
                int max = dao.getMaxInstanceId();
                if (max == 0) {
                    instance.setId("1");
                    instance.setInstanceId(1);
                } else {
                    instance.setId(String.valueOf(max + 1));
                    instance.setInstanceId(max + 1);
                }

                dao.save(instance);
            }
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<InstanceRegisterSerialWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public InstanceRegisterSerialWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new InstanceRegisterSerialWorker(role(), clusterContext);
        }

        @Override public int queueSize() {
            return 256;
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return InstanceRegisterSerialWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new ForeverFirstSelector();
        }

        @Override public DataDefine dataDefine() {
            return new ApplicationDataDefine();
        }
    }
}
