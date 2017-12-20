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
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceReferenceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetricTable;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceMetricEsPersistenceDAO extends EsDAO implements IInstanceReferenceMetricPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceReferenceMetric> {

    private final Logger logger = LoggerFactory.getLogger(InstanceReferenceMetricEsPersistenceDAO.class);

    public InstanceReferenceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public InstanceReferenceMetric get(String id) {
        GetResponse getResponse = getClient().prepareGet(InstanceReferenceMetricTable.TABLE, id).get();
        if (getResponse.isExists()) {
            logger.debug("getId: {} is exist", id);
            InstanceReferenceMetric instanceReferenceMetric = new InstanceReferenceMetric(id);
            Map<String, Object> source = getResponse.getSource();
            instanceReferenceMetric.setFrontInstanceId((Integer)source.get(InstanceReferenceMetricTable.COLUMN_FRONT_INSTANCE_ID));
            instanceReferenceMetric.setBehindInstanceId((Integer)source.get(InstanceReferenceMetricTable.COLUMN_BEHIND_INSTANCE_ID));

            instanceReferenceMetric.setTransactionCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue());
            instanceReferenceMetric.setTransactionErrorCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue());
            instanceReferenceMetric.setTransactionDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue());
            instanceReferenceMetric.setTransactionErrorDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            instanceReferenceMetric.setBusinessTransactionCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS)).longValue());
            instanceReferenceMetric.setBusinessTransactionErrorCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS)).longValue());
            instanceReferenceMetric.setBusinessTransactionDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM)).longValue());
            instanceReferenceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            instanceReferenceMetric.setMqTransactionCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS)).longValue());
            instanceReferenceMetric.setMqTransactionErrorCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS)).longValue());
            instanceReferenceMetric.setMqTransactionDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM)).longValue());
            instanceReferenceMetric.setMqTransactionErrorDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM)).longValue());

            instanceReferenceMetric.setTimeBucket(((Number)source.get(InstanceReferenceMetricTable.COLUMN_TIME_BUCKET)).longValue());

            return instanceReferenceMetric;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(InstanceReferenceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceMetricTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(InstanceReferenceMetricTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());

        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(InstanceReferenceMetricTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(InstanceReferenceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceMetricTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(InstanceReferenceMetricTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());

        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(InstanceReferenceMetricTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(InstanceReferenceMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(InstanceReferenceMetricTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, InstanceReferenceMetricTable.TABLE);
    }
}
