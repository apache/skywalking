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

import java.util.*;
import java.util.concurrent.*;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public enum CacheUpdateTimer {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(CacheUpdateTimer.class);

    private Boolean isStarted = false;

    public void start(ModuleManager moduleManager) {
        logger.info("Cache update timer start");

        final long timeInterval = 3;

        if (!isStarted) {
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                new RunnableWithExceptionProtection(() -> update(moduleManager),
                    t -> logger.error("Cache update failure.", t)), 1, timeInterval, TimeUnit.SECONDS);

            this.isStarted = true;
        }
    }

    private void update(ModuleManager moduleManager) {
        IServiceInventoryCacheDAO serviceInventoryCacheDAO = moduleManager.find(StorageModule.NAME).provider().getService(IServiceInventoryCacheDAO.class);
        ServiceInventoryCache serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        List<ServiceInventory> serviceInventories = serviceInventoryCacheDAO.loadLastMappingUpdate();

        serviceInventories.forEach(serviceInventory -> {
            logger.info("Update mapping service id in the cache of service inventory, service id: {}, mapping service id: {}", serviceInventory.getSequence(), serviceInventory.getMappingServiceId());
            ServiceInventory cache = serviceInventoryCache.get(serviceInventory.getSequence());
            if (Objects.nonNull(cache)) {
                cache.setMappingServiceId(serviceInventory.getMappingServiceId());
                cache.setMappingLastUpdateTime(serviceInventory.getMappingLastUpdateTime());
            } else {
                logger.warn("Unable to found the id of {} in service inventory cache.", serviceInventory.getSequence());
            }
        });
    }
}