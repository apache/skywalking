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
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMetricEsPersistenceDAO extends EsDAO implements IApplicationReferenceMetricPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ApplicationReferenceMetric> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationReferenceMetricEsPersistenceDAO.class);

    public ApplicationReferenceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public ApplicationReferenceMetric get(String id) {
        GetResponse getResponse = getClient().prepareGet(ApplicationReferenceMetricTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ApplicationReferenceMetric applicationReferenceMetric = new ApplicationReferenceMetric(id);
            Map<String, Object> source = getResponse.getSource();
            applicationReferenceMetric.setFrontApplicationId(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID)).intValue());
            applicationReferenceMetric.setBehindApplicationId(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID)).intValue());

            applicationReferenceMetric.setTransactionCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue());
            applicationReferenceMetric.setTransactionErrorCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue());
            applicationReferenceMetric.setTransactionDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue());
            applicationReferenceMetric.setTransactionErrorDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            applicationReferenceMetric.setBusinessTransactionCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS)).longValue());
            applicationReferenceMetric.setBusinessTransactionErrorCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS)).longValue());
            applicationReferenceMetric.setBusinessTransactionDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM)).longValue());
            applicationReferenceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            applicationReferenceMetric.setMqTransactionCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS)).longValue());
            applicationReferenceMetric.setMqTransactionErrorCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS)).longValue());
            applicationReferenceMetric.setMqTransactionDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM)).longValue());
            applicationReferenceMetric.setMqTransactionErrorDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            applicationReferenceMetric.setSatisfiedCount(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_SATISFIED_COUNT)).longValue());
            applicationReferenceMetric.setToleratingCount(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TOLERATING_COUNT)).longValue());
            applicationReferenceMetric.setFrustratedCount(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_FRUSTRATED_COUNT)).longValue());
            applicationReferenceMetric.setTimeBucket(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET)).longValue());
            return applicationReferenceMetric;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ApplicationReferenceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());

        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(ApplicationReferenceMetricTable.COLUMN_SATISFIED_COUNT, data.getSatisfiedCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_TOLERATING_COUNT, data.getToleratingCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_FRUSTRATED_COUNT, data.getFrustratedCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(ApplicationReferenceMetricTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ApplicationReferenceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());

        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(ApplicationReferenceMetricTable.COLUMN_SATISFIED_COUNT, data.getSatisfiedCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_TOLERATING_COUNT, data.getToleratingCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_FRUSTRATED_COUNT, data.getFrustratedCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(ApplicationReferenceMetricTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(ApplicationReferenceMetricTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ApplicationReferenceMetricTable.TABLE);
    }
}
