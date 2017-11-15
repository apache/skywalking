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

package org.skywalking.apm.collector.storage.es.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.dao.IServiceReferencePersistenceDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReference;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReferenceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceEsPersistenceDAO extends EsDAO implements IServiceReferencePersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ServiceReference> {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceEsPersistenceDAO.class);

    public ServiceReferenceEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public ServiceReference get(String id) {
        GetResponse getResponse = getClient().prepareGet(ServiceReferenceTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ServiceReference serviceReference = new ServiceReference(id);
            Map<String, Object> source = getResponse.getSource();
            serviceReference.setEntryServiceId(((Number)source.get(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID)).intValue());
            serviceReference.setEntryServiceName((String)source.get(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME));
            serviceReference.setFrontServiceId(((Number)source.get(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)).intValue());
            serviceReference.setFrontServiceName((String)source.get(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME));
            serviceReference.setBehindServiceId(((Number)source.get(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID)).intValue());
            serviceReference.setBehindServiceName((String)source.get(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME));
            serviceReference.setS1Lte(((Number)source.get(ServiceReferenceTable.COLUMN_S1_LTE)).longValue());
            serviceReference.setS3Lte(((Number)source.get(ServiceReferenceTable.COLUMN_S3_LTE)).longValue());
            serviceReference.setS5Lte(((Number)source.get(ServiceReferenceTable.COLUMN_S5_LTE)).longValue());
            serviceReference.setS5Gt(((Number)source.get(ServiceReferenceTable.COLUMN_S5_GT)).longValue());
            serviceReference.setSummary(((Number)source.get(ServiceReferenceTable.COLUMN_SUMMARY)).longValue());
            serviceReference.setError(((Number)source.get(ServiceReferenceTable.COLUMN_ERROR)).longValue());
            serviceReference.setCostSummary(((Number)source.get(ServiceReferenceTable.COLUMN_COST_SUMMARY)).longValue());
            serviceReference.setTimeBucket(((Number)source.get(ServiceReferenceTable.COLUMN_TIME_BUCKET)).longValue());
            return serviceReference;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ServiceReference data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, data.getEntryServiceName());
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, data.getFrontServiceName());
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, data.getBehindServiceName());
        source.put(ServiceReferenceTable.COLUMN_S1_LTE, data.getS1Lte());
        source.put(ServiceReferenceTable.COLUMN_S3_LTE, data.getS3Lte());
        source.put(ServiceReferenceTable.COLUMN_S5_LTE, data.getS5Lte());
        source.put(ServiceReferenceTable.COLUMN_S5_GT, data.getS5Gt());
        source.put(ServiceReferenceTable.COLUMN_SUMMARY, data.getSummary());
        source.put(ServiceReferenceTable.COLUMN_ERROR, data.getError());
        source.put(ServiceReferenceTable.COLUMN_COST_SUMMARY, data.getCostSummary());
        source.put(ServiceReferenceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(ServiceReferenceTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ServiceReference data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, data.getEntryServiceName());
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, data.getFrontServiceName());
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, data.getBehindServiceName());
        source.put(ServiceReferenceTable.COLUMN_S1_LTE, data.getS1Lte());
        source.put(ServiceReferenceTable.COLUMN_S3_LTE, data.getS3Lte());
        source.put(ServiceReferenceTable.COLUMN_S5_LTE, data.getS5Lte());
        source.put(ServiceReferenceTable.COLUMN_S5_GT, data.getS5Gt());
        source.put(ServiceReferenceTable.COLUMN_SUMMARY, data.getSummary());
        source.put(ServiceReferenceTable.COLUMN_ERROR, data.getError());
        source.put(ServiceReferenceTable.COLUMN_COST_SUMMARY, data.getCostSummary());
        source.put(ServiceReferenceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(ServiceReferenceTable.TABLE, data.getId()).setDoc(source);
    }
}
