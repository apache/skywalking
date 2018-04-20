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
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.cache.IApplicationCacheDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Application;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public class ApplicationCacheGuavaService implements ApplicationCacheService {

    private final Cache<String, Integer> codeCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();
    private final Cache<Integer, Application> applicationCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    private final Cache<Integer, Integer> addressIdCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private final ModuleManager moduleManager;
    private IApplicationCacheDAO applicationCacheDAO;

    public ApplicationCacheGuavaService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IApplicationCacheDAO getApplicationCacheDAO() {
        if (isNull(applicationCacheDAO)) {
            this.applicationCacheDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationCacheDAO.class);
        }
        return this.applicationCacheDAO;
    }

    @Override public int getApplicationIdByCode(String applicationCode) {
        return CacheUtils.retrieveOrElse(codeCache, applicationCode,
            () -> getApplicationCacheDAO().getApplicationIdByCode(applicationCode), 0);
    }

    @Override public Application getApplicationById(int applicationId) {
        return CacheUtils.retrieve(applicationCache, applicationId,
            () -> getApplicationCacheDAO().getApplication(applicationId));
    }



    @Override public int getApplicationIdByAddressId(int addressId) {
        return CacheUtils.retrieveOrElse(addressIdCache, addressId,
            () -> getApplicationCacheDAO().getApplicationIdByAddressId(addressId), 0);
    }
}
