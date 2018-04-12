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
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
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
        return ServiceReferenceMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final ServiceReferenceMetric esDataToStreamData(Map<String, Object> source) {
        ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric();
        serviceReferenceMetric.setMetricId((String)source.get(ServiceReferenceMetricTable.METRIC_ID.getName()));

        serviceReferenceMetric.setFrontApplicationId(((Number)source.get(ServiceReferenceMetricTable.FRONT_APPLICATION_ID.getName())).intValue());
        serviceReferenceMetric.setBehindApplicationId(((Number)source.get(ServiceReferenceMetricTable.BEHIND_APPLICATION_ID.getName())).intValue());
        serviceReferenceMetric.setFrontInstanceId(((Number)source.get(ServiceReferenceMetricTable.FRONT_INSTANCE_ID.getName())).intValue());
        serviceReferenceMetric.setBehindInstanceId(((Number)source.get(ServiceReferenceMetricTable.BEHIND_INSTANCE_ID.getName())).intValue());
        serviceReferenceMetric.setFrontServiceId(((Number)source.get(ServiceReferenceMetricTable.FRONT_SERVICE_ID.getName())).intValue());
        serviceReferenceMetric.setBehindServiceId(((Number)source.get(ServiceReferenceMetricTable.BEHIND_SERVICE_ID.getName())).intValue());
        serviceReferenceMetric.setSourceValue(((Number)source.get(ServiceReferenceMetricTable.SOURCE_VALUE.getName())).intValue());

        serviceReferenceMetric.setTransactionCalls(((Number)source.get(ServiceReferenceMetricTable.TRANSACTION_CALLS.getName())).longValue());
        serviceReferenceMetric.setTransactionErrorCalls(((Number)source.get(ServiceReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName())).longValue());
        serviceReferenceMetric.setTransactionDurationSum(((Number)source.get(ServiceReferenceMetricTable.TRANSACTION_DURATION_SUM.getName())).longValue());
        serviceReferenceMetric.setTransactionErrorDurationSum(((Number)source.get(ServiceReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        serviceReferenceMetric.setTransactionAverageDuration(((Number)source.get(ServiceReferenceMetricTable.TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        serviceReferenceMetric.setBusinessTransactionCalls(((Number)source.get(ServiceReferenceMetricTable.BUSINESS_TRANSACTION_CALLS.getName())).longValue());
        serviceReferenceMetric.setBusinessTransactionErrorCalls(((Number)source.get(ServiceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName())).longValue());
        serviceReferenceMetric.setBusinessTransactionDurationSum(((Number)source.get(ServiceReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName())).longValue());
        serviceReferenceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(ServiceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        serviceReferenceMetric.setBusinessTransactionAverageDuration(((Number)source.get(ServiceReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        serviceReferenceMetric.setMqTransactionCalls(((Number)source.get(ServiceReferenceMetricTable.MQ_TRANSACTION_CALLS.getName())).longValue());
        serviceReferenceMetric.setMqTransactionErrorCalls(((Number)source.get(ServiceReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName())).longValue());
        serviceReferenceMetric.setMqTransactionDurationSum(((Number)source.get(ServiceReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName())).longValue());
        serviceReferenceMetric.setMqTransactionErrorDurationSum(((Number)source.get(ServiceReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        serviceReferenceMetric.setMqTransactionAverageDuration(((Number)source.get(ServiceReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        serviceReferenceMetric.setTimeBucket(((Number)source.get(ServiceReferenceMetricTable.TIME_BUCKET.getName())).longValue());
        return serviceReferenceMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(ServiceReferenceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        source.put(ServiceReferenceMetricTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        source.put(ServiceReferenceMetricTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());
        source.put(ServiceReferenceMetricTable.FRONT_INSTANCE_ID.getName(), streamData.getFrontInstanceId());
        source.put(ServiceReferenceMetricTable.BEHIND_INSTANCE_ID.getName(), streamData.getBehindInstanceId());
        source.put(ServiceReferenceMetricTable.FRONT_SERVICE_ID.getName(), streamData.getFrontServiceId());
        source.put(ServiceReferenceMetricTable.BEHIND_SERVICE_ID.getName(), streamData.getBehindServiceId());
        source.put(ServiceReferenceMetricTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        source.put(ServiceReferenceMetricTable.TRANSACTION_CALLS.getName(), streamData.getTransactionCalls());
        source.put(ServiceReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName(), streamData.getTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.TRANSACTION_DURATION_SUM.getName(), streamData.getTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getTransactionErrorDurationSum());
        source.put(ServiceReferenceMetricTable.TRANSACTION_AVERAGE_DURATION.getName(), streamData.getTransactionAverageDuration());

        source.put(ServiceReferenceMetricTable.BUSINESS_TRANSACTION_CALLS.getName(), streamData.getBusinessTransactionCalls());
        source.put(ServiceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName(), streamData.getBusinessTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName(), streamData.getBusinessTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getBusinessTransactionErrorDurationSum());
        source.put(ServiceReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getBusinessTransactionAverageDuration());

        source.put(ServiceReferenceMetricTable.MQ_TRANSACTION_CALLS.getName(), streamData.getMqTransactionCalls());
        source.put(ServiceReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName(), streamData.getMqTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName(), streamData.getMqTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getMqTransactionErrorDurationSum());
        source.put(ServiceReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getMqTransactionAverageDuration());

        source.put(ServiceReferenceMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return source;
    }

    @GraphComputingMetric(name = "/persistence/get/" + ServiceReferenceMetricTable.TABLE)
    @Override public final ServiceReferenceMetric get(String id) {
        return super.get(id);
    }
}
