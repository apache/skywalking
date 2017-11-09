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

package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Iterator;
import java.util.Map;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.storage.dao.IServiceEntryUIDAO;
import org.skywalking.apm.collector.storage.dao.IServiceReferenceUIDAO;
import org.skywalking.apm.collector.storage.service.DAOService;
import org.skywalking.apm.collector.storage.table.service.ServiceEntryTable;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReferenceTable;

/**
 * @author peng-yongsheng
 */
public class ServiceTreeService {

    private final DAOService daoService;
    private final CacheServiceManager cacheServiceManager;

    public ServiceTreeService(DAOService daoService, CacheServiceManager cacheServiceManager) {
        this.daoService = daoService;
        this.cacheServiceManager = cacheServiceManager;
    }

    public JsonObject loadEntryService(int applicationId, String entryServiceName, long startTime, long endTime,
        int from, int size) {
        IServiceEntryUIDAO serviceEntryDAO = (IServiceEntryUIDAO)daoService.get(IServiceEntryUIDAO.class);

        JsonObject response = serviceEntryDAO.load(applicationId, entryServiceName, startTime, endTime, from, size);
        JsonArray entryServices = response.get("array").getAsJsonArray();
        for (JsonElement element : entryServices) {
            JsonObject entryService = element.getAsJsonObject();
            int respApplication = entryService.get(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_APPLICATION_ID)).getAsInt();
            String applicationCode = cacheServiceManager.getApplicationCacheService().get(respApplication);
            entryService.addProperty("applicationCode", applicationCode);
        }

        return response;
    }

    public JsonArray loadServiceTree(int entryServiceId, long startTime, long endTime) {
        IServiceReferenceUIDAO serviceReferenceDAO = (IServiceReferenceUIDAO)daoService.get(IServiceReferenceUIDAO.class);
        Map<String, JsonObject> serviceReferenceMap = serviceReferenceDAO.load(entryServiceId, startTime, endTime);
        return buildTreeData(serviceReferenceMap);
    }

    private JsonArray buildTreeData(Map<String, JsonObject> serviceReferenceMap) {
        JsonArray serviceReferenceArray = new JsonArray();
        JsonObject rootServiceReference = findRoot(serviceReferenceMap);
        if (ObjectUtils.isNotEmpty(rootServiceReference)) {
            serviceReferenceArray.add(rootServiceReference);
            String id = rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID));
            serviceReferenceMap.remove(id);

            int rootServiceId = rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt();
            sortAsTree(rootServiceId, serviceReferenceArray, serviceReferenceMap);
        }

        return serviceReferenceArray;
    }

    private JsonObject findRoot(Map<String, JsonObject> serviceReferenceMap) {
        for (JsonObject serviceReference : serviceReferenceMap.values()) {
            int frontServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)).getAsInt();
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
            int frontServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)).getAsInt();
            if (serviceId == frontServiceId) {
                serviceReferenceArray.add(serviceReference);

                int behindServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt();
                sortAsTree(behindServiceId, serviceReferenceArray, serviceReferenceMap);
            }
        }
    }

    private void merge(Map<String, JsonObject> serviceReferenceMap, JsonObject serviceReference) {
        String id = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID));

        if (serviceReferenceMap.containsKey(id)) {
            JsonObject reference = serviceReferenceMap.get(id);
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S1_LTE));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S3_LTE));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_LTE));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_GT));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_ERROR));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_SUMMARY));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_COST_SUMMARY));
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