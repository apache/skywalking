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
import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IEndpointInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.module.*;
import org.slf4j.*;

import static java.util.Objects.*;

/**
 * @author peng-yongsheng
 */
public class EndpointInventoryCacheService implements Service {

    private static final Logger logger = LoggerFactory.getLogger(EndpointInventoryCacheService.class);

    private final ModuleManager moduleManager;
    private IEndpointInventoryCacheDAO cacheDAO;

    public EndpointInventoryCacheService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private final Cache<String, Integer> idCache = CacheBuilder.newBuilder().initialCapacity(1000).maximumSize(1000000).build();

    private final Cache<Integer, EndpointInventory> sequenceCache = CacheBuilder.newBuilder().initialCapacity(1000).maximumSize(1000000).build();

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

    public EndpointInventory get(int endpointId) {
        EndpointInventory endpointInventory = null;
        try {
            endpointInventory = sequenceCache.get(endpointId, () -> getCacheDAO().get(endpointId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (isNull(endpointInventory)) {
            endpointInventory = getCacheDAO().get(endpointId);
            if (nonNull(endpointInventory)) {
                sequenceCache.put(endpointId, endpointInventory);
            } else {
                logger.warn("EndpointInventory id {} is not in cache and persistent storage.", endpointId);
            }
        }

        return endpointInventory;
    }

    private IEndpointInventoryCacheDAO getCacheDAO() {
        if (isNull(cacheDAO)) {
            cacheDAO = moduleManager.find(StorageModule.NAME).getService(IEndpointInventoryCacheDAO.class);
        }
        return cacheDAO;
    }
}
