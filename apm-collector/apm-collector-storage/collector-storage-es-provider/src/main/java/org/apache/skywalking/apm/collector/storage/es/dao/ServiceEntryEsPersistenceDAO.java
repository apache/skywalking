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


package org.apache.skywalking.apm.collector.storage.es.dao;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceEntry;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceEntryTable;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IServiceEntryPersistenceDAO;

/**
 * @author peng-yongsheng
 */
public class ServiceEntryEsPersistenceDAO extends EsDAO implements IServiceEntryPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ServiceEntry> {

    public ServiceEntryEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public ServiceEntry get(String id) {
        GetResponse getResponse = getClient().prepareGet(ServiceEntryTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ServiceEntry serviceEntry = new ServiceEntry(id);
            Map<String, Object> source = getResponse.getSource();
            serviceEntry.setApplicationId(((Number)source.get(ServiceEntryTable.COLUMN_APPLICATION_ID)).intValue());
            serviceEntry.setEntryServiceId(((Number)source.get(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID)).intValue());
            serviceEntry.setEntryServiceName((String)source.get(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME));
            serviceEntry.setRegisterTime(((Number)source.get(ServiceEntryTable.COLUMN_REGISTER_TIME)).longValue());
            serviceEntry.setNewestTime(((Number)source.get(ServiceEntryTable.COLUMN_NEWEST_TIME)).longValue());
            return serviceEntry;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ServiceEntry data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceEntryTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, data.getEntryServiceName());
        source.put(ServiceEntryTable.COLUMN_REGISTER_TIME, data.getRegisterTime());
        source.put(ServiceEntryTable.COLUMN_NEWEST_TIME, data.getNewestTime());
        return getClient().prepareIndex(ServiceEntryTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ServiceEntry data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceEntryTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, data.getEntryServiceName());
        source.put(ServiceEntryTable.COLUMN_REGISTER_TIME, data.getRegisterTime());
        source.put(ServiceEntryTable.COLUMN_NEWEST_TIME, data.getNewestTime());

        return getClient().prepareUpdate(ServiceEntryTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
