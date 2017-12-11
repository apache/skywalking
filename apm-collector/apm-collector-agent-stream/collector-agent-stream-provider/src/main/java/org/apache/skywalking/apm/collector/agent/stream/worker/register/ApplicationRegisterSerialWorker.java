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


package org.apache.skywalking.apm.collector.agent.stream.worker.register;

import org.apache.skywalking.apm.collector.agent.stream.IdAutoIncrement;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationRegisterDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorker;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.stream.worker.base.WorkerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationRegisterSerialWorker extends AbstractLocalAsyncWorker<Application, Application> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationRegisterSerialWorker.class);

    private final IApplicationRegisterDAO applicationRegisterDAO;
    private final ApplicationCacheService applicationCacheService;

    public ApplicationRegisterSerialWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.applicationRegisterDAO = getModuleManager().find(StorageModule.NAME).getService(IApplicationRegisterDAO.class);
        this.applicationCacheService = getModuleManager().find(CacheModule.NAME).getService(ApplicationCacheService.class);
    }

    @Override public int id() {
        return 101;
    }

    @Override protected void onWork(Application application) throws WorkerException {
        logger.debug("register application, application code: {}", application.getApplicationCode());
        int applicationId = applicationCacheService.get(application.getApplicationCode());

        if (applicationId == 0) {
            Application newApplication;
            int min = applicationRegisterDAO.getMinApplicationId();
            if (min == 0) {
                Application userApplication = new Application(String.valueOf(Const.USER_ID));
                userApplication.setApplicationCode(Const.USER_CODE);
                userApplication.setApplicationId(Const.USER_ID);
                applicationRegisterDAO.save(userApplication);

                newApplication = new Application("-1");
                newApplication.setApplicationId(-1);
                newApplication.setApplicationCode(application.getApplicationCode());
            } else {
                int max = applicationRegisterDAO.getMaxApplicationId();
                applicationId = IdAutoIncrement.INSTANCE.increment(min, max);

                newApplication = new Application(String.valueOf(applicationId));
                newApplication.setApplicationId(applicationId);
                newApplication.setApplicationCode(application.getApplicationCode());
            }
            applicationRegisterDAO.save(newApplication);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<Application, Application, ApplicationRegisterSerialWorker> {

        public Factory(ModuleManager moduleManager, QueueCreatorService<Application> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public ApplicationRegisterSerialWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationRegisterSerialWorker(moduleManager);
        }

        @Override public int queueSize() {
            return 256;
        }
    }
}
