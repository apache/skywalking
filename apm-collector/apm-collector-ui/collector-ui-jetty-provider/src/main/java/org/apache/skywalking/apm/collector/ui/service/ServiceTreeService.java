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


package org.apache.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Iterator;
import java.util.Map;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.storage.dao.IServiceEntryUIDAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceEntryTable;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceUIDAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;

/**
 * @author peng-yongsheng
 */
public class ServiceTreeService {

    private final IServiceEntryUIDAO serviceEntryDAO;
    private final IServiceReferenceUIDAO serviceReferenceDAO;
    private final ApplicationCacheService applicationCacheService;
    private final ServiceNameCacheService serviceNameCacheService;

    public ServiceTreeService(ModuleManager moduleManager) {
        this.serviceEntryDAO = moduleManager.find(StorageModule.NAME).getService(IServiceEntryUIDAO.class);
        this.serviceReferenceDAO = moduleManager.find(StorageModule.NAME).getService(IServiceReferenceUIDAO.class);
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
    }

    public JsonObject loadEntryService(int applicationId, String entryServiceName, long startTime, long endTime,
        int from, int size) {
        JsonObject response = serviceEntryDAO.load(applicationId, entryServiceName, startTime, endTime, from, size);
        JsonArray entryServices = response.get("array").getAsJsonArray();
        for (JsonElement element : entryServices) {
            JsonObject entryService = element.getAsJsonObject();
            int respApplication = entryService.get(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_APPLICATION_ID)).getAsInt();
            String applicationCode = applicationCacheService.get(respApplication);
            entryService.addProperty("applicationCode", applicationCode);
        }

        return response;
    }

    public JsonArray loadServiceTree(int entryServiceId, long startTime, long endTime) {
        Map<String, JsonObject> serviceReferenceMap = serviceReferenceDAO.load(entryServiceId, startTime, endTime);
        serviceReferenceMap.values().forEach(serviceReference -> {
            int frontServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID)).getAsInt();
            int behindServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt();
            String frontServiceName = serviceNameCacheService.getSplitServiceName(serviceNameCacheService.get(frontServiceId));
            String behindServiceName = serviceNameCacheService.getSplitServiceName(serviceNameCacheService.get(behindServiceId));
            serviceReference.addProperty("frontServiceName", frontServiceName);
            serviceReference.addProperty("behindServiceName", behindServiceName);
        });
        return buildTreeData(serviceReferenceMap);
    }

    private JsonArray buildTreeData(Map<String, JsonObject> serviceReferenceMap) {
        JsonArray serviceReferenceArray = new JsonArray();
        JsonObject rootServiceReference = findRoot(serviceReferenceMap);
        if (ObjectUtils.isNotEmpty(rootServiceReference)) {
            serviceReferenceArray.add(rootServiceReference);
            String id = rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID));
            serviceReferenceMap.remove(id);

            int rootServiceId = rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt();
            sortAsTree(rootServiceId, serviceReferenceArray, serviceReferenceMap);
        }

        return serviceReferenceArray;
    }

    private JsonObject findRoot(Map<String, JsonObject> serviceReferenceMap) {
        for (JsonObject serviceReference : serviceReferenceMap.values()) {
            int frontServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID)).getAsInt();
            if (frontServiceId == 1) {
                return serviceReference;
            }
        }
        return null;
    }

    private void sortAsTree(int serviceId, JsonArray serviceReferenceArray,
        Map<String, JsonObject> serviceReferenceMap) {
        Iterator<JsonObject> iterator = serviceReferenceMap.values().iterator();
        while (iterator.hasNext()) {
            JsonObject serviceReference = iterator.next();
            int frontServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID)).getAsInt();
            if (serviceId == frontServiceId) {
                serviceReferenceArray.add(serviceReference);

                int behindServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt();
                sortAsTree(behindServiceId, serviceReferenceArray, serviceReferenceMap);
            }
        }
    }

    private void merge(Map<String, JsonObject> serviceReferenceMap, JsonObject serviceReference) {
        String id = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID));

        if (serviceReferenceMap.containsKey(id)) {
            JsonObject reference = serviceReferenceMap.get(id);
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));
        } else {
            serviceReferenceMap.put(id, serviceReference);
        }
    }

    private void add(JsonObject oldReference, JsonObject newReference, String key) {
        long oldValue = oldReference.get(key).getAsLong();
        long newValue = newReference.get(key).getAsLong();
        oldReference.addProperty(key, oldValue + newValue);
    }
}
