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

package org.apache.skywalking.apm.collector.storage.h2.dao.imp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractInstanceMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<InstanceMetric> {

    AbstractInstanceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final InstanceMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceMetric instanceMetric = new InstanceMetric();

        instanceMetric.setId(resultSet.getString(InstanceMetricTable.ID.getName()));
        instanceMetric.setMetricId(resultSet.getString(InstanceMetricTable.METRIC_ID.getName()));
        instanceMetric.setApplicationId(resultSet.getInt(InstanceMetricTable.APPLICATION_ID.getName()));
        instanceMetric.setInstanceId(resultSet.getInt(InstanceMetricTable.INSTANCE_ID.getName()));
        instanceMetric.setSourceValue(resultSet.getInt(InstanceMetricTable.SOURCE_VALUE.getName()));

        instanceMetric.setTransactionCalls(resultSet.getLong(InstanceMetricTable.TRANSACTION_CALLS.getName()));
        instanceMetric.setTransactionErrorCalls(resultSet.getLong(InstanceMetricTable.TRANSACTION_ERROR_CALLS.getName()));
        instanceMetric.setTransactionErrorDurationSum(resultSet.getLong(InstanceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName()));
        instanceMetric.setTransactionAverageDuration(resultSet.getLong(InstanceMetricTable.TRANSACTION_AVERAGE_DURATION.getName()));

        instanceMetric.setBusinessTransactionCalls(resultSet.getLong(InstanceMetricTable.BUSINESS_TRANSACTION_CALLS.getName()));
        instanceMetric.setBusinessTransactionErrorCalls(resultSet.getLong(InstanceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName()));
        instanceMetric.setBusinessTransactionDurationSum(resultSet.getLong(InstanceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName()));
        instanceMetric.setBusinessTransactionErrorDurationSum(resultSet.getLong(InstanceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName()));
        instanceMetric.setBusinessTransactionAverageDuration(resultSet.getLong(InstanceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName()));

        instanceMetric.setMqTransactionCalls(resultSet.getLong(InstanceMetricTable.MQ_TRANSACTION_CALLS.getName()));
        instanceMetric.setMqTransactionErrorCalls(resultSet.getLong(InstanceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName()));
        instanceMetric.setMqTransactionDurationSum(resultSet.getLong(InstanceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName()));
        instanceMetric.setMqTransactionErrorDurationSum(resultSet.getLong(InstanceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName()));
        instanceMetric.setMqTransactionAverageDuration(resultSet.getLong(InstanceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName()));

        instanceMetric.setTimeBucket(resultSet.getLong(InstanceMetricTable.TIME_BUCKET.getName()));
        return instanceMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(InstanceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceMetricTable.ID.getName(), streamData.getId());
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
}
