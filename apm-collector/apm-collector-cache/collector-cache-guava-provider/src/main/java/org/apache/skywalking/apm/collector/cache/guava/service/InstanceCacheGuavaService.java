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

package org.apache.skywalking.apm.collector.cache.guava.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.skywalking.apm.collector.cache.guava.CacheUtils;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.cache.IInstanceCacheDAO;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class InstanceCacheGuavaService implements InstanceCacheService {

    private final Cache<Integer, Integer> applicationIdCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    private final Cache<String, Integer> agentUUIDCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    private final Cache<String, Integer> addressIdCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    private final ModuleManager moduleManager;
    private IInstanceCacheDAO instanceCacheDAO;

    public InstanceCacheGuavaService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IInstanceCacheDAO getInstanceCacheDAO() {
        if (isNull(instanceCacheDAO)) {
            this.instanceCacheDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceCacheDAO.class);
        }
        return this.instanceCacheDAO;
    }

    @Override public int getApplicationId(int instanceId) {
        return CacheUtils.retrieveOrElse(applicationIdCache, instanceId,
            () -> getInstanceCacheDAO().getApplicationId(instanceId), 0);
    }

    @Override public int getInstanceIdByAgentUUID(int applicationId, String agentUUID) {
        String key = applicationId + Const.ID_SPLIT + agentUUID;
        return CacheUtils.retrieveOrElse(agentUUIDCache, key,
            () -> getInstanceCacheDAO().getInstanceIdByAgentUUID(applicationId, agentUUID), 0);
    }

    @Override public int getInstanceIdByAddressId(int applicationId, int addressId) {
        String key = applicationId + Const.ID_SPLIT + addressId;
        return CacheUtils.retrieveOrElse(addressIdCache, key,
            () -> getInstanceCacheDAO().getInstanceIdByAddressId(applicationId, addressId), 0);
    }
}
