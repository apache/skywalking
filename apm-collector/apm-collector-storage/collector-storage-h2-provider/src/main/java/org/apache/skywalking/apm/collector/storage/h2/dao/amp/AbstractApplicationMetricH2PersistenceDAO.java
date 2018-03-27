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

    public AbstractApplicationMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ApplicationMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationMetric applicationMetric = new ApplicationMetric();
        applicationMetric.setId(resultSet.getString(ApplicationMetricTable.COLUMN_ID));
        applicationMetric.setMetricId(resultSet.getString(ApplicationMetricTable.COLUMN_METRIC_ID));

        applicationMetric.setApplicationId(resultSet.getInt(ApplicationMetricTable.COLUMN_APPLICATION_ID));
        applicationMetric.setSourceValue(resultSet.getInt(ApplicationMetricTable.COLUMN_SOURCE_VALUE));

        applicationMetric.setTransactionCalls(resultSet.getLong(ApplicationMetricTable.COLUMN_TRANSACTION_CALLS));
        applicationMetric.setTransactionErrorCalls(resultSet.getLong(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
        applicationMetric.setTransactionDurationSum(resultSet.getLong(ApplicationMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
        applicationMetric.setTransactionErrorDurationSum(resultSet.getLong(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));
        applicationMetric.setTransactionAverageDuration(resultSet.getLong(ApplicationMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION));

        applicationMetric.setBusinessTransactionCalls(resultSet.getLong(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS));
        applicationMetric.setBusinessTransactionErrorCalls(resultSet.getLong(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS));
        applicationMetric.setBusinessTransactionDurationSum(resultSet.getLong(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM));
        applicationMetric.setBusinessTransactionErrorDurationSum(resultSet.getLong(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM));
        applicationMetric.setBusinessTransactionAverageDuration(resultSet.getLong(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION));

        applicationMetric.setMqTransactionCalls(resultSet.getLong(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_CALLS));
        applicationMetric.setMqTransactionErrorCalls(resultSet.getLong(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS));
        applicationMetric.setMqTransactionDurationSum(resultSet.getLong(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM));
        applicationMetric.setMqTransactionErrorDurationSum(resultSet.getLong(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM));
        applicationMetric.setMqTransactionAverageDuration(resultSet.getLong(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION));

        applicationMetric.setSatisfiedCount(resultSet.getLong(ApplicationMetricTable.COLUMN_SATISFIED_COUNT));
        applicationMetric.setToleratingCount(resultSet.getLong(ApplicationMetricTable.COLUMN_TOLERATING_COUNT));
        applicationMetric.setFrustratedCount(resultSet.getLong(ApplicationMetricTable.COLUMN_FRUSTRATED_COUNT));
        applicationMetric.setTimeBucket(resultSet.getLong(ApplicationMetricTable.COLUMN_TIME_BUCKET));

        return applicationMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ApplicationMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationMetricTable.COLUMN_ID, streamData.getId());
        source.put(ApplicationMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(ApplicationMetricTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(ApplicationMetricTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_CALLS, streamData.getTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, streamData.getTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_DURATION_SUM, streamData.getTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, streamData.getTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.COLUMN_SATISFIED_COUNT, streamData.getSatisfiedCount());
        source.put(ApplicationMetricTable.COLUMN_TOLERATING_COUNT, streamData.getToleratingCount());
        source.put(ApplicationMetricTable.COLUMN_FRUSTRATED_COUNT, streamData.getFrustratedCount());
        source.put(ApplicationMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
