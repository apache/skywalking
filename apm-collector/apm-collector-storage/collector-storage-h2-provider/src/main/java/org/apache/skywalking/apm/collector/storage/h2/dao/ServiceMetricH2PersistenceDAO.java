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
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.storage.dao.IServiceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ServiceMetricH2PersistenceDAO extends H2DAO implements IServiceMetricPersistenceDAO<H2SqlEntity, H2SqlEntity, ServiceMetric> {

    private final Logger logger = LoggerFactory.getLogger(ServiceMetricH2PersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public ServiceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override
    public ServiceMetric get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, ServiceMetricTable.TABLE, ServiceMetricTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                ServiceMetric serviceMetric = new ServiceMetric(id);
                serviceMetric.setServiceId(rs.getInt(ServiceMetricTable.COLUMN_SERVICE_ID));

                serviceMetric.setTransactionCalls(rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_CALLS));
                serviceMetric.setTransactionErrorCalls(rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
                serviceMetric.setTransactionDurationSum(rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
                serviceMetric.setTransactionErrorDurationSum(rs.getLong(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));

                serviceMetric.setBusinessTransactionCalls(rs.getLong(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS));
                serviceMetric.setBusinessTransactionErrorCalls(rs.getLong(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS));
                serviceMetric.setBusinessTransactionDurationSum(rs.getLong(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM));
                serviceMetric.setBusinessTransactionErrorDurationSum(rs.getLong(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM));

                serviceMetric.setMqTransactionCalls(rs.getLong(ServiceMetricTable.COLUMN_MQ_TRANSACTION_CALLS));
                serviceMetric.setMqTransactionErrorCalls(rs.getLong(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS));
                serviceMetric.setMqTransactionDurationSum(rs.getLong(ServiceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM));
                serviceMetric.setMqTransactionErrorDurationSum(rs.getLong(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM));

                serviceMetric.setTimeBucket(rs.getLong(ServiceMetricTable.COLUMN_TIME_BUCKET));
                return serviceMetric;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public H2SqlEntity prepareBatchInsert(ServiceMetric data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceMetricTable.COLUMN_ID, data.getId());
        source.put(ServiceMetricTable.COLUMN_SERVICE_ID, data.getServiceId());

        source.put(ServiceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(ServiceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        String sql = SqlBuilder.buildBatchInsertSql(ServiceMetricTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override
    public H2SqlEntity prepareBatchUpdate(ServiceMetric data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceMetricTable.COLUMN_SERVICE_ID, data.getServiceId());

        source.put(ServiceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(ServiceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(ServiceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        String sql = SqlBuilder.buildBatchUpdateSql(ServiceMetricTable.TABLE, source.keySet(), ServiceMetricTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
