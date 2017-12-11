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
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class InstanceMetricH2PersistenceDAO extends H2DAO implements IInstanceMetricPersistenceDAO<H2SqlEntity, H2SqlEntity, InstanceMetric> {

    private final Logger logger = LoggerFactory.getLogger(InstanceMetricH2PersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public InstanceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public InstanceMetric get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, InstanceMetricTable.TABLE, InstanceMetricTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                InstanceMetric instanceMetric = new InstanceMetric(id);
                instanceMetric.setApplicationId(rs.getInt(InstanceMetricTable.COLUMN_APPLICATION_ID));
                instanceMetric.setInstanceId(rs.getInt(InstanceMetricTable.COLUMN_INSTANCE_ID));

                instanceMetric.setTransactionCalls(rs.getLong(InstanceMetricTable.COLUMN_TRANSACTION_CALLS));
                instanceMetric.setTransactionErrorCalls(rs.getLong(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
                instanceMetric.setTransactionDurationSum(rs.getLong(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
                instanceMetric.setTransactionErrorDurationSum(rs.getLong(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));

                instanceMetric.setBusinessTransactionCalls(rs.getLong(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS));
                instanceMetric.setBusinessTransactionErrorCalls(rs.getLong(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS));
                instanceMetric.setBusinessTransactionDurationSum(rs.getLong(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM));
                instanceMetric.setBusinessTransactionErrorDurationSum(rs.getLong(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM));

                instanceMetric.setMqTransactionCalls(rs.getLong(InstanceMetricTable.COLUMN_MQ_TRANSACTION_CALLS));
                instanceMetric.setMqTransactionErrorCalls(rs.getLong(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS));
                instanceMetric.setMqTransactionDurationSum(rs.getLong(InstanceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM));
                instanceMetric.setMqTransactionErrorDurationSum(rs.getLong(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM));

                instanceMetric.setTimeBucket(rs.getLong(InstanceMetricTable.COLUMN_TIME_BUCKET));
                return instanceMetric;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(InstanceMetric data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(InstanceMetricTable.COLUMN_ID, data.getId());
        source.put(InstanceMetricTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceMetricTable.COLUMN_INSTANCE_ID, data.getInstanceId());

        source.put(InstanceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchInsertSql(InstanceMetricTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(InstanceMetric data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(InstanceMetricTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceMetricTable.COLUMN_INSTANCE_ID, data.getInstanceId());

        source.put(InstanceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(InstanceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchUpdateSql(InstanceMetricTable.TABLE, source.keySet(), InstanceMetricTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
