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

    public AbstractApplicationReferenceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ApplicationReferenceMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationReferenceMetric applicationReferenceMetric = new ApplicationReferenceMetric();
        applicationReferenceMetric.setId(resultSet.getString(ApplicationReferenceMetricTable.COLUMN_ID));
        applicationReferenceMetric.setMetricId(resultSet.getString(ApplicationReferenceMetricTable.COLUMN_METRIC_ID));

        applicationReferenceMetric.setFrontApplicationId(resultSet.getInt(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID));
        applicationReferenceMetric.setBehindApplicationId(resultSet.getInt(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID));
        applicationReferenceMetric.setSourceValue(resultSet.getInt(ApplicationReferenceMetricTable.COLUMN_SOURCE_VALUE));

        applicationReferenceMetric.setTransactionCalls(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
        applicationReferenceMetric.setTransactionErrorCalls(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
        applicationReferenceMetric.setTransactionDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
        applicationReferenceMetric.setTransactionErrorDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));
        applicationReferenceMetric.setTransactionAverageDuration(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION));

        applicationReferenceMetric.setBusinessTransactionCalls(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS));
        applicationReferenceMetric.setBusinessTransactionErrorCalls(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS));
        applicationReferenceMetric.setBusinessTransactionDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM));
        applicationReferenceMetric.setBusinessTransactionErrorDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM));
        applicationReferenceMetric.setBusinessTransactionAverageDuration(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION));

        applicationReferenceMetric.setMqTransactionCalls(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS));
        applicationReferenceMetric.setMqTransactionErrorCalls(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS));
        applicationReferenceMetric.setMqTransactionDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM));
        applicationReferenceMetric.setMqTransactionErrorDurationSum(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM));
        applicationReferenceMetric.setMqTransactionAverageDuration(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION));

        applicationReferenceMetric.setSatisfiedCount(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_SATISFIED_COUNT));
        applicationReferenceMetric.setToleratingCount(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_TOLERATING_COUNT));
        applicationReferenceMetric.setFrustratedCount(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_FRUSTRATED_COUNT));

        applicationReferenceMetric.setTimeBucket(resultSet.getLong(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET));
        return applicationReferenceMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ApplicationReferenceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceMetricTable.COLUMN_ID, streamData.getId());
        source.put(ApplicationReferenceMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, streamData.getFrontApplicationId());
        source.put(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, streamData.getBehindApplicationId());
        source.put(ApplicationReferenceMetricTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS, streamData.getTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, streamData.getTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, streamData.getTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, streamData.getTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_AVERAGE_DURATION, streamData.getTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION, streamData.getBusinessTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_MQ_TRANSACTION_AVERAGE_DURATION, streamData.getMqTransactionAverageDuration());

        source.put(ApplicationReferenceMetricTable.COLUMN_SATISFIED_COUNT, streamData.getSatisfiedCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_TOLERATING_COUNT, streamData.getToleratingCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_FRUSTRATED_COUNT, streamData.getFrustratedCount());

        source.put(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
