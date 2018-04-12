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

package org.apache.skywalking.apm.collector.storage.h2.dao.amp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<ApplicationMetric> {

    AbstractApplicationMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ApplicationMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationMetric applicationMetric = new ApplicationMetric();
        applicationMetric.setId(resultSet.getString(ApplicationMetricTable.ID.getName()));
        applicationMetric.setMetricId(resultSet.getString(ApplicationMetricTable.METRIC_ID.getName()));

        applicationMetric.setApplicationId(resultSet.getInt(ApplicationMetricTable.APPLICATION_ID.getName()));
        applicationMetric.setSourceValue(resultSet.getInt(ApplicationMetricTable.SOURCE_VALUE.getName()));

        applicationMetric.setTransactionCalls(resultSet.getLong(ApplicationMetricTable.TRANSACTION_CALLS.getName()));
        applicationMetric.setTransactionErrorCalls(resultSet.getLong(ApplicationMetricTable.TRANSACTION_ERROR_CALLS.getName()));
        applicationMetric.setTransactionDurationSum(resultSet.getLong(ApplicationMetricTable.TRANSACTION_DURATION_SUM.getName()));
        applicationMetric.setTransactionErrorDurationSum(resultSet.getLong(ApplicationMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName()));
        applicationMetric.setTransactionAverageDuration(resultSet.getLong(ApplicationMetricTable.TRANSACTION_AVERAGE_DURATION.getName()));

        applicationMetric.setBusinessTransactionCalls(resultSet.getLong(ApplicationMetricTable.BUSINESS_TRANSACTION_CALLS.getName()));
        applicationMetric.setBusinessTransactionErrorCalls(resultSet.getLong(ApplicationMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName()));
        applicationMetric.setBusinessTransactionDurationSum(resultSet.getLong(ApplicationMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName()));
        applicationMetric.setBusinessTransactionErrorDurationSum(resultSet.getLong(ApplicationMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName()));
        applicationMetric.setBusinessTransactionAverageDuration(resultSet.getLong(ApplicationMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName()));

        applicationMetric.setMqTransactionCalls(resultSet.getLong(ApplicationMetricTable.MQ_TRANSACTION_CALLS.getName()));
        applicationMetric.setMqTransactionErrorCalls(resultSet.getLong(ApplicationMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName()));
        applicationMetric.setMqTransactionDurationSum(resultSet.getLong(ApplicationMetricTable.MQ_TRANSACTION_DURATION_SUM.getName()));
        applicationMetric.setMqTransactionErrorDurationSum(resultSet.getLong(ApplicationMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName()));
        applicationMetric.setMqTransactionAverageDuration(resultSet.getLong(ApplicationMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName()));

        applicationMetric.setSatisfiedCount(resultSet.getLong(ApplicationMetricTable.SATISFIED_COUNT.getName()));
        applicationMetric.setToleratingCount(resultSet.getLong(ApplicationMetricTable.TOLERATING_COUNT.getName()));
        applicationMetric.setFrustratedCount(resultSet.getLong(ApplicationMetricTable.FRUSTRATED_COUNT.getName()));
        applicationMetric.setTimeBucket(resultSet.getLong(ApplicationMetricTable.TIME_BUCKET.getName()));

        return applicationMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ApplicationMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationMetricTable.ID.getName(), streamData.getId());
        source.put(ApplicationMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        source.put(ApplicationMetricTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        source.put(ApplicationMetricTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        source.put(ApplicationMetricTable.TRANSACTION_CALLS.getName(), streamData.getTransactionCalls());
        source.put(ApplicationMetricTable.TRANSACTION_ERROR_CALLS.getName(), streamData.getTransactionErrorCalls());
        source.put(ApplicationMetricTable.TRANSACTION_DURATION_SUM.getName(), streamData.getTransactionDurationSum());
        source.put(ApplicationMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.BUSINESS_TRANSACTION_CALLS.getName(), streamData.getBusinessTransactionCalls());
        source.put(ApplicationMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName(), streamData.getBusinessTransactionErrorCalls());
        source.put(ApplicationMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName(), streamData.getBusinessTransactionDurationSum());
        source.put(ApplicationMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getBusinessTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.MQ_TRANSACTION_CALLS.getName(), streamData.getMqTransactionCalls());
        source.put(ApplicationMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName(), streamData.getMqTransactionErrorCalls());
        source.put(ApplicationMetricTable.MQ_TRANSACTION_DURATION_SUM.getName(), streamData.getMqTransactionDurationSum());
        source.put(ApplicationMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getMqTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.SATISFIED_COUNT.getName(), streamData.getSatisfiedCount());
        source.put(ApplicationMetricTable.TOLERATING_COUNT.getName(), streamData.getToleratingCount());
        source.put(ApplicationMetricTable.FRUSTRATED_COUNT.getName(), streamData.getFrustratedCount());
        source.put(ApplicationMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return source;
    }
}
