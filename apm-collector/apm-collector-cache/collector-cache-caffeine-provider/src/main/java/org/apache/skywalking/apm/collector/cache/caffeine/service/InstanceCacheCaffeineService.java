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

package org.apache.skywalking.apm.collector.cache.caffeine.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.cache.IInstanceCacheDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class InstanceCacheCaffeineService implements InstanceCacheService {

    private final Logger logger = LoggerFactory.getLogger(InstanceCacheCaffeineService.class);

    private final Cache<Integer, Integer> applicationIdCache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).initialCapacity(100).maximumSize(5000).build();

    private final Cache<String, Integer> agentUUIDCache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).initialCapacity(100).maximumSize(5000).build();

    private final Cache<String, Integer> addressIdCache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).initialCapacity(100).maximumSize(5000).build();

    private final ModuleManager moduleManager;
    private IInstanceCacheDAO instanceCacheDAO;

    public InstanceCacheCaffeineService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IInstanceCacheDAO getInstanceCacheDAO() {
        if (isNull(instanceCacheDAO)) {
            this.instanceCacheDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceCacheDAO.class);
        }
        return this.instanceCacheDAO;
    }

    @Override public int getApplicationId(int instanceId) {
        int applicationId = 0;
        try {
            Integer value = applicationIdCache.get(instanceId, key -> getInstanceCacheDAO().getApplicationId(key));
            applicationId = value == null ? 0 : value;
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (applicationId == 0) {
            applicationId = getInstanceCacheDAO().getApplicationId(instanceId);
            if (applicationId != 0) {
                applicationIdCache.put(instanceId, applicationId);
            }
        }
        return applicationId;
    }

    @Override public int getInstanceIdByAgentUUID(int applicationId, String agentUUID) {
        String cacheKey = applicationId + Const.ID_SPLIT + agentUUID;

        int instanceId = 0;
        try {
            Integer value = agentUUIDCache.get(cacheKey, key -> getInstanceCacheDAO().getInstanceIdByAgentUUID(applicationId, agentUUID));
            instanceId = value == null ? 0 : value;
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (instanceId == 0) {
            instanceId = getInstanceCacheDAO().getInstanceIdByAgentUUID(applicationId, agentUUID);
            if (applicationId != 0) {
                agentUUIDCache.put(cacheKey, instanceId);
            }
        }
        return instanceId;
    }

    @Override public int getInstanceIdByAddressId(int applicationId, int addressId) {
        String cacheKey = applicationId + Const.ID_SPLIT + addressId;

        int instanceId = 0;
        try {
            Integer value = addressIdCache.get(cacheKey, key -> getInstanceCacheDAO().getInstanceIdByAddressId(applicationId, addressId));
            instanceId = value == null ? 0 : value;
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (instanceId == 0) {
            instanceId = getInstanceCacheDAO().getInstanceIdByAddressId(applicationId, addressId);
            if (applicationId != 0) {
                addressIdCache.put(cacheKey, instanceId);
            }
        }
        return instanceId;
    }
}
