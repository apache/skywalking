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

package org.apache.skywalking.apm.collector.storage.es.dao.smp;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractServiceMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<ServiceMetric> {

    AbstractServiceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return ServiceMetricTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final ServiceMetric esDataToStreamData(Map<String, Object> source) {
        ServiceMetric serviceMetric = new ServiceMetric();
        serviceMetric.setMetricId((String)source.get(ServiceMetricTable.COLUMN_METRIC_ID));

        serviceMetric.setApplicationId(((Number)source.get(ServiceMetricTable.COLUMN_APPLICATION_ID)).intValue());
        serviceMetric.setInstanceId(((Number)source.get(ServiceMetricTable.COLUMN_INSTANCE_ID)).intValue());
        serviceMetric.setServiceId(((Number)source.get(ServiceMetricTable.COLUMN_SERVICE_ID)).intValue());
        serviceMetric.setSourceValue(((Number)source.get(ServiceMetricTable.COLUMN_SOURCE_VALUE)).intValue());

        serviceMetric.setTransactionCalls(((Number)source.get(ServiceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue());
        serviceMetric.setTransactionErrorCalls(((Number)source.get(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue());
        serviceMetric.setTransactionDurationSum(((Number)source.get(ServiceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue());
        serviceMetric.setTransactionErrorDurationSum(((Number)source.get(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        serviceMetric.setTransactionAverageDuration(((Number)source.get(ServiceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION)).longValue());

        serviceMetric.setBusinessTransactionCalls(((Number)source.get(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS)).longValue());
        serviceMetric.setBusinessTransactionErrorCalls(((Number)source.get(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS)).longValue());
        serviceMetric.setBusinessTransactionDurationSum(((Number)source.get(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM)).longValue());
        serviceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        serviceMetric.setBusinessTransactionAverageDuration(((Number)source.get(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION)).longValue());

        serviceMetric.setMqTransactionCalls(((Number)source.get(ServiceMetricTable.COLUMN_MQ_TRANSACTION_CALLS)).longValue());
        serviceMetric.setMqTransactionErrorCalls(((Number)source.get(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS)).longValue());
        serviceMetric.setMqTransactionDurationSum(((Number)source.get(ServiceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM)).longValue());
        serviceMetric.setMqTransactionErrorDurationSum(((Number)source.get(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM)).longValue());
        serviceMetric.setMqTransactionAverageDuration(((Number)source.get(ServiceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION)).longValue());

        serviceMetric.setTimeBucket(((Number)source.get(ServiceMetricTable.COLUMN_TIME_BUCKET)).longValue());
        return serviceMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(ServiceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(ServiceMetricTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(ServiceMetricTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(ServiceMetricTable.COLUMN_SERVICE_ID, streamData.getServiceId());
        source.put(ServiceMetricTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ServiceMetricTable.COLUMN_TRANSACTION_CALLS, streamData.getTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, streamData.getTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, streamData.getTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, streamData.getTransactionErrorDurationSum());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION, streamData.getTransactionAverageDuration());

        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION, streamData.getBusinessTransactionAverageDuration());

        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION, streamData.getMqTransactionAverageDuration());

        source.put(ServiceMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
