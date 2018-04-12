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
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
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
        return ServiceMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final ServiceMetric esDataToStreamData(Map<String, Object> source) {
        ServiceMetric serviceMetric = new ServiceMetric();
        serviceMetric.setMetricId((String)source.get(ServiceMetricTable.METRIC_ID.getName()));

        serviceMetric.setApplicationId(((Number)source.get(ServiceMetricTable.APPLICATION_ID.getName())).intValue());
        serviceMetric.setInstanceId(((Number)source.get(ServiceMetricTable.INSTANCE_ID.getName())).intValue());
        serviceMetric.setServiceId(((Number)source.get(ServiceMetricTable.SERVICE_ID.getName())).intValue());
        serviceMetric.setSourceValue(((Number)source.get(ServiceMetricTable.SOURCE_VALUE.getName())).intValue());

        serviceMetric.setTransactionCalls(((Number)source.get(ServiceMetricTable.TRANSACTION_CALLS.getName())).longValue());
        serviceMetric.setTransactionErrorCalls(((Number)source.get(ServiceMetricTable.TRANSACTION_ERROR_CALLS.getName())).longValue());
        serviceMetric.setTransactionDurationSum(((Number)source.get(ServiceMetricTable.TRANSACTION_DURATION_SUM.getName())).longValue());
        serviceMetric.setTransactionErrorDurationSum(((Number)source.get(ServiceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        serviceMetric.setTransactionAverageDuration(((Number)source.get(ServiceMetricTable.TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        serviceMetric.setBusinessTransactionCalls(((Number)source.get(ServiceMetricTable.BUSINESS_TRANSACTION_CALLS.getName())).longValue());
        serviceMetric.setBusinessTransactionErrorCalls(((Number)source.get(ServiceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName())).longValue());
        serviceMetric.setBusinessTransactionDurationSum(((Number)source.get(ServiceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName())).longValue());
        serviceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(ServiceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        serviceMetric.setBusinessTransactionAverageDuration(((Number)source.get(ServiceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        serviceMetric.setMqTransactionCalls(((Number)source.get(ServiceMetricTable.MQ_TRANSACTION_CALLS.getName())).longValue());
        serviceMetric.setMqTransactionErrorCalls(((Number)source.get(ServiceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName())).longValue());
        serviceMetric.setMqTransactionDurationSum(((Number)source.get(ServiceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName())).longValue());
        serviceMetric.setMqTransactionErrorDurationSum(((Number)source.get(ServiceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        serviceMetric.setMqTransactionAverageDuration(((Number)source.get(ServiceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        serviceMetric.setTimeBucket(((Number)source.get(ServiceMetricTable.TIME_BUCKET.getName())).longValue());
        return serviceMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(ServiceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        source.put(ServiceMetricTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        source.put(ServiceMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        source.put(ServiceMetricTable.SERVICE_ID.getName(), streamData.getServiceId());
        source.put(ServiceMetricTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        source.put(ServiceMetricTable.TRANSACTION_CALLS.getName(), streamData.getTransactionCalls());
        source.put(ServiceMetricTable.TRANSACTION_ERROR_CALLS.getName(), streamData.getTransactionErrorCalls());
        source.put(ServiceMetricTable.TRANSACTION_DURATION_SUM.getName(), streamData.getTransactionDurationSum());
        source.put(ServiceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getTransactionErrorDurationSum());
        source.put(ServiceMetricTable.TRANSACTION_AVERAGE_DURATION.getName(), streamData.getTransactionAverageDuration());

        source.put(ServiceMetricTable.BUSINESS_TRANSACTION_CALLS.getName(), streamData.getBusinessTransactionCalls());
        source.put(ServiceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName(), streamData.getBusinessTransactionErrorCalls());
        source.put(ServiceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName(), streamData.getBusinessTransactionDurationSum());
        source.put(ServiceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getBusinessTransactionErrorDurationSum());
        source.put(ServiceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getBusinessTransactionAverageDuration());

        source.put(ServiceMetricTable.MQ_TRANSACTION_CALLS.getName(), streamData.getMqTransactionCalls());
        source.put(ServiceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName(), streamData.getMqTransactionErrorCalls());
        source.put(ServiceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName(), streamData.getMqTransactionDurationSum());
        source.put(ServiceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getMqTransactionErrorDurationSum());
        source.put(ServiceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getMqTransactionAverageDuration());

        source.put(ServiceMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return source;
    }

    @GraphComputingMetric(name = "/persistence/get/" + ServiceMetricTable.TABLE)
    @Override public final ServiceMetric get(String id) {
        return super.get(id);
    }
}
