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

package org.apache.skywalking.apm.collector.storage.h2.dao.srmp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractServiceReferenceMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<ServiceReferenceMetric> {

    AbstractServiceReferenceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ServiceReferenceMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric();
        serviceReferenceMetric.setId(resultSet.getString(ServiceReferenceMetricTable.COLUMN_ID));
        serviceReferenceMetric.setMetricId(resultSet.getString(ServiceReferenceMetricTable.COLUMN_METRIC_ID));

        serviceReferenceMetric.setFrontApplicationId(resultSet.getInt(ServiceReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID));
        serviceReferenceMetric.setBehindApplicationId(resultSet.getInt(ServiceReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID));
        serviceReferenceMetric.setFrontInstanceId(resultSet.getInt(ServiceReferenceMetricTable.COLUMN_FRONT_INSTANCE_ID));
        serviceReferenceMetric.setBehindInstanceId(resultSet.getInt(ServiceReferenceMetricTable.COLUMN_BEHIND_INSTANCE_ID));
        serviceReferenceMetric.setFrontServiceId(resultSet.getInt(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID));
        serviceReferenceMetric.setBehindServiceId(resultSet.getInt(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID));
        serviceReferenceMetric.setSourceValue(resultSet.getInt(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE));

        serviceReferenceMetric.setTransactionCalls(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        serviceReferenceMetric.setTransactionErrorCalls(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
        serviceReferenceMetric.setTransactionDurationSum(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
        serviceReferenceMetric.setTransactionErrorDurationSum(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));
        serviceReferenceMetric.setTransactionAverageDuration(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION));

        serviceReferenceMetric.setBusinessTransactionCalls(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS));
        serviceReferenceMetric.setBusinessTransactionErrorCalls(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS));
        serviceReferenceMetric.setBusinessTransactionDurationSum(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM));
        serviceReferenceMetric.setBusinessTransactionErrorDurationSum(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM));
        serviceReferenceMetric.setBusinessTransactionAverageDuration(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION));

        serviceReferenceMetric.setMqTransactionCalls(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS));
        serviceReferenceMetric.setMqTransactionErrorCalls(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS));
        serviceReferenceMetric.setMqTransactionDurationSum(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM));
        serviceReferenceMetric.setMqTransactionErrorDurationSum(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM));
        serviceReferenceMetric.setMqTransactionAverageDuration(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION));

        serviceReferenceMetric.setTimeBucket(resultSet.getLong(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET));
        return serviceReferenceMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ServiceReferenceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceMetricTable.COLUMN_ID, streamData.getId());
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

        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
