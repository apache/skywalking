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

package org.apache.skywalking.apm.collector.analysis.register.provider.register;

import org.apache.skywalking.apm.collector.analysis.register.define.graph.WorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.*;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.*;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.register.IApplicationRegisterDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ApplicationRegisterSerialWorker extends AbstractLocalAsyncWorker<Application, Application> {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationRegisterSerialWorker.class);

    private final IApplicationRegisterDAO applicationRegisterDAO;
    private final ApplicationCacheService applicationCacheService;

    private ApplicationRegisterSerialWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.applicationRegisterDAO = getModuleManager().find(StorageModule.NAME).getService(IApplicationRegisterDAO.class);
        this.applicationCacheService = getModuleManager().find(CacheModule.NAME).getService(ApplicationCacheService.class);
    }

    @Override public int id() {
        return WorkerIdDefine.APPLICATION_REGISTER_SERIAL_WORKER;
    }

    @Override protected void onWork(Application application) {
        if (logger.isDebugEnabled()) {
            logger.debug("register application, application code: {}", application.getApplicationCode());
        }

        int applicationId;

        if (BooleanUtils.valueToBoolean(application.getIsAddress())) {
            applicationId = applicationCacheService.getApplicationIdByAddressId(application.getAddressId());
        } else {
            applicationId = applicationCacheService.getApplicationIdByCode(application.getApplicationCode());
        }

        if (applicationId == 0) {
            Application newApplication;
            int min = applicationRegisterDAO.getMinApplicationId();
            if (min == 0) {
                Application userApplication = new Application();
                userApplication.setId(String.valueOf(Const.NONE_APPLICATION_ID));
                userApplication.setApplicationCode(Const.USER_CODE);
                userApplication.setApplicationId(Const.NONE_APPLICATION_ID);
                userApplication.setAddressId(Const.NONE);
                userApplication.setIsAddress(BooleanUtils.FALSE);
                applicationRegisterDAO.save(userApplication);

                newApplication = new Application();
                newApplication.setId("-1");
                newApplication.setApplicationId(-1);
                newApplication.setApplicationCode(application.getApplicationCode());
                newApplication.setAddressId(application.getAddressId());
                newApplication.setIsAddress(application.getIsAddress());
            } else {
                int max = applicationRegisterDAO.getMaxApplicationId();
                applicationId = IdAutoIncrement.INSTANCE.increment(min, max);

                newApplication = new Application();
                newApplication.setId(String.valueOf(applicationId));
                newApplication.setApplicationId(applicationId);
                newApplication.setApplicationCode(application.getApplicationCode());
                newApplication.setAddressId(application.getAddressId());
                newApplication.setIsAddress(application.getIsAddress());
            }
            applicationRegisterDAO.save(newApplication);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<Application, Application, ApplicationRegisterSerialWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ApplicationRegisterSerialWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationRegisterSerialWorker(moduleManager);
        }

        @Override public int queueSize() {
            return 256;
        }
    }
}
