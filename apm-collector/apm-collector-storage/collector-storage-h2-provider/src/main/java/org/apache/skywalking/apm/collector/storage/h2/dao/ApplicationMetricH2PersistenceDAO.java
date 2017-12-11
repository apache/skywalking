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


package org.apache.skywalking.apm.collector.storage.h2.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetricTable;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ApplicationMetricH2PersistenceDAO extends H2DAO implements IApplicationMetricPersistenceDAO<H2SqlEntity, H2SqlEntity, ApplicationMetric> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationMetricH2PersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public ApplicationMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public ApplicationMetric get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, ApplicationMetricTable.TABLE, ApplicationMetricTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                ApplicationMetric applicationMetric = new ApplicationMetric(id);
                applicationMetric.setApplicationId(rs.getInt(ApplicationMetricTable.COLUMN_APPLICATION_ID));

                applicationMetric.setTransactionCalls(rs.getLong(ApplicationMetricTable.COLUMN_TRANSACTION_CALLS));
                applicationMetric.setTransactionErrorCalls(rs.getLong(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
                applicationMetric.setTransactionDurationSum(rs.getLong(ApplicationMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
                applicationMetric.setTransactionErrorDurationSum(rs.getLong(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));

                applicationMetric.setBusinessTransactionCalls(rs.getLong(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS));
                applicationMetric.setBusinessTransactionErrorCalls(rs.getLong(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS));
                applicationMetric.setBusinessTransactionDurationSum(rs.getLong(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM));
                applicationMetric.setBusinessTransactionErrorDurationSum(rs.getLong(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM));

                applicationMetric.setMqTransactionCalls(rs.getLong(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_CALLS));
                applicationMetric.setMqTransactionErrorCalls(rs.getLong(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS));
                applicationMetric.setMqTransactionDurationSum(rs.getLong(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM));
                applicationMetric.setMqTransactionErrorDurationSum(rs.getLong(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM));

                applicationMetric.setSatisfiedCount(rs.getLong(ApplicationMetricTable.COLUMN_SATISFIED_COUNT));
                applicationMetric.setToleratingCount(rs.getLong(ApplicationMetricTable.COLUMN_TOLERATING_COUNT));
                applicationMetric.setFrustratedCount(rs.getLong(ApplicationMetricTable.COLUMN_FRUSTRATED_COUNT));
                applicationMetric.setTimeBucket(rs.getLong(ApplicationMetricTable.COLUMN_TIME_BUCKET));
                return applicationMetric;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(ApplicationMetric data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(ApplicationMetricTable.COLUMN_ID, data.getId());
        source.put(ApplicationMetricTable.COLUMN_APPLICATION_ID, data.getApplicationId());

        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.COLUMN_SATISFIED_COUNT, data.getSatisfiedCount());
        source.put(ApplicationMetricTable.COLUMN_TOLERATING_COUNT, data.getToleratingCount());
        source.put(ApplicationMetricTable.COLUMN_FRUSTRATED_COUNT, data.getFrustratedCount());
        source.put(ApplicationMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchInsertSql(ApplicationMetricTable.TABLE, source.keySet());
        entity.setSql(sql);

        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(ApplicationMetric data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(ApplicationMetricTable.COLUMN_APPLICATION_ID, data.getApplicationId());

        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(ApplicationMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(ApplicationMetricTable.COLUMN_SATISFIED_COUNT, data.getSatisfiedCount());
        source.put(ApplicationMetricTable.COLUMN_TOLERATING_COUNT, data.getToleratingCount());
        source.put(ApplicationMetricTable.COLUMN_FRUSTRATED_COUNT, data.getFrustratedCount());
        source.put(ApplicationMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchUpdateSql(ApplicationMetricTable.TABLE, source.keySet(), ApplicationMetricTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
