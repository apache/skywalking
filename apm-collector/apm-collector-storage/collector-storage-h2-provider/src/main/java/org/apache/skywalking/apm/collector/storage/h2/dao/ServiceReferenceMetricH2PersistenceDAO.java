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
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ServiceReferenceMetricH2PersistenceDAO extends H2DAO implements IServiceReferenceMetricPersistenceDAO<H2SqlEntity, H2SqlEntity, ServiceReferenceMetric> {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceMetricH2PersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public ServiceReferenceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override
    public ServiceReferenceMetric get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, ServiceReferenceMetricTable.TABLE, ServiceReferenceMetricTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric(id);
                serviceReferenceMetric.setEntryServiceId(rs.getInt(ServiceReferenceMetricTable.COLUMN_ENTRY_SERVICE_ID));
                serviceReferenceMetric.setFrontServiceId(rs.getInt(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID));
                serviceReferenceMetric.setBehindServiceId(rs.getInt(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID));
                serviceReferenceMetric.setSourceValue(rs.getInt(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE));

                serviceReferenceMetric.setTransactionCalls(rs.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS));
                serviceReferenceMetric.setTransactionErrorCalls(rs.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS));
                serviceReferenceMetric.setTransactionDurationSum(rs.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM));
                serviceReferenceMetric.setTransactionErrorDurationSum(rs.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM));

                serviceReferenceMetric.setBusinessTransactionCalls(rs.getLong(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS));
                serviceReferenceMetric.setBusinessTransactionErrorCalls(rs.getLong(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS));
                serviceReferenceMetric.setBusinessTransactionDurationSum(rs.getLong(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM));
                serviceReferenceMetric.setBusinessTransactionErrorDurationSum(rs.getLong(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM));

                serviceReferenceMetric.setMqTransactionCalls(rs.getLong(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS));
                serviceReferenceMetric.setMqTransactionErrorCalls(rs.getLong(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS));
                serviceReferenceMetric.setMqTransactionDurationSum(rs.getLong(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM));
                serviceReferenceMetric.setMqTransactionErrorDurationSum(rs.getLong(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM));

                serviceReferenceMetric.setTimeBucket(rs.getLong(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET));
                return serviceReferenceMetric;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public H2SqlEntity prepareBatchInsert(ServiceReferenceMetric data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceMetricTable.COLUMN_ID, data.getId());
        source.put(ServiceReferenceMetricTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        String sql = SqlBuilder.buildBatchInsertSql(ServiceReferenceMetricTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override
    public H2SqlEntity prepareBatchUpdate(ServiceReferenceMetric data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceMetricTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceMetricTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, data.getTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, data.getTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, data.getTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, data.getTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, data.getBusinessTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, data.getBusinessTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, data.getBusinessTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, data.getBusinessTransactionErrorDurationSum());

        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, data.getMqTransactionCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, data.getMqTransactionErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, data.getMqTransactionDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, data.getMqTransactionErrorDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        String sql = SqlBuilder.buildBatchUpdateSql(ServiceReferenceMetricTable.TABLE, source.keySet(), ServiceReferenceMetricTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
