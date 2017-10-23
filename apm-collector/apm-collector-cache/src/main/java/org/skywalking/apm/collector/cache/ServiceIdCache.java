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
import org.skywalking.apm.collector.cache.dao.IServiceNameCacheDAO;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceIdCache {

    private static final Logger logger = LoggerFactory.getLogger(ServiceIdCache.class);

    //TODO size configuration
    private static Cache<String, Integer> SERVICE_CACHE = CacheBuilder.newBuilder().maximumSize(1000).build();

    public static int get(int applicationId, String serviceName) {
        IServiceNameCacheDAO dao = (IServiceNameCacheDAO)DAOContainer.INSTANCE.get(IServiceNameCacheDAO.class.getName());

        int serviceId = 0;
        try {
            serviceId = SERVICE_CACHE.get(applicationId + Const.ID_SPLIT + serviceName, () -> dao.getServiceId(applicationId, serviceName));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (serviceId == 0) {
            serviceId = dao.getServiceId(applicationId, serviceName);
            if (serviceId != 0) {
                SERVICE_CACHE.put(applicationId + Const.ID_SPLIT + serviceName, serviceId);
            }
        }
        return serviceId;
    }
}
