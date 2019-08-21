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

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.module.*;

/**
 * @author wusheng
 */
public class LogQueryService implements Service {
    
    private final ModuleManager moduleManager;
    private ILogQueryDAO logQueryDAO;
    private ServiceInventoryCache serviceInventoryCache;
    private ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private EndpointInventoryCache endpointInventoryCache;

    public LogQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ILogQueryDAO getLogQueryDAO() {
        if (logQueryDAO == null) {
            this.logQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ILogQueryDAO.class);
        }
        return logQueryDAO;
    }

    private ServiceInventoryCache getServiceInventoryCache() {
        if (serviceInventoryCache == null) {
            this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        }
        return serviceInventoryCache;
    }

    private ServiceInstanceInventoryCache getServiceInstanceInventoryCache() {
        if (serviceInstanceInventoryCache == null) {
            this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class);
        }
        return serviceInstanceInventoryCache;
    }

    private EndpointInventoryCache getEndpointInventoryCache() {
        if (endpointInventoryCache == null) {
            this.endpointInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(EndpointInventoryCache.class);
        }
        return endpointInventoryCache;
    }

    public Logs queryLogs(final String metricName, int serviceId, int serviceInstanceId, int endpointId,
        String traceId, LogState state, String stateCode, Pagination paging, final long startTB,
        final long endTB) throws IOException {
        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(paging);

        Logs logs = getLogQueryDAO().queryLogs(metricName, serviceId, serviceInstanceId, endpointId,
            traceId, state, stateCode, paging, page.getFrom(), page.getLimit(), startTB, endTB);
        logs.getLogs().forEach(log -> {
            if (log.getServiceId() != Const.NONE) {
                log.setServiceName(getServiceInventoryCache().get(log.getServiceId()).getName());
            }
            if (log.getServiceInstanceId() != Const.NONE) {
                log.setServiceInstanceName(getServiceInstanceInventoryCache().get(log.getServiceInstanceId()).getName());
            }
            if (log.getEndpointId() != Const.NONE) {
                log.setEndpointName(getEndpointInventoryCache().get(log.getEndpointId()).getName());
            }
        });
        return logs;
    }
}
