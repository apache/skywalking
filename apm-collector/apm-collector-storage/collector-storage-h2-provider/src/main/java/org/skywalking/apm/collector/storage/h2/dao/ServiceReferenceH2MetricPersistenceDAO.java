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
import org.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;
import org.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;
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
                serviceReferenceMetric.setCalls(rs.getLong(ServiceReferenceMetricTable.COLUMN_CALLS));
                serviceReferenceMetric.setErrorCalls(rs.getLong(ServiceReferenceMetricTable.COLUMN_ERROR_CALLS));
                serviceReferenceMetric.setDurationSum(rs.getLong(ServiceReferenceMetricTable.COLUMN_DURATION_SUM));
                serviceReferenceMetric.setErrorDurationSum(rs.getLong(ServiceReferenceMetricTable.COLUMN_ERROR_DURATION_SUM));
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
        source.put(ServiceReferenceMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
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
        source.put(ServiceReferenceMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ServiceReferenceMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ServiceReferenceMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
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
