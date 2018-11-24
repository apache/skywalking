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
import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnIds;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * @author peng-yongsheng
 */
public class AggregationQueryService implements Service {

    private final ModuleManager moduleManager;
    private IAggregationQueryDAO aggregationQueryDAO;

    public AggregationQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IAggregationQueryDAO getAggregationQueryDAO() {
        if (aggregationQueryDAO == null) {
            aggregationQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IAggregationQueryDAO.class);
        }
        return aggregationQueryDAO;
    }

    public List<TopNEntity> getServiceTopN(final String indName, final int topN, final Step step, final long startTB,
        final long endTB, final Order order) throws IOException {
        List<TopNEntity> topNEntities = getAggregationQueryDAO().getServiceTopN(indName, ValueColumnIds.INSTANCE.getValueCName(indName), topN, step, startTB, endTB, order);
        for (TopNEntity entity : topNEntities) {
            ServiceInventory inventory = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class).get(Integer.valueOf(entity.getId()));
            if (inventory != null) {
                entity.setName(inventory.getName());
            }
        }
        return topNEntities;
    }

    public List<TopNEntity> getAllServiceInstanceTopN(final String indName, final int topN, final Step step,
        final long startTB, final long endTB, final Order order) throws IOException {
        List<TopNEntity> topNEntities = getAggregationQueryDAO().getAllServiceInstanceTopN(indName, ValueColumnIds.INSTANCE.getValueCName(indName), topN, step, startTB, endTB, order);
        for (TopNEntity entity : topNEntities) {
            ServiceInstanceInventory inventory = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class).get(Integer.valueOf(entity.getId()));
            if (inventory != null) {
                entity.setName(inventory.getName());
            }
        }
        return topNEntities;
    }

    public List<TopNEntity> getServiceInstanceTopN(final int serviceId, final String indName, final int topN,
        final Step step, final long startTB, final long endTB, final Order order) throws IOException {
        List<TopNEntity> topNEntities = getAggregationQueryDAO().getServiceInstanceTopN(serviceId, indName, ValueColumnIds.INSTANCE.getValueCName(indName), topN, step, startTB, endTB, order);
        for (TopNEntity entity : topNEntities) {
            ServiceInstanceInventory inventory = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class).get(Integer.valueOf(entity.getId()));
            if (inventory != null) {
                entity.setName(inventory.getName());
            }
        }
        return topNEntities;
    }

    public List<TopNEntity> getAllEndpointTopN(final String indName, final int topN, final Step step,
        final long startTB, final long endTB, final Order order) throws IOException {
        List<TopNEntity> topNEntities = getAggregationQueryDAO().getAllEndpointTopN(indName, ValueColumnIds.INSTANCE.getValueCName(indName), topN, step, startTB, endTB, order);
        for (TopNEntity entity : topNEntities) {
            EndpointInventory inventory = moduleManager.find(CoreModule.NAME).provider().getService(EndpointInventoryCache.class).get(Integer.valueOf(entity.getId()));
            if (inventory != null) {
                entity.setName(inventory.getName());
            }
        }
        return topNEntities;
    }

    public List<TopNEntity> getEndpointTopN(final int serviceId, final String indName, final int topN,
        final Step step, final long startTB, final long endTB, final Order order) throws IOException {
        List<TopNEntity> topNEntities = getAggregationQueryDAO().getEndpointTopN(serviceId, indName, ValueColumnIds.INSTANCE.getValueCName(indName), topN, step, startTB, endTB, order);
        for (TopNEntity entity : topNEntities) {
            EndpointInventory inventory = moduleManager.find(CoreModule.NAME).provider().getService(EndpointInventoryCache.class).get(Integer.valueOf(entity.getId()));
            if (inventory != null) {
                entity.setName(inventory.getName());
            }
        }
        return topNEntities;
    }
}
