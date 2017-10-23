/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.cache.dao.IApplicationCacheDAO;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationCache {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationCache.class);

    private static Cache<String, Integer> CODE_CACHE = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();

    public static int get(String applicationCode) {
        IApplicationCacheDAO dao = (IApplicationCacheDAO)DAOContainer.INSTANCE.get(IApplicationCacheDAO.class.getName());

        int applicationId = 0;
        try {
            applicationId = CODE_CACHE.get(applicationCode, () -> dao.getApplicationId(applicationCode));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (applicationId == 0) {
            applicationId = dao.getApplicationId(applicationCode);
            if (applicationId != 0) {
                CODE_CACHE.put(applicationCode, applicationId);
            }
        }
        return applicationId;
    }

    private static Cache<Integer, String> ID_CACHE = CacheBuilder.newBuilder().maximumSize(1000).build();

    public static String get(int applicationId) {
        IApplicationCacheDAO dao = (IApplicationCacheDAO)DAOContainer.INSTANCE.get(IApplicationCacheDAO.class.getName());

        String applicationCode = Const.EMPTY_STRING;
        try {
            applicationCode = ID_CACHE.get(applicationId, () -> dao.getApplicationCode(applicationId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (StringUtils.isEmpty(applicationCode)) {
            applicationCode = dao.getApplicationCode(applicationId);
            if (StringUtils.isNotEmpty(applicationCode)) {
                CODE_CACHE.put(applicationCode, applicationId);
            }
        }
        return applicationCode;
    }
}
