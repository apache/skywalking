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
import org.skywalking.apm.collector.storage.dao.IServiceMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.skywalking.apm.collector.storage.table.service.ServiceMetric;
import org.skywalking.apm.collector.storage.table.service.ServiceMetricTable;
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
                serviceMetric.setCalls(rs.getLong(ServiceMetricTable.COLUMN_CALLS));
                serviceMetric.setErrorCalls(rs.getLong(ServiceMetricTable.COLUMN_ERROR_CALLS));
                serviceMetric.setDurationSum(rs.getLong(ServiceMetricTable.COLUMN_DURATION_SUM));
                serviceMetric.setErrorDurationSum(rs.getLong(ServiceMetricTable.COLUMN_ERROR_DURATION_SUM));
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
        source.put(ServiceMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ServiceMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ServiceMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ServiceMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
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
        source.put(ServiceMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ServiceMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ServiceMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ServiceMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
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
