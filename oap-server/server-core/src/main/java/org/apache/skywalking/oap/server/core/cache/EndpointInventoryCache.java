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
import java.util.Objects;
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
public class EndpointInventoryCache implements Service {

    private static final Logger logger = LoggerFactory.getLogger(EndpointInventoryCache.class);

    private final ModuleManager moduleManager;
    private final EndpointInventory userEndpoint;
    private final Cache<String, Integer> endpointNameCache = CacheBuilder.newBuilder().initialCapacity(5000).maximumSize(100000).build();

    private final Cache<Integer, EndpointInventory> endpointIdCache = CacheBuilder.newBuilder().initialCapacity(5000).maximumSize(100000).build();

    private IEndpointInventoryCacheDAO cacheDAO;

    public EndpointInventoryCache(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;

        this.userEndpoint = new EndpointInventory();
        this.userEndpoint.setSequence(Const.USER_ENDPOINT_ID);
        this.userEndpoint.setName(Const.USER_CODE);
        this.userEndpoint.setServiceId(Const.USER_SERVICE_ID);
    }

    private IEndpointInventoryCacheDAO getCacheDAO() {
        if (isNull(cacheDAO)) {
            cacheDAO = moduleManager.find(StorageModule.NAME).provider().getService(IEndpointInventoryCacheDAO.class);
        }
        return cacheDAO;
    }

    public int getEndpointId(int serviceId, String endpointName, int detectPoint) {
        String id = EndpointInventory.buildId(serviceId, endpointName, detectPoint);

        Integer endpointId = endpointNameCache.getIfPresent(id);

        if (Objects.isNull(endpointId) || endpointId == Const.NONE) {
            endpointId = getCacheDAO().getEndpointId(serviceId, endpointName, detectPoint);
            if (endpointId != Const.NONE) {
                endpointNameCache.put(id, endpointId);
            }
        }
        return endpointId;
    }

    public EndpointInventory get(int endpointId) {
        if (Const.USER_ENDPOINT_ID == endpointId) {
            return userEndpoint;
        }

        EndpointInventory endpointInventory = endpointIdCache.getIfPresent(endpointId);

        if (isNull(endpointInventory)) {
            endpointInventory = getCacheDAO().get(endpointId);
            if (nonNull(endpointInventory)) {
                endpointIdCache.put(endpointId, endpointInventory);
            } else {
                logger.warn("EndpointInventory id {} is not in cache and persistent storage.", endpointId);
            }
        }

        return endpointInventory;
    }
}
