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
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
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
        return InstanceReferenceMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final InstanceReferenceMetric esDataToStreamData(Map<String, Object> source) {
        InstanceReferenceMetric instanceReferenceMetric = new InstanceReferenceMetric();
        instanceReferenceMetric.setMetricId((String)source.get(InstanceReferenceMetricTable.METRIC_ID.getName()));

        instanceReferenceMetric.setFrontApplicationId((Integer)source.get(InstanceReferenceMetricTable.FRONT_APPLICATION_ID.getName()));
        instanceReferenceMetric.setBehindApplicationId((Integer)source.get(InstanceReferenceMetricTable.BEHIND_APPLICATION_ID.getName()));
        instanceReferenceMetric.setFrontInstanceId((Integer)source.get(InstanceReferenceMetricTable.FRONT_INSTANCE_ID.getName()));
        instanceReferenceMetric.setBehindInstanceId((Integer)source.get(InstanceReferenceMetricTable.BEHIND_INSTANCE_ID.getName()));
        instanceReferenceMetric.setSourceValue((Integer)source.get(InstanceReferenceMetricTable.SOURCE_VALUE.getName()));

        instanceReferenceMetric.setTransactionCalls(((Number)source.get(InstanceReferenceMetricTable.TRANSACTION_CALLS.getName())).longValue());
        instanceReferenceMetric.setTransactionErrorCalls(((Number)source.get(InstanceReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName())).longValue());
        instanceReferenceMetric.setTransactionDurationSum(((Number)source.get(InstanceReferenceMetricTable.TRANSACTION_DURATION_SUM.getName())).longValue());
        instanceReferenceMetric.setTransactionErrorDurationSum(((Number)source.get(InstanceReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        instanceReferenceMetric.setTransactionAverageDuration(((Number)source.get(InstanceReferenceMetricTable.TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        instanceReferenceMetric.setBusinessTransactionCalls(((Number)source.get(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_CALLS.getName())).longValue());
        instanceReferenceMetric.setBusinessTransactionErrorCalls(((Number)source.get(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName())).longValue());
        instanceReferenceMetric.setBusinessTransactionDurationSum(((Number)source.get(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName())).longValue());
        instanceReferenceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        instanceReferenceMetric.setBusinessTransactionAverageDuration(((Number)source.get(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        instanceReferenceMetric.setMqTransactionCalls(((Number)source.get(InstanceReferenceMetricTable.MQ_TRANSACTION_CALLS.getName())).longValue());
        instanceReferenceMetric.setMqTransactionErrorCalls(((Number)source.get(InstanceReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName())).longValue());
        instanceReferenceMetric.setMqTransactionDurationSum(((Number)source.get(InstanceReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName())).longValue());
        instanceReferenceMetric.setMqTransactionErrorDurationSum(((Number)source.get(InstanceReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        instanceReferenceMetric.setMqTransactionAverageDuration(((Number)source.get(InstanceReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        instanceReferenceMetric.setTimeBucket(((Number)source.get(InstanceReferenceMetricTable.TIME_BUCKET.getName())).longValue());
        return instanceReferenceMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(InstanceReferenceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        source.put(InstanceReferenceMetricTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        source.put(InstanceReferenceMetricTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());
        source.put(InstanceReferenceMetricTable.FRONT_INSTANCE_ID.getName(), streamData.getFrontInstanceId());
        source.put(InstanceReferenceMetricTable.BEHIND_INSTANCE_ID.getName(), streamData.getBehindInstanceId());
        source.put(InstanceReferenceMetricTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        source.put(InstanceReferenceMetricTable.TRANSACTION_CALLS.getName(), streamData.getTransactionCalls());
        source.put(InstanceReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName(), streamData.getTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.TRANSACTION_DURATION_SUM.getName(), streamData.getTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getTransactionErrorDurationSum());
        source.put(InstanceReferenceMetricTable.TRANSACTION_AVERAGE_DURATION.getName(), streamData.getTransactionAverageDuration());

        source.put(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_CALLS.getName(), streamData.getBusinessTransactionCalls());
        source.put(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName(), streamData.getBusinessTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName(), streamData.getBusinessTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getBusinessTransactionErrorDurationSum());
        source.put(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getBusinessTransactionAverageDuration());

        source.put(InstanceReferenceMetricTable.MQ_TRANSACTION_CALLS.getName(), streamData.getMqTransactionCalls());
        source.put(InstanceReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName(), streamData.getMqTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName(), streamData.getMqTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getMqTransactionErrorDurationSum());
        source.put(InstanceReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getMqTransactionAverageDuration());

        source.put(InstanceReferenceMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return source;
    }

    @GraphComputingMetric(name = "/persistence/get/" + InstanceReferenceMetricTable.TABLE)
    @Override public final InstanceReferenceMetric get(String id) {
        return super.get(id);
    }
}
