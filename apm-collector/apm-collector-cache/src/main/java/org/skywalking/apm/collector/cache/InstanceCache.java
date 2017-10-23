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
import org.skywalking.apm.collector.cache.dao.IInstanceCacheDAO;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceCache {

    private static final Logger logger = LoggerFactory.getLogger(InstanceCache.class);

    private static Cache<Integer, Integer> INSTANCE_CACHE = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    public static int get(int applicationInstanceId) {
        IInstanceCacheDAO dao = (IInstanceCacheDAO)DAOContainer.INSTANCE.get(IInstanceCacheDAO.class.getName());

        int applicationId = 0;
        try {
            applicationId = INSTANCE_CACHE.get(applicationInstanceId, () -> dao.getApplicationId(applicationInstanceId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (applicationId == 0) {
            applicationId = dao.getApplicationId(applicationInstanceId);
            if (applicationId != 0) {
                INSTANCE_CACHE.put(applicationInstanceId, applicationId);
            }
        }
        return applicationId;
    }
}
