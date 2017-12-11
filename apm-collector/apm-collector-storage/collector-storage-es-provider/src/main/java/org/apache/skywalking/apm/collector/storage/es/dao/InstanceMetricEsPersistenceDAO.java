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
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceMetricEsPersistenceDAO extends EsDAO implements IInstanceMetricPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceMetric> {

    private final Logger logger = LoggerFactory.getLogger(InstanceMetricEsPersistenceDAO.class);

    public InstanceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public InstanceMetric get(String id) {
        GetResponse getResponse = getClient().prepareGet(InstanceMetricTable.TABLE, id).get();
        if (getResponse.isExists()) {
            logger.debug("getId: {} is exist", id);
            InstanceMetric instanceMetric = new InstanceMetric(id);
            Map<String, Object> source = getResponse.getSource();
            instanceMetric.setApplicationId((Integer)source.get(InstanceMetricTable.COLUMN_APPLICATION_ID));
            instanceMetric.setInstanceId((Integer)source.get(InstanceMetricTable.COLUMN_INSTANCE_ID));

            instanceMetric.setTransactionCalls(((Number)source.get(InstanceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue());
            instanceMetric.setTransactionErrorCalls(((Number)source.get(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue());
            instanceMetric.setTransactionDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue());
            instanceMetric.setTransactionErrorDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            instanceMetric.setBusinessTransactionCalls(((Number)source.get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS)).longValue());
            instanceMetric.setBusinessTransactionErrorCalls(((Number)source.get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS)).longValue());
            instanceMetric.setBusinessTransactionDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM)).longValue());
            instanceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            instanceMetric.setMqTransactionCalls(((Number)source.get(InstanceMetricTable.COLUMN_MQ_TRANSACTION_CALLS)).longValue());
            instanceMetric.setMqTransactionErrorCalls(((Number)source.get(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS)).longValue());
            instanceMetric.setMqTransactionDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM)).longValue());
            instanceMetric.setMqTransactionErrorDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            instanceMetric.setTimeBucket(((Number)source.get(InstanceMetricTable.COLUMN_TIME_BUCKET)).longValue());

            return instanceMetric;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(InstanceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceMetricTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceMetricTable.COLUMN_INSTANCE_ID, data.getInstanceId());

        source.put(InstanceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(InstanceMetricTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(InstanceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceMetricTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceMetricTable.COLUMN_INSTANCE_ID, data.getInstanceId());

        source.put(InstanceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(InstanceMetricTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(InstanceMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(InstanceMetricTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, InstanceMetricTable.TABLE);
    }
}
