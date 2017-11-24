/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.h2.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.skywalking.apm.collector.storage.dao.IServiceReferenceMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReferenceMetric;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReferenceMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ServiceReferenceH2MetricPersistenceDAO extends H2DAO implements IServiceReferenceMetricPersistenceDAO<H2SqlEntity, H2SqlEntity, ServiceReferenceMetric> {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceH2MetricPersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public ServiceReferenceH2MetricPersistenceDAO(H2Client client) {
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
                serviceReferenceMetric.setS1Lte(rs.getLong(ServiceReferenceMetricTable.COLUMN_S1_LTE));
                serviceReferenceMetric.setS3Lte(rs.getLong(ServiceReferenceMetricTable.COLUMN_S3_LTE));
                serviceReferenceMetric.setS5Lte(rs.getLong(ServiceReferenceMetricTable.COLUMN_S5_LTE));
                serviceReferenceMetric.setS5Gt(rs.getLong(ServiceReferenceMetricTable.COLUMN_S5_GT));
                serviceReferenceMetric.setSummary(rs.getLong(ServiceReferenceMetricTable.COLUMN_SUMMARY));
                serviceReferenceMetric.setError(rs.getLong(ServiceReferenceMetricTable.COLUMN_ERROR));
                serviceReferenceMetric.setCostSummary(rs.getLong(ServiceReferenceMetricTable.COLUMN_COST_SUMMARY));
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
        source.put(ServiceReferenceMetricTable.COLUMN_S1_LTE, data.getS1Lte());
        source.put(ServiceReferenceMetricTable.COLUMN_S3_LTE, data.getS3Lte());
        source.put(ServiceReferenceMetricTable.COLUMN_S5_LTE, data.getS5Lte());
        source.put(ServiceReferenceMetricTable.COLUMN_S5_GT, data.getS5Gt());
        source.put(ServiceReferenceMetricTable.COLUMN_SUMMARY, data.getSummary());
        source.put(ServiceReferenceMetricTable.COLUMN_ERROR, data.getError());
        source.put(ServiceReferenceMetricTable.COLUMN_COST_SUMMARY, data.getCostSummary());
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
        source.put(ServiceReferenceMetricTable.COLUMN_S1_LTE, data.getS1Lte());
        source.put(ServiceReferenceMetricTable.COLUMN_S3_LTE, data.getS3Lte());
        source.put(ServiceReferenceMetricTable.COLUMN_S5_LTE, data.getS5Lte());
        source.put(ServiceReferenceMetricTable.COLUMN_S5_GT, data.getS5Gt());
        source.put(ServiceReferenceMetricTable.COLUMN_SUMMARY, data.getSummary());
        source.put(ServiceReferenceMetricTable.COLUMN_ERROR, data.getError());
        source.put(ServiceReferenceMetricTable.COLUMN_COST_SUMMARY, data.getCostSummary());
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
