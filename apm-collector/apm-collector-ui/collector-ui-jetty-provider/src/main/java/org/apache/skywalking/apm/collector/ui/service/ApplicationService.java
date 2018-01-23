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

package org.apache.skywalking.apm.collector.ui.service;

import java.util.List;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;

/**
 * @author peng-yongsheng
 */
public class ApplicationService {

    private final IInstanceUIDAO instanceDAO;
    private final ApplicationCacheService applicationCacheService;

    public ApplicationService(ModuleManager moduleManager) {
        this.instanceDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceUIDAO.class);
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
    }

    public List<Application> getApplications(long startTime, long endTime) {
        List<Application> applications = instanceDAO.getApplications(startTime, endTime);

        applications.forEach(application -> {
            String applicationCode = applicationCacheService.getApplicationCodeById(application.getId());
            application.setName(applicationCode);
        });
        return applications;
    }
}
