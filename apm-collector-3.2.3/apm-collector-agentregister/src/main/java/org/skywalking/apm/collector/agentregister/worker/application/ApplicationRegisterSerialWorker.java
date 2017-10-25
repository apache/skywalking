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

package org.skywalking.apm.collector.agentregister.worker.application;

import org.skywalking.apm.collector.agentregister.worker.IdAutoIncrement;
import org.skywalking.apm.collector.agentregister.worker.application.dao.IApplicationDAO;
import org.skywalking.apm.collector.cache.dao.IApplicationCacheDAO;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.register.ApplicationDataDefine;
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
public class ApplicationRegisterSerialWorker extends AbstractLocalAsyncWorker {

    private final Logger logger = LoggerFactory.getLogger(ApplicationRegisterSerialWorker.class);

    public ApplicationRegisterSerialWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected void onWork(Object message) throws WorkerException {
        if (message instanceof ApplicationDataDefine.Application) {
            ApplicationDataDefine.Application application = (ApplicationDataDefine.Application)message;
            logger.debug("register application, application code: {}", application.getApplicationCode());

            IApplicationCacheDAO cacheDao = (IApplicationCacheDAO)DAOContainer.INSTANCE.get(IApplicationCacheDAO.class.getName());
            int applicationId = cacheDao.getApplicationId(application.getApplicationCode());

            IApplicationDAO dao = (IApplicationDAO)DAOContainer.INSTANCE.get(IApplicationDAO.class.getName());
            if (applicationId == 0) {
                int min = dao.getMinApplicationId();
                if (min == 0) {
                    ApplicationDataDefine.Application userApplication = new ApplicationDataDefine.Application(String.valueOf(Const.USER_ID), Const.USER_CODE, Const.USER_ID);
                    dao.save(userApplication);

                    application.setApplicationId(-1);
                    application.setId("-1");
                } else {
                    int max = dao.getMaxApplicationId();
                    applicationId = IdAutoIncrement.INSTANCE.increment(min, max);
                    application.setApplicationId(applicationId);
                    application.setId(String.valueOf(applicationId));
                }
                dao.save(application);
            }
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ApplicationRegisterSerialWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public ApplicationRegisterSerialWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new ApplicationRegisterSerialWorker(role(), clusterContext);
        }

        @Override public int queueSize() {
            return 256;
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ApplicationRegisterSerialWorker.class.getSimpleName();
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
