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

package org.apache.skywalking.apm.collector.storage.h2.dao.smp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractServiceMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<ServiceMetric> {

    public AbstractServiceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ServiceMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ServiceMetric serviceMetric = new ServiceMetric();
        serviceMetric.setId(resultSet.getString(ServiceMetricTable.COLUMN_ID));
        serviceMetric.setMetricId(resultSet.getString(ServiceMetricTable.COLUMN_METRIC_ID));

        serviceMetric.setApplicationId(resultSet.getInt(ServiceMetricTable.COLUMN_APPLICATION_ID));
        serviceMetric.setInstanceId(resultSet.getInt(ServiceMetricTable.COLUMN_INSTANCE_ID));
        serviceMetric.setServiceId(resultSet.getInt(ServiceMetricTable.COLUMN_SERVICE_ID));
        serviceMetric.setSourceValue(resultSet.getInt(ServiceMetricTable.COLUMN_SOURCE_VALUE));

        serviceMetric.setTransactionCalls(resultSet.getLong(ServiceMetricTable.COLUMN_TRANSACTION_CALLS));
        serviceMetric.setTransactionErrorCalls(resultSet.getLong(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
        serviceMetric.setTransactionDurationSum(resultSet.getLong(ServiceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
        serviceMetric.setTransactionErrorDurationSum(resultSet.getLong(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));
        serviceMetric.setTransactionAverageDuration(resultSet.getLong(ServiceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION));

        serviceMetric.setBusinessTransactionCalls(resultSet.getLong(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS));
        serviceMetric.setBusinessTransactionErrorCalls(resultSet.getLong(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS));
        serviceMetric.setBusinessTransactionDurationSum(resultSet.getLong(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM));
        serviceMetric.setBusinessTransactionErrorDurationSum(resultSet.getLong(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM));
        serviceMetric.setBusinessTransactionAverageDuration(resultSet.getLong(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION));

        serviceMetric.setMqTransactionCalls(resultSet.getLong(ServiceMetricTable.COLUMN_MQ_TRANSACTION_CALLS));
        serviceMetric.setMqTransactionErrorCalls(resultSet.getLong(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS));
        serviceMetric.setMqTransactionDurationSum(resultSet.getLong(ServiceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM));
        serviceMetric.setMqTransactionErrorDurationSum(resultSet.getLong(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM));
        serviceMetric.setMqTransactionAverageDuration(resultSet.getLong(ServiceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION));

        serviceMetric.setTimeBucket(resultSet.getLong(ServiceMetricTable.COLUMN_TIME_BUCKET));
        return serviceMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ServiceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceMetricTable.COLUMN_ID, streamData.getId());
        source.put(ServiceMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(ServiceMetricTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(ServiceMetricTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(ServiceMetricTable.COLUMN_SERVICE_ID, streamData.getServiceId());
        source.put(ServiceMetricTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ServiceMetricTable.COLUMN_TRANSACTION_CALLS, streamData.getTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, streamData.getTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, streamData.getTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, streamData.getTransactionErrorDurationSum());

        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());

        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());

        source.put(ServiceMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
