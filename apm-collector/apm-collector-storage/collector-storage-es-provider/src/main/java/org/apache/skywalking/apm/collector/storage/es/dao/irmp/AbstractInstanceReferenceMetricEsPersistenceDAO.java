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

package org.apache.skywalking.apm.collector.storage.es.dao.irmp;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractInstanceReferenceMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<InstanceReferenceMetric> {

    AbstractInstanceReferenceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return InstanceReferenceMetricTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final InstanceReferenceMetric esDataToStreamData(Map<String, Object> source) {
        InstanceReferenceMetric instanceReferenceMetric = new InstanceReferenceMetric();
        instanceReferenceMetric.setMetricId((String)source.get(InstanceReferenceMetricTable.COLUMN_METRIC_ID));

        instanceReferenceMetric.setFrontApplicationId((Integer)source.get(InstanceReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID));
        instanceReferenceMetric.setBehindApplicationId((Integer)source.get(InstanceReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID));
        instanceReferenceMetric.setFrontInstanceId((Integer)source.get(InstanceReferenceMetricTable.COLUMN_FRONT_INSTANCE_ID));
        instanceReferenceMetric.setBehindInstanceId((Integer)source.get(InstanceReferenceMetricTable.COLUMN_BEHIND_INSTANCE_ID));
        instanceReferenceMetric.setSourceValue((Integer)source.get(InstanceReferenceMetricTable.COLUMN_SOURCE_VALUE));

        instanceReferenceMetric.setTransactionCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue());
        instanceReferenceMetric.setTransactionErrorCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue());
        instanceReferenceMetric.setTransactionDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue());
        instanceReferenceMetric.setTransactionErrorDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        instanceReferenceMetric.setTransactionAverageDuration(((Number)source.get(InstanceReferenceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION)).longValue());

        instanceReferenceMetric.setBusinessTransactionCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS)).longValue());
        instanceReferenceMetric.setBusinessTransactionErrorCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS)).longValue());
        instanceReferenceMetric.setBusinessTransactionDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM)).longValue());
        instanceReferenceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        instanceReferenceMetric.setBusinessTransactionAverageDuration(((Number)source.get(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION)).longValue());

        instanceReferenceMetric.setMqTransactionCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS)).longValue());
        instanceReferenceMetric.setMqTransactionErrorCalls(((Number)source.get(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS)).longValue());
        instanceReferenceMetric.setMqTransactionDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM)).longValue());
        instanceReferenceMetric.setMqTransactionErrorDurationSum(((Number)source.get(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        instanceReferenceMetric.setMqTransactionAverageDuration(((Number)source.get(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION)).longValue());

        instanceReferenceMetric.setTimeBucket(((Number)source.get(InstanceReferenceMetricTable.COLUMN_TIME_BUCKET)).longValue());
        return instanceReferenceMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(InstanceReferenceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(InstanceReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, streamData.getFrontApplicationId());
        source.put(InstanceReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, streamData.getBehindApplicationId());
        source.put(InstanceReferenceMetricTable.COLUMN_FRONT_INSTANCE_ID, streamData.getFrontInstanceId());
        source.put(InstanceReferenceMetricTable.COLUMN_BEHIND_INSTANCE_ID, streamData.getBehindInstanceId());
        source.put(InstanceReferenceMetricTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, streamData.getTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, streamData.getTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, streamData.getTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, streamData.getTransactionErrorDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION, streamData.getTransactionAverageDuration());

        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION, streamData.getBusinessTransactionAverageDuration());

        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION, streamData.getMqTransactionAverageDuration());

        source.put(InstanceReferenceMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
