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

package org.apache.skywalking.apm.collector.storage.h2.dao.armp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationReferenceMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<ApplicationReferenceMetric> {

    AbstractApplicationReferenceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ApplicationReferenceMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationReferenceMetric applicationReferenceMetric = new ApplicationReferenceMetric();
        applicationReferenceMetric.setId(resultSet.getString(ApplicationReferenceMetricTable.ID.getName()));
        applicationReferenceMetric.setMetricId(resultSet.getString(ApplicationReferenceMetricTable.METRIC_ID.getName()));

        applicationReferenceMetric.setFrontApplicationId(resultSet.getInt(ApplicationReferenceMetricTable.FRONT_APPLICATION_ID.getName()));
        applicationReferenceMetric.setBehindApplicationId(resultSet.getInt(ApplicationReferenceMetricTable.BEHIND_APPLICATION_ID.getName()));
        applicationReferenceMetric.setSourceValue(resultSet.getInt(ApplicationReferenceMetricTable.SOURCE_VALUE.getName()));

        applicationReferenceMetric.setTransactionCalls(resultSet.getLong(ApplicationReferenceMetricTable.TRANSACTION_CALLS.getName()));
        applicationReferenceMetric.setTransactionErrorCalls(resultSet.getLong(ApplicationReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName()));
        applicationReferenceMetric.setTransactionDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.TRANSACTION_DURATION_SUM.getName()));
        applicationReferenceMetric.setTransactionErrorDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName()));
        applicationReferenceMetric.setTransactionAverageDuration(resultSet.getLong(ApplicationReferenceMetricTable.TRANSACTION_AVERAGE_DURATION.getName()));

        applicationReferenceMetric.setBusinessTransactionCalls(resultSet.getLong(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_CALLS.getName()));
        applicationReferenceMetric.setBusinessTransactionErrorCalls(resultSet.getLong(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName()));
        applicationReferenceMetric.setBusinessTransactionDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName()));
        applicationReferenceMetric.setBusinessTransactionErrorDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName()));
        applicationReferenceMetric.setBusinessTransactionAverageDuration(resultSet.getLong(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName()));

        applicationReferenceMetric.setMqTransactionCalls(resultSet.getLong(ApplicationReferenceMetricTable.MQ_TRANSACTION_CALLS.getName()));
        applicationReferenceMetric.setMqTransactionErrorCalls(resultSet.getLong(ApplicationReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName()));
        applicationReferenceMetric.setMqTransactionDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName()));
        applicationReferenceMetric.setMqTransactionErrorDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName()));
        applicationReferenceMetric.setMqTransactionAverageDuration(resultSet.getLong(ApplicationReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName()));

        applicationReferenceMetric.setSatisfiedCount(resultSet.getLong(ApplicationReferenceMetricTable.SATISFIED_COUNT.getName()));
        applicationReferenceMetric.setToleratingCount(resultSet.getLong(ApplicationReferenceMetricTable.TOLERATING_COUNT.getName()));
        applicationReferenceMetric.setFrustratedCount(resultSet.getLong(ApplicationReferenceMetricTable.FRUSTRATED_COUNT.getName()));

        applicationReferenceMetric.setTimeBucket(resultSet.getLong(ApplicationReferenceMetricTable.TIME_BUCKET.getName()));
        return applicationReferenceMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ApplicationReferenceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceMetricTable.ID.getName(), streamData.getId());
        source.put(ApplicationReferenceMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        source.put(ApplicationReferenceMetricTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        source.put(ApplicationReferenceMetricTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());
        source.put(ApplicationReferenceMetricTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        source.put(ApplicationReferenceMetricTable.TRANSACTION_CALLS.getName(), streamData.getTransactionCalls());
        source.put(ApplicationReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName(), streamData.getTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.TRANSACTION_DURATION_SUM.getName(), streamData.getTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.TRANSACTION_AVERAGE_DURATION.getName(), streamData.getTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_CALLS.getName(), streamData.getBusinessTransactionCalls());
        source.put(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName(), streamData.getBusinessTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName(), streamData.getBusinessTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getBusinessTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getBusinessTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.MQ_TRANSACTION_CALLS.getName(), streamData.getMqTransactionCalls());
        source.put(ApplicationReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName(), streamData.getMqTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName(), streamData.getMqTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName(), streamData.getMqTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION.getName(), streamData.getMqTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.SATISFIED_COUNT.getName(), streamData.getSatisfiedCount());
        source.put(ApplicationReferenceMetricTable.TOLERATING_COUNT.getName(), streamData.getToleratingCount());
        source.put(ApplicationReferenceMetricTable.FRUSTRATED_COUNT.getName(), streamData.getFrustratedCount());

        source.put(ApplicationReferenceMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return source;
    }
}
