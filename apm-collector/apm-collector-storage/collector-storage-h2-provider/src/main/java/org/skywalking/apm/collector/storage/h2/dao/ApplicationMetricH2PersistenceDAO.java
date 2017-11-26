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
import org.skywalking.apm.collector.storage.dao.IApplicationMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.skywalking.apm.collector.storage.table.application.ApplicationMetric;
import org.skywalking.apm.collector.storage.table.application.ApplicationMetricTable;
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
                applicationMetric.setCalls(rs.getLong(ApplicationMetricTable.COLUMN_CALLS));
                applicationMetric.setErrorCalls(rs.getLong(ApplicationMetricTable.COLUMN_ERROR_CALLS));
                applicationMetric.setDurationSum(rs.getLong(ApplicationMetricTable.COLUMN_DURATION_SUM));
                applicationMetric.setErrorDurationSum(rs.getLong(ApplicationMetricTable.COLUMN_ERROR_DURATION_SUM));
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
        source.put(ApplicationMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ApplicationMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ApplicationMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
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
        source.put(ApplicationMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ApplicationMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ApplicationMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
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
