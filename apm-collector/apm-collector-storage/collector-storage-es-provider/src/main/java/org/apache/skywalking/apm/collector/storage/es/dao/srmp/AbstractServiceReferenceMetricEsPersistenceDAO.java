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

package org.apache.skywalking.apm.collector.storage.es.dao.srmp;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractServiceReferenceMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<ServiceReferenceMetric> {

    AbstractServiceReferenceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return ServiceReferenceMetricTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final ServiceReferenceMetric esDataToStreamData(Map<String, Object> source) {
        ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric();
        serviceReferenceMetric.setMetricId((String)source.get(ServiceReferenceMetricTable.COLUMN_METRIC_ID));

        serviceReferenceMetric.setFrontApplicationId(((Number)source.get(ServiceReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID)).intValue());
        serviceReferenceMetric.setBehindApplicationId(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID)).intValue());
        serviceReferenceMetric.setFrontInstanceId(((Number)source.get(ServiceReferenceMetricTable.COLUMN_FRONT_INSTANCE_ID)).intValue());
        serviceReferenceMetric.setBehindInstanceId(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BEHIND_INSTANCE_ID)).intValue());
        serviceReferenceMetric.setFrontServiceId(((Number)source.get(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID)).intValue());
        serviceReferenceMetric.setBehindServiceId(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID)).intValue());
        serviceReferenceMetric.setSourceValue(((Number)source.get(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE)).intValue());

        serviceReferenceMetric.setTransactionCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue());
        serviceReferenceMetric.setTransactionErrorCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue());
        serviceReferenceMetric.setTransactionDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue());
        serviceReferenceMetric.setTransactionErrorDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        serviceReferenceMetric.setTransactionAverageDuration(((Number)source.get(ServiceReferenceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION)).longValue());

        serviceReferenceMetric.setBusinessTransactionCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS)).longValue());
        serviceReferenceMetric.setBusinessTransactionErrorCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS)).longValue());
        serviceReferenceMetric.setBusinessTransactionDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM)).longValue());
        serviceReferenceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        serviceReferenceMetric.setBusinessTransactionAverageDuration(((Number)source.get(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION)).longValue());

        serviceReferenceMetric.setMqTransactionCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS)).longValue());
        serviceReferenceMetric.setMqTransactionErrorCalls(((Number)source.get(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS)).longValue());
        serviceReferenceMetric.setMqTransactionDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM)).longValue());
        serviceReferenceMetric.setMqTransactionErrorDurationSum(((Number)source.get(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        serviceReferenceMetric.setMqTransactionAverageDuration(((Number)source.get(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION)).longValue());

        serviceReferenceMetric.setTimeBucket(((Number)source.get(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET)).longValue());
        return serviceReferenceMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(ServiceReferenceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(ServiceReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, streamData.getFrontApplicationId());
        source.put(ServiceReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, streamData.getBehindApplicationId());
        source.put(ServiceReferenceMetricTable.COLUMN_FRONT_INSTANCE_ID, streamData.getFrontInstanceId());
        source.put(ServiceReferenceMetricTable.COLUMN_BEHIND_INSTANCE_ID, streamData.getBehindInstanceId());
        source.put(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID, streamData.getFrontServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID, streamData.getBehindServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, streamData.getTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, streamData.getTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, streamData.getTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, streamData.getTransactionErrorDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION, streamData.getTransactionAverageDuration());

        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION, streamData.getBusinessTransactionAverageDuration());

        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION, streamData.getMqTransactionAverageDuration());

        source.put(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
