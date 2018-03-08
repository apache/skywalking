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

package org.apache.skywalking.apm.collector.storage.es.dao.armp;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationReferenceMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<ApplicationReferenceMetric> {

    AbstractApplicationReferenceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final ApplicationReferenceMetric esDataToStreamData(Map<String, Object> source) {
        ApplicationReferenceMetric applicationReferenceMetric = new ApplicationReferenceMetric();
        applicationReferenceMetric.setMetricId((String)source.get(ApplicationReferenceMetricTable.COLUMN_METRIC_ID));

        applicationReferenceMetric.setFrontApplicationId(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID)).intValue());
        applicationReferenceMetric.setBehindApplicationId(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID)).intValue());
        applicationReferenceMetric.setSourceValue(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_SOURCE_VALUE)).intValue());

        applicationReferenceMetric.setTransactionCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue());
        applicationReferenceMetric.setTransactionErrorCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue());
        applicationReferenceMetric.setTransactionDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue());
        applicationReferenceMetric.setTransactionErrorDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        applicationReferenceMetric.setTransactionAverageDuration(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION)).longValue());

        applicationReferenceMetric.setBusinessTransactionCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS)).longValue());
        applicationReferenceMetric.setBusinessTransactionErrorCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS)).longValue());
        applicationReferenceMetric.setBusinessTransactionDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM)).longValue());
        applicationReferenceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        applicationReferenceMetric.setBusinessTransactionAverageDuration(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION)).longValue());

        applicationReferenceMetric.setMqTransactionCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS)).longValue());
        applicationReferenceMetric.setMqTransactionErrorCalls(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS)).longValue());
        applicationReferenceMetric.setMqTransactionDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM)).longValue());
        applicationReferenceMetric.setMqTransactionErrorDurationSum(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        applicationReferenceMetric.setMqTransactionAverageDuration(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION)).longValue());

        applicationReferenceMetric.setSatisfiedCount(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_SATISFIED_COUNT)).longValue());
        applicationReferenceMetric.setToleratingCount(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TOLERATING_COUNT)).longValue());
        applicationReferenceMetric.setFrustratedCount(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_FRUSTRATED_COUNT)).longValue());

        applicationReferenceMetric.setTimeBucket(((Number)source.get(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET)).longValue());
        return applicationReferenceMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(ApplicationReferenceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, streamData.getFrontApplicationId());
        source.put(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, streamData.getBehindApplicationId());
        source.put(ApplicationReferenceMetricTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS, streamData.getTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, streamData.getTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, streamData.getTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, streamData.getTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION, streamData.getTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION, streamData.getBusinessTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION, streamData.getMqTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.COLUMN_SATISFIED_COUNT, streamData.getSatisfiedCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_TOLERATING_COUNT, streamData.getToleratingCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_FRUSTRATED_COUNT, streamData.getFrustratedCount());

        source.put(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
