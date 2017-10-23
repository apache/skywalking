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
import com.google.gson.JsonObject;
import java.util.Iterator;
import java.util.Map;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceTable;
import org.skywalking.apm.collector.ui.dao.IServiceEntryDAO;
import org.skywalking.apm.collector.ui.dao.IServiceReferenceDAO;

/**
 * @author peng-yongsheng
 */
public class ServiceTreeService {

    public JsonObject loadEntryService(int applicationId, String entryServiceName, long startTime, long endTime,
        int from, int size) {
        IServiceEntryDAO serviceEntryDAO = (IServiceEntryDAO)DAOContainer.INSTANCE.get(IServiceEntryDAO.class.getName());
        return serviceEntryDAO.load(applicationId, entryServiceName, startTime, endTime, from, size);
    }

    public JsonArray loadServiceTree(int entryServiceId, long startTime, long endTime) {
        IServiceReferenceDAO serviceReferenceDAO = (IServiceReferenceDAO)DAOContainer.INSTANCE.get(IServiceReferenceDAO.class.getName());
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