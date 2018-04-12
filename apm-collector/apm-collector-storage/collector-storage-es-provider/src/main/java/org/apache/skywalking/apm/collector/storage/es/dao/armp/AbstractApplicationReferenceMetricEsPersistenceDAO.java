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
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
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
        return ApplicationReferenceMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final ApplicationReferenceMetric esDataToStreamData(Map<String, Object> source) {
        ApplicationReferenceMetric applicationReferenceMetric = new ApplicationReferenceMetric();
        applicationReferenceMetric.setMetricId((String)source.get(ApplicationReferenceMetricTable.METRIC_ID.getName()));

        applicationReferenceMetric.setFrontApplicationId(((Number)source.get(ApplicationReferenceMetricTable.FRONT_APPLICATION_ID.getName())).intValue());
        applicationReferenceMetric.setBehindApplicationId(((Number)source.get(ApplicationReferenceMetricTable.BEHIND_APPLICATION_ID.getName())).intValue());
        applicationReferenceMetric.setSourceValue(((Number)source.get(ApplicationReferenceMetricTable.SOURCE_VALUE.getName())).intValue());

        applicationReferenceMetric.setTransactionCalls(((Number)source.get(ApplicationReferenceMetricTable.TRANSACTION_CALLS.getName())).longValue());
        applicationReferenceMetric.setTransactionErrorCalls(((Number)source.get(ApplicationReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName())).longValue());
        applicationReferenceMetric.setTransactionDurationSum(((Number)source.get(ApplicationReferenceMetricTable.TRANSACTION_DURATION_SUM.getName())).longValue());
        applicationReferenceMetric.setTransactionErrorDurationSum(((Number)source.get(ApplicationReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        applicationReferenceMetric.setTransactionAverageDuration(((Number)source.get(ApplicationReferenceMetricTable.TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        applicationReferenceMetric.setBusinessTransactionCalls(((Number)source.get(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_CALLS.getName())).longValue());
        applicationReferenceMetric.setBusinessTransactionErrorCalls(((Number)source.get(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName())).longValue());
        applicationReferenceMetric.setBusinessTransactionDurationSum(((Number)source.get(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName())).longValue());
        applicationReferenceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        applicationReferenceMetric.setBusinessTransactionAverageDuration(((Number)source.get(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        applicationReferenceMetric.setMqTransactionCalls(((Number)source.get(ApplicationReferenceMetricTable.MQ_TRANSACTION_CALLS.getName())).longValue());
        applicationReferenceMetric.setMqTransactionErrorCalls(((Number)source.get(ApplicationReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName())).longValue());
        applicationReferenceMetric.setMqTransactionDurationSum(((Number)source.get(ApplicationReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName())).longValue());
        applicationReferenceMetric.setMqTransactionErrorDurationSum(((Number)source.get(ApplicationReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        applicationReferenceMetric.setMqTransactionAverageDuration(((Number)source.get(ApplicationReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        applicationReferenceMetric.setSatisfiedCount(((Number)source.get(ApplicationReferenceMetricTable.SATISFIED_COUNT.getName())).longValue());
        applicationReferenceMetric.setToleratingCount(((Number)source.get(ApplicationReferenceMetricTable.TOLERATING_COUNT.getName())).longValue());
        applicationReferenceMetric.setFrustratedCount(((Number)source.get(ApplicationReferenceMetricTable.FRUSTRATED_COUNT.getName())).longValue());

        applicationReferenceMetric.setTimeBucket(((Number)source.get(ApplicationReferenceMetricTable.TIME_BUCKET.getName())).longValue());
        return applicationReferenceMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(ApplicationReferenceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        source.put(ApplicationReferenceMetricTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        source.put(ApplicationReferenceMetricTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());
        source.put(ApplicationReferenceMetricTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        source.put(ApplicationReferenceMetricTable.TRANSACTION_CALLS.getName(), streamData.getTransactionCalls());
        source.put(ApplicationReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName(), streamData.getTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.TRANSACTION_DURATION_SUM.getName(), streamData.getTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.TRANSACTION_AVERAGE_DURATION.getName(), streamData.getTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_CALLS.getName(), streamData.getBusinessTransactionCalls());
        source.put(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName(), streamData.getBusinessTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName(), streamData.getBusinessTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getBusinessTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getBusinessTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.MQ_TRANSACTION_CALLS.getName(), streamData.getMqTransactionCalls());
        source.put(ApplicationReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName(), streamData.getMqTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName(), streamData.getMqTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getMqTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getMqTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.SATISFIED_COUNT.getName(), streamData.getSatisfiedCount());
        source.put(ApplicationReferenceMetricTable.TOLERATING_COUNT.getName(), streamData.getToleratingCount());
        source.put(ApplicationReferenceMetricTable.FRUSTRATED_COUNT.getName(), streamData.getFrustratedCount());

        source.put(ApplicationReferenceMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return source;
    }

    @GraphComputingMetric(name = "/persistence/get/" + ApplicationReferenceMetricTable.TABLE)
    @Override public final ApplicationReferenceMetric get(String id) {
        return super.get(id);
    }
}
