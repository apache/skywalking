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

import com.google.common.cache.*;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.register.endpoint.Endpoint;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IEndpointCacheDAO;
import org.apache.skywalking.oap.server.library.module.*;
import org.slf4j.*;

import static java.util.Objects.*;

/**
 * @author peng-yongsheng
 */
public class EndpointCacheService implements Service {

    private static final Logger logger = LoggerFactory.getLogger(EndpointCacheService.class);

    private final ModuleManager moduleManager;
    private IEndpointCacheDAO cacheDAO;

    public EndpointCacheService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private final Cache<String, Integer> idCache = CacheBuilder.newBuilder().initialCapacity(1000).maximumSize(1000000).build();

    private final Cache<Integer, Endpoint> sequenceCache = CacheBuilder.newBuilder().initialCapacity(1000).maximumSize(1000000).build();

    public int get(int serviceId, String serviceName, int srcSpanType) {
        String id = serviceId + Const.ID_SPLIT + serviceName + Const.ID_SPLIT + srcSpanType;

        int endpointId = 0;

        try {
            endpointId = idCache.get(id, () -> getCacheDAO().get(id));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (serviceId == 0) {
            endpointId = getCacheDAO().get(id);
            if (endpointId != 0) {
                idCache.put(id, endpointId);
            }
        }
        return endpointId;
    }

    public Endpoint get(int endpointId) {
        Endpoint endpoint = null;
        try {
            endpoint = sequenceCache.get(endpointId, () -> getCacheDAO().get(endpointId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (isNull(endpoint)) {
            endpoint = getCacheDAO().get(endpointId);
            if (nonNull(endpoint)) {
                sequenceCache.put(endpointId, endpoint);
            } else {
                logger.warn("Endpoint id {} is not in cache and persistent storage.", endpointId);
            }
        }

        return endpoint;
    }

    private IEndpointCacheDAO getCacheDAO() {
        if (isNull(cacheDAO)) {
            cacheDAO = moduleManager.find(StorageModule.NAME).getService(IEndpointCacheDAO.class);
        }
        return cacheDAO;
    }
}
