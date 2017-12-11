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
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceMetricEsPersistenceDAO extends EsDAO implements IServiceReferenceMetricPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ServiceReferenceMetric> {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceMetricEsPersistenceDAO.class);

    public ServiceReferenceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public ServiceReferenceMetric get(String id) {
        GetResponse getResponse = getClient().prepareGet(ServiceReferenceMetricTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric(id);
            Map<String, Object> source = getResponse.getSource();
            serviceReferenceMetric.setEntryServiceId(((Number)source.get(ServiceReferenceMetricTable.COLUMN_ENTRY_SERVICE_ID)).intValue());
            serviceReferenceMetric.setFrontServiceId(((Number)source.get(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID)).intValue());
            serviceReferenceMetric.setBehindServiceId(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID)).intValue());
            serviceReferenceMetric.setSourceValue(((Number)source.get(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE)).intValue());

            serviceReferenceMetric.setTransactionCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue());
            serviceReferenceMetric.setTransactionErrorCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue());
            serviceReferenceMetric.setTransactionDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue());
            serviceReferenceMetric.setTransactionErrorDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            serviceReferenceMetric.setBusinessTransactionCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS)).longValue());
            serviceReferenceMetric.setBusinessTransactionErrorCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS)).longValue());
            serviceReferenceMetric.setBusinessTransactionDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM)).longValue());
            serviceReferenceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            serviceReferenceMetric.setMqTransactionCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS)).longValue());
            serviceReferenceMetric.setMqTransactionErrorCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS)).longValue());
            serviceReferenceMetric.setMqTransactionDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM)).longValue());
            serviceReferenceMetric.setMqTransactionErrorDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            serviceReferenceMetric.setTimeBucket(((Number)source.get(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET)).longValue());
            return serviceReferenceMetric;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ServiceReferenceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceMetricTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(ServiceReferenceMetricTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ServiceReferenceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceMetricTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(ServiceReferenceMetricTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(ServiceReferenceMetricTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ServiceReferenceMetricTable.TABLE);
    }
}
