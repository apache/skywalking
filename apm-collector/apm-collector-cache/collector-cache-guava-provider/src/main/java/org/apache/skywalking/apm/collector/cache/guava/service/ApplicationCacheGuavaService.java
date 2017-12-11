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
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationCacheDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationCacheGuavaService implements ApplicationCacheService {

    private final Logger logger = LoggerFactory.getLogger(ApplicationCacheGuavaService.class);

    private final Cache<String, Integer> codeCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();

    private final ModuleManager moduleManager;
    private IApplicationCacheDAO applicationCacheDAO;

    public ApplicationCacheGuavaService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IApplicationCacheDAO getApplicationCacheDAO() {
        if (ObjectUtils.isEmpty(applicationCacheDAO)) {
            this.applicationCacheDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationCacheDAO.class);
        }
        return this.applicationCacheDAO;
    }

    public int get(String applicationCode) {
        int applicationId = 0;
        try {
            applicationId = codeCache.get(applicationCode, () -> getApplicationCacheDAO().getApplicationId(applicationCode));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (applicationId == 0) {
            applicationId = getApplicationCacheDAO().getApplicationId(applicationCode);
            if (applicationId != 0) {
                codeCache.put(applicationCode, applicationId);
            }
        }
        return applicationId;
    }

    private final Cache<Integer, String> idCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    public String get(int applicationId) {
        String applicationCode = Const.EMPTY_STRING;
        try {
            applicationCode = idCache.get(applicationId, () -> getApplicationCacheDAO().getApplicationCode(applicationId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (StringUtils.isEmpty(applicationCode)) {
            applicationCode = getApplicationCacheDAO().getApplicationCode(applicationId);
            if (StringUtils.isNotEmpty(applicationCode)) {
                codeCache.put(applicationCode, applicationId);
            }
        }
        return applicationCode;
    }
}
