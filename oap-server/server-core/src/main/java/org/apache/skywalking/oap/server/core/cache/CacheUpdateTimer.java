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

package org.apache.skywalking.oap.server.core.cache;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTask;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.*;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public enum CacheUpdateTimer {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(CacheUpdateTimer.class);

    private Boolean isStarted = false;

    public void start(ModuleDefineHolder moduleDefineHolder) {
        logger.info("Cache updateServiceInventory timer start");

        final long timeInterval = 10;

        if (!isStarted) {
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                new RunnableWithExceptionProtection(() -> update(moduleDefineHolder),
                    t -> logger.error("Cache update failure.", t)), 1, timeInterval, TimeUnit.SECONDS);

            this.isStarted = true;
        }
    }

    private void update(ModuleDefineHolder moduleDefineHolder) {
        updateServiceInventory(moduleDefineHolder);
        updateServiceInstanceInventory(moduleDefineHolder);
        updateNetAddressInventory(moduleDefineHolder);
        updateProfileTask(moduleDefineHolder);
    }

    private void updateServiceInventory(ModuleDefineHolder moduleDefineHolder) {
        IServiceInventoryCacheDAO serviceInventoryCacheDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(IServiceInventoryCacheDAO.class);
        ServiceInventoryCache serviceInventoryCache = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        List<ServiceInventory> serviceInventories = serviceInventoryCacheDAO.loadLastUpdate(System.currentTimeMillis() - 60000);

        serviceInventories.forEach(serviceInventory -> {
            ServiceInventory cache = serviceInventoryCache.get(serviceInventory.getSequence());
            if (Objects.nonNull(cache)) {
                if (cache.getMappingServiceId() != serviceInventory.getMappingServiceId()) {
                    cache.setMappingServiceId(serviceInventory.getMappingServiceId());
                    cache.setServiceNodeType(serviceInventory.getServiceNodeType());
                    cache.setProperties(serviceInventory.getProperties());
                    logger.info("Update the cache of service inventory, service id: {}", serviceInventory.getSequence());
                }
            } else {
                logger.warn("Unable to found the id of {} in service inventory cache.", serviceInventory.getSequence());
            }
        });
    }

    private void updateServiceInstanceInventory(ModuleDefineHolder moduleDefineHolder) {
        IServiceInstanceInventoryCacheDAO instanceInventoryCacheDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(IServiceInstanceInventoryCacheDAO.class);
        ServiceInstanceInventoryCache instanceInventoryCache = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class);
        List<ServiceInstanceInventory> instanceInventories = instanceInventoryCacheDAO.loadLastUpdate(System.currentTimeMillis() - 60000);

        instanceInventories.forEach(instanceInventory -> {
            ServiceInstanceInventory cache = instanceInventoryCache.get(instanceInventory.getSequence());
            if (Objects.nonNull(cache)) {
                if (cache.getMappingServiceInstanceId() != instanceInventory.getMappingServiceInstanceId()) {
                    cache.setMappingServiceInstanceId(instanceInventory.getMappingServiceInstanceId());
                    cache.setServiceInstanceNodeType(instanceInventory.getServiceInstanceNodeType());
                    cache.setProperties(instanceInventory.getProperties());
                    logger.info("Update the cache of service instance inventory, instance id: {}", instanceInventory.getSequence());
                }
            } else {
                logger.warn("Unable to found the id of {} in service instance inventory cache.", instanceInventory.getSequence());
            }
        });
    }

    private void updateNetAddressInventory(ModuleDefineHolder moduleDefineHolder) {
        INetworkAddressInventoryCacheDAO addressInventoryCacheDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(INetworkAddressInventoryCacheDAO.class);
        NetworkAddressInventoryCache addressInventoryCache = moduleDefineHolder.find(CoreModule.NAME).provider().getService(NetworkAddressInventoryCache.class);
        List<NetworkAddressInventory> addressInventories = addressInventoryCacheDAO.loadLastUpdate(System.currentTimeMillis() - 60000);

        addressInventories.forEach(addressInventory -> {
            NetworkAddressInventory cache = addressInventoryCache.get(addressInventory.getSequence());
            if (Objects.nonNull(cache)) {
                if (!cache.getNetworkAddressNodeType().equals(addressInventory.getNetworkAddressNodeType())) {
                    cache.setNetworkAddressNodeType(addressInventory.getNetworkAddressNodeType());
                    logger.info("Update the cache of net address inventory, address id: {}", addressInventory.getSequence());
                }
            } else {
                logger.warn("Unable to found the id of {} in net address inventory cache.", addressInventory.getSequence());
            }
        });
    }

    /**
     * update all profile task list for each service
     * @param moduleDefineHolder
     */
    private void updateProfileTask(ModuleDefineHolder moduleDefineHolder) {
        IProfileTaskQueryDAO profileTaskQueryDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(IProfileTaskQueryDAO.class);
        ProfileTaskCache profileTaskCache = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ProfileTaskCache.class);
        try {
            final List<ProfileTask> taskList = profileTaskQueryDAO.getTaskList(null, null, profileTaskCache.getCacheStartTimeBucket(), profileTaskCache.getCacheEndTimeBucket(), null);

            taskList.stream().collect(Collectors.groupingBy(t -> t.getServiceId())).entrySet().stream().forEach(e -> {
                final Integer serviceId = e.getKey();
                final List<ProfileTask> profileTasks = e.getValue();

                profileTaskCache.saveTaskList(serviceId, profileTasks);
            });
        } catch (IOException e) {
            logger.warn("Unable to update profile task cache", e);
        }
    }
}