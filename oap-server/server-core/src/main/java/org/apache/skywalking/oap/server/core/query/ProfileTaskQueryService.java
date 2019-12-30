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
package org.apache.skywalking.oap.server.core.query;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTask;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

import static java.util.Objects.isNull;

/**
 * handle profile task queries
 *
 * @author MrPro
 */
public class ProfileTaskQueryService implements Service {
    private final ModuleManager moduleManager;
    private IProfileTaskQueryDAO profileTaskQueryDAO;
    private ServiceInventoryCache serviceInventoryCache;

    public ProfileTaskQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IProfileTaskQueryDAO getProfileTaskDAO() {
        if (profileTaskQueryDAO == null) {
            this.profileTaskQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IProfileTaskQueryDAO.class);
        }
        return profileTaskQueryDAO;
    }

    private ServiceInventoryCache getServiceInventoryCache() {
        if (isNull(serviceInventoryCache)) {
            this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        }
        return serviceInventoryCache;
    }

    /**
     * search profile task list
     * @param serviceId monitor service
     * @param endpointName endpoint name to monitored
     * @return
     */
    public List<ProfileTask> getTaskList(Integer serviceId, String endpointName) throws IOException {
        final List<ProfileTask> tasks = getProfileTaskDAO().getTaskList(serviceId, endpointName, null, null, null);

        // add service name
        if (CollectionUtils.isNotEmpty(tasks)) {
            final ServiceInventoryCache serviceInventoryCache = getServiceInventoryCache();
            for (ProfileTask task : tasks) {
                final ServiceInventory serviceInventory = serviceInventoryCache.get(task.getServiceId());
                if (serviceInventory != null) {
                    task.setServiceName(serviceInventory.getName());
                }
            }
        }

        return tasks;
    }

}
