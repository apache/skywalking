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

    public AbstractInstanceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final InstanceMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceMetric instanceMetric = new InstanceMetric();

        instanceMetric.setId(resultSet.getString(InstanceMetricTable.COLUMN_ID));
        instanceMetric.setMetricId(resultSet.getString(InstanceMetricTable.COLUMN_METRIC_ID));
        instanceMetric.setApplicationId(resultSet.getInt(InstanceMetricTable.COLUMN_APPLICATION_ID));
        instanceMetric.setInstanceId(resultSet.getInt(InstanceMetricTable.COLUMN_INSTANCE_ID));
        instanceMetric.setSourceValue(resultSet.getInt(InstanceMetricTable.COLUMN_SOURCE_VALUE));

        instanceMetric.setTransactionCalls(resultSet.getLong(InstanceMetricTable.COLUMN_TRANSACTION_CALLS));
        instanceMetric.setTransactionErrorCalls(resultSet.getLong(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
        instanceMetric.setTransactionDurationSum(resultSet.getLong(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
        instanceMetric.setTransactionErrorDurationSum(resultSet.getLong(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));
        instanceMetric.setTransactionAverageDuration(resultSet.getLong(InstanceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION));

        instanceMetric.setBusinessTransactionCalls(resultSet.getLong(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS));
        instanceMetric.setBusinessTransactionErrorCalls(resultSet.getLong(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS));
        instanceMetric.setBusinessTransactionDurationSum(resultSet.getLong(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM));
        instanceMetric.setBusinessTransactionErrorDurationSum(resultSet.getLong(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM));
        instanceMetric.setBusinessTransactionAverageDuration(resultSet.getLong(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION));

        instanceMetric.setMqTransactionCalls(resultSet.getLong(InstanceMetricTable.COLUMN_MQ_TRANSACTION_CALLS));
        instanceMetric.setMqTransactionErrorCalls(resultSet.getLong(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS));
        instanceMetric.setMqTransactionDurationSum(resultSet.getLong(InstanceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM));
        instanceMetric.setMqTransactionErrorDurationSum(resultSet.getLong(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM));
        instanceMetric.setMqTransactionAverageDuration(resultSet.getLong(InstanceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION));

        instanceMetric.setTimeBucket(resultSet.getLong(InstanceMetricTable.COLUMN_TIME_BUCKET));
        return instanceMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(InstanceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceMetricTable.COLUMN_ID, streamData.getId());
        source.put(InstanceMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(InstanceMetricTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(InstanceMetricTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(InstanceMetricTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(InstanceMetricTable.COLUMN_TRANSACTION_CALLS, streamData.getTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, streamData.getTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, streamData.getTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, streamData.getTransactionErrorDurationSum());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION, streamData.getTransactionAverageDuration());

        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION, streamData.getBusinessTransactionAverageDuration());

        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION, streamData.getMqTransactionAverageDuration());

        source.put(InstanceMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
