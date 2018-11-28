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

package org.apache.skywalking.oap.server.core.register.service;

import java.util.Objects;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.EndpointInventoryCache;
import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.register.worker.InventoryProcess;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class EndpointInventoryRegister implements IEndpointInventoryRegister {

    private static final Logger logger = LoggerFactory.getLogger(EndpointInventoryRegister.class);

    private final ModuleManager moduleManager;
    private EndpointInventoryCache cacheService;

    public EndpointInventoryRegister(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private EndpointInventoryCache getCacheService() {
        if (isNull(cacheService)) {
            cacheService = moduleManager.find(CoreModule.NAME).provider().getService(EndpointInventoryCache.class);
        }
        return cacheService;
    }

    @Override public int getOrCreate(int serviceId, String endpointName, DetectPoint detectPoint) {
        int endpointId = getCacheService().getEndpointId(serviceId, endpointName, detectPoint.ordinal());

        if (endpointId == Const.NONE) {
            EndpointInventory endpointInventory = new EndpointInventory();
            endpointInventory.setServiceId(serviceId);
            endpointInventory.setName(endpointName);
            endpointInventory.setDetectPoint(detectPoint.ordinal());

            long now = System.currentTimeMillis();
            endpointInventory.setRegisterTime(now);
            endpointInventory.setHeartbeatTime(now);

            InventoryProcess.INSTANCE.in(endpointInventory);
        }
        return endpointId;
    }

    @Override public int get(int serviceId, String endpointName, int detectPoint) {
        return getCacheService().getEndpointId(serviceId, endpointName, detectPoint);
    }

    @Override public void heartbeat(int endpointId, long heartBeatTime) {
        EndpointInventory endpointInventory = getCacheService().get(endpointId);
        if (Objects.nonNull(endpointInventory)) {
            endpointInventory.setHeartbeatTime(heartBeatTime);

            InventoryProcess.INSTANCE.in(endpointInventory);
        } else {
            logger.warn("Endpoint {} heartbeat, but not found in storage.");
        }
    }
}
