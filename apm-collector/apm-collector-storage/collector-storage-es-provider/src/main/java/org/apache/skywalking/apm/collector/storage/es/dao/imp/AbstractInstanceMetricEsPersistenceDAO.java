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
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
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
        return InstanceMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final InstanceMetric esDataToStreamData(Map<String, Object> source) {
        InstanceMetric instanceMetric = new InstanceMetric();

        instanceMetric.setMetricId((String)source.get(InstanceMetricTable.METRIC_ID.getName()));
        instanceMetric.setApplicationId((Integer)source.get(InstanceMetricTable.APPLICATION_ID.getName()));
        instanceMetric.setInstanceId((Integer)source.get(InstanceMetricTable.INSTANCE_ID.getName()));
        instanceMetric.setSourceValue((Integer)source.get(InstanceMetricTable.SOURCE_VALUE.getName()));

        instanceMetric.setTransactionCalls(((Number)source.get(InstanceMetricTable.TRANSACTION_CALLS.getName())).longValue());
        instanceMetric.setTransactionErrorCalls(((Number)source.get(InstanceMetricTable.TRANSACTION_ERROR_CALLS.getName())).longValue());
        instanceMetric.setTransactionDurationSum(((Number)source.get(InstanceMetricTable.TRANSACTION_DURATION_SUM.getName())).longValue());
        instanceMetric.setTransactionErrorDurationSum(((Number)source.get(InstanceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        instanceMetric.setTransactionAverageDuration(((Number)source.get(InstanceMetricTable.TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        instanceMetric.setBusinessTransactionCalls(((Number)source.get(InstanceMetricTable.BUSINESS_TRANSACTION_CALLS.getName())).longValue());
        instanceMetric.setBusinessTransactionErrorCalls(((Number)source.get(InstanceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName())).longValue());
        instanceMetric.setBusinessTransactionDurationSum(((Number)source.get(InstanceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName())).longValue());
        instanceMetric.setBusinessTransactionErrorDurationSum(((Number)source.get(InstanceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        instanceMetric.setBusinessTransactionAverageDuration(((Number)source.get(InstanceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        instanceMetric.setMqTransactionCalls(((Number)source.get(InstanceMetricTable.MQ_TRANSACTION_CALLS.getName())).longValue());
        instanceMetric.setMqTransactionErrorCalls(((Number)source.get(InstanceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName())).longValue());
        instanceMetric.setMqTransactionDurationSum(((Number)source.get(InstanceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName())).longValue());
        instanceMetric.setMqTransactionErrorDurationSum(((Number)source.get(InstanceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        instanceMetric.setMqTransactionAverageDuration(((Number)source.get(InstanceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        instanceMetric.setTimeBucket(((Number)source.get(InstanceMetricTable.TIME_BUCKET.getName())).longValue());
        return instanceMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(InstanceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceMetricTable.METRIC_ID.getName(), streamData.getMetricId());
        source.put(InstanceMetricTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        source.put(InstanceMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        source.put(InstanceMetricTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        source.put(InstanceMetricTable.TRANSACTION_CALLS.getName(), streamData.getTransactionCalls());
        source.put(InstanceMetricTable.TRANSACTION_ERROR_CALLS.getName(), streamData.getTransactionErrorCalls());
        source.put(InstanceMetricTable.TRANSACTION_DURATION_SUM.getName(), streamData.getTransactionDurationSum());
        source.put(InstanceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getTransactionErrorDurationSum());
        source.put(InstanceMetricTable.TRANSACTION_AVERAGE_DURATION.getName(), streamData.getTransactionAverageDuration());

        source.put(InstanceMetricTable.BUSINESS_TRANSACTION_CALLS.getName(), streamData.getBusinessTransactionCalls());
        source.put(InstanceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName(), streamData.getBusinessTransactionErrorCalls());
        source.put(InstanceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName(), streamData.getBusinessTransactionDurationSum());
        source.put(InstanceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getBusinessTransactionErrorDurationSum());
        source.put(InstanceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getBusinessTransactionAverageDuration());

        source.put(InstanceMetricTable.MQ_TRANSACTION_CALLS.getName(), streamData.getMqTransactionCalls());
        source.put(InstanceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName(), streamData.getMqTransactionErrorCalls());
        source.put(InstanceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName(), streamData.getMqTransactionDurationSum());
        source.put(InstanceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getMqTransactionErrorDurationSum());
        source.put(InstanceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getMqTransactionAverageDuration());

        source.put(InstanceMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return source;
    }

    @GraphComputingMetric(name = "/persistence/get/" + InstanceMetricTable.TABLE)
    @Override public final InstanceMetric get(String id) {
        return super.get(id);
    }
}
