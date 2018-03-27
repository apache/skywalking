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

package org.apache.skywalking.apm.collector.storage.es.dao.imp;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractInstanceMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<InstanceMetric> {

    AbstractInstanceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return InstanceMetricTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final InstanceMetric esDataToStreamData(Map<String, Object> source) {
        InstanceMetric instanceMetric = new InstanceMetric();

        instanceMetric.setMetricId((String)source.get(InstanceMetricTable.COLUMN_METRIC_ID));
        instanceMetric.setApplicationId((Integer)source.get(InstanceMetricTable.COLUMN_APPLICATION_ID));
        instanceMetric.setInstanceId((Integer)source.get(InstanceMetricTable.COLUMN_INSTANCE_ID));
        instanceMetric.setSourceValue((Integer)source.get(InstanceMetricTable.COLUMN_SOURCE_VALUE));

        instanceMetric.setTransactionCalls(((Number)source.get(InstanceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue());
        instanceMetric.setTransactionErrorCalls(((Number)source.get(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue());
        instanceMetric.setTransactionDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue());
        instanceMetric.setTransactionErrorDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        instanceMetric.setTransactionAverageDuration(((Number)source.get(InstanceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION)).longValue());

        instanceMetric.setBusinessTransactionCalls(((Number)source.get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS)).longValue());
        instanceMetric.setBusinessTransactionErrorCalls(((Number)source.get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS)).longValue());
        instanceMetric.setBusinessTransactionDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM)).longValue());
        instanceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        instanceMetric.setBusinessTransactionAverageDuration(((Number)source.get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION)).longValue());

        instanceMetric.setMqTransactionCalls(((Number)source.get(InstanceMetricTable.COLUMN_MQ_TRANSACTION_CALLS)).longValue());
        instanceMetric.setMqTransactionErrorCalls(((Number)source.get(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS)).longValue());
        instanceMetric.setMqTransactionDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM)).longValue());
        instanceMetric.setMqTransactionErrorDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        instanceMetric.setMqTransactionAverageDuration(((Number)source.get(InstanceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION)).longValue());

        instanceMetric.setTimeBucket(((Number)source.get(InstanceMetricTable.COLUMN_TIME_BUCKET)).longValue());
        return instanceMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(InstanceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(InstanceMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(InstanceMetricTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(InstanceMetricTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(InstanceMetricTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(InstanceMetricTable.COLUMN_TRANSACTION_CALLS, streamData.getTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, streamData.getTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, streamData.getTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, streamData.getTransactionErrorDurationSum());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION, streamData.getTransactionAverageDuration());

        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION, streamData.getBusinessTransactionAverageDuration());

        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION, streamData.getMqTransactionAverageDuration());

        source.put(InstanceMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
