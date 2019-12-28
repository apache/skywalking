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
import org.apache.skywalking.oap.server.core.query.entity.ThreadMonitorTask;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profile.IThreadMonitorTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

import static java.util.Objects.isNull;

/**
 * handle thread monitor task queries
 *
 * @author MrPro
 */
public class ThreadMonitorTaskQueryService implements Service {
    private final ModuleManager moduleManager;
    private IThreadMonitorTaskQueryDAO threadMonitorTaskQueryDAO;
    private ServiceInventoryCache serviceInventoryCache;

    public ThreadMonitorTaskQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IThreadMonitorTaskQueryDAO getThreadMonitorTaskDAO() {
        if (threadMonitorTaskQueryDAO == null) {
            this.threadMonitorTaskQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IThreadMonitorTaskQueryDAO.class);
        }
        return threadMonitorTaskQueryDAO;
    }

    private ServiceInventoryCache getServiceInventoryCache() {
        if (isNull(serviceInventoryCache)) {
            this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        }
        return serviceInventoryCache;
    }

    /**
     * search thread monitor task list
     * @param serviceId monitor service
     * @param endpointName endpoint name to monitored
     * @param searchStartTimeBucket start time bucket
     * @param searchEndTimeBucket end time bucket
     * @return
     */
    public List<ThreadMonitorTask> getTaskList(Integer serviceId, String endpointName, long searchStartTimeBucket, long searchEndTimeBucket) throws IOException {
        final List<ThreadMonitorTask> tasks = getThreadMonitorTaskDAO().getTaskList(serviceId, endpointName, searchStartTimeBucket, searchEndTimeBucket);

        // add service name
        if (CollectionUtils.isNotEmpty(tasks)) {
            final ServiceInventoryCache serviceInventoryCache = getServiceInventoryCache();
            for (ThreadMonitorTask task : tasks) {
                final ServiceInventory serviceInventory = serviceInventoryCache.get(task.getServiceId());
                if (serviceInventory != null) {
                    task.setServiceName(serviceInventory.getName());
                }
            }
        }

        return tasks;
    }

}
