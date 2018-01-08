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
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.irmp.IInstanceReferenceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class InstanceReferenceMetricH2PersistenceDAO extends H2DAO implements IInstanceReferenceMinuteMetricPersistenceDAO<H2SqlEntity, H2SqlEntity, InstanceReferenceMetric> {

    private final Logger logger = LoggerFactory.getLogger(InstanceReferenceMetricH2PersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public InstanceReferenceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public InstanceReferenceMetric get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, InstanceReferenceMetricTable.TABLE, InstanceReferenceMetricTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                InstanceReferenceMetric instanceReferenceMetric = new InstanceReferenceMetric();
                instanceReferenceMetric.setId(id);
                instanceReferenceMetric.setFrontInstanceId(rs.getInt(InstanceReferenceMetricTable.COLUMN_FRONT_INSTANCE_ID));
                instanceReferenceMetric.setBehindInstanceId(rs.getInt(InstanceReferenceMetricTable.COLUMN_BEHIND_INSTANCE_ID));

                instanceReferenceMetric.setTransactionCalls(rs.getLong(InstanceReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
                instanceReferenceMetric.setTransactionErrorCalls(rs.getLong(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
                instanceReferenceMetric.setTransactionDurationSum(rs.getLong(InstanceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
                instanceReferenceMetric.setTransactionErrorDurationSum(rs.getLong(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));

                instanceReferenceMetric.setBusinessTransactionCalls(rs.getLong(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS));
                instanceReferenceMetric.setBusinessTransactionErrorCalls(rs.getLong(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS));
                instanceReferenceMetric.setBusinessTransactionDurationSum(rs.getLong(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM));
                instanceReferenceMetric.setBusinessTransactionErrorDurationSum(rs.getLong(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM));

                instanceReferenceMetric.setMqTransactionCalls(rs.getLong(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS));
                instanceReferenceMetric.setMqTransactionErrorCalls(rs.getLong(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS));
                instanceReferenceMetric.setMqTransactionDurationSum(rs.getLong(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM));
                instanceReferenceMetric.setMqTransactionErrorDurationSum(rs.getLong(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM));

                instanceReferenceMetric.setTimeBucket(rs.getLong(InstanceReferenceMetricTable.COLUMN_TIME_BUCKET));
                return instanceReferenceMetric;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(InstanceReferenceMetric data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(InstanceReferenceMetricTable.COLUMN_ID, data.getId());
        source.put(InstanceReferenceMetricTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(InstanceReferenceMetricTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());

        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchInsertSql(InstanceReferenceMetricTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(InstanceReferenceMetric data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(InstanceReferenceMetricTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(InstanceReferenceMetricTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());

        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(InstanceReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchUpdateSql(InstanceReferenceMetricTable.TABLE, source.keySet(), InstanceReferenceMetricTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
