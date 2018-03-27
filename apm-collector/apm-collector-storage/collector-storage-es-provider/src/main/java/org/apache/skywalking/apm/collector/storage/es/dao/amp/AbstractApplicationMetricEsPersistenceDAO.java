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

package org.apache.skywalking.apm.collector.storage.es.dao.amp;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<ApplicationMetric> {

    AbstractApplicationMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return ApplicationMetricTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final ApplicationMetric esDataToStreamData(Map<String, Object> source) {
        ApplicationMetric applicationMetric = new ApplicationMetric();
        applicationMetric.setMetricId((String)source.get(ApplicationMetricTable.COLUMN_METRIC_ID));

        applicationMetric.setApplicationId(((Number)source.get(ApplicationMetricTable.COLUMN_APPLICATION_ID)).intValue());
        applicationMetric.setSourceValue(((Number)source.get(ApplicationMetricTable.COLUMN_SOURCE_VALUE)).intValue());

        applicationMetric.setTransactionCalls(((Number)source.get(ApplicationMetricTable.COLUMN_TRANSACTION_CALLS)).longValue());
        applicationMetric.setTransactionErrorCalls(((Number)source.get(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue());
        applicationMetric.setTransactionDurationSum(((Number)source.get(ApplicationMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue());
        applicationMetric.setTransactionErrorDurationSum(((Number)source.get(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        applicationMetric.setTransactionAverageDuration(((Number)source.get(ApplicationMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION)).longValue());

        applicationMetric.setBusinessTransactionCalls(((Number)source.get(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS)).longValue());
        applicationMetric.setBusinessTransactionErrorCalls(((Number)source.get(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS)).longValue());
        applicationMetric.setBusinessTransactionDurationSum(((Number)source.get(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM)).longValue());
        applicationMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        applicationMetric.setBusinessTransactionAverageDuration(((Number)source.get(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION)).longValue());

        applicationMetric.setMqTransactionCalls(((Number)source.get(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_CALLS)).longValue());
        applicationMetric.setMqTransactionErrorCalls(((Number)source.get(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS)).longValue());
        applicationMetric.setMqTransactionDurationSum(((Number)source.get(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM)).longValue());
        applicationMetric.setMqTransactionErrorDurationSum(((Number)source.get(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        applicationMetric.setMqTransactionAverageDuration(((Number)source.get(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION)).longValue());

        applicationMetric.setSatisfiedCount(((Number)source.get(ApplicationMetricTable.COLUMN_SATISFIED_COUNT)).longValue());
        applicationMetric.setToleratingCount(((Number)source.get(ApplicationMetricTable.COLUMN_TOLERATING_COUNT)).longValue());
        applicationMetric.setFrustratedCount(((Number)source.get(ApplicationMetricTable.COLUMN_FRUSTRATED_COUNT)).longValue());
        applicationMetric.setTimeBucket(((Number)source.get(ApplicationMetricTable.COLUMN_TIME_BUCKET)).longValue());

        return applicationMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(ApplicationMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(ApplicationMetricTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(ApplicationMetricTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_CALLS, streamData.getTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, streamData.getTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_DURATION_SUM, streamData.getTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, streamData.getTransactionErrorDurationSum());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION, streamData.getTransactionAverageDuration());

        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION, streamData.getBusinessTransactionAverageDuration());

        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION, streamData.getMqTransactionAverageDuration());

        source.put(ApplicationMetricTable.COLUMN_SATISFIED_COUNT, streamData.getSatisfiedCount());
        source.put(ApplicationMetricTable.COLUMN_TOLERATING_COUNT, streamData.getToleratingCount());
        source.put(ApplicationMetricTable.COLUMN_FRUSTRATED_COUNT, streamData.getFrustratedCount());
        source.put(ApplicationMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
