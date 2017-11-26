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
import org.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;
import org.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ApplicationReferenceMetricH2PersistenceDAO extends H2DAO implements IApplicationReferenceMetricPersistenceDAO<H2SqlEntity, H2SqlEntity, ApplicationReferenceMetric> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationReferenceMetricH2PersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public ApplicationReferenceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public ApplicationReferenceMetric get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, ApplicationReferenceMetricTable.TABLE, ApplicationReferenceMetricTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                ApplicationReferenceMetric applicationReferenceMetric = new ApplicationReferenceMetric(id);
                applicationReferenceMetric.setFrontApplicationId(rs.getInt(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID));
                applicationReferenceMetric.setBehindApplicationId(rs.getInt(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID));
                applicationReferenceMetric.setCalls(rs.getLong(ApplicationReferenceMetricTable.COLUMN_CALLS));
                applicationReferenceMetric.setErrorCalls(rs.getLong(ApplicationReferenceMetricTable.COLUMN_ERROR_CALLS));
                applicationReferenceMetric.setDurationSum(rs.getLong(ApplicationReferenceMetricTable.COLUMN_DURATION_SUM));
                applicationReferenceMetric.setErrorDurationSum(rs.getLong(ApplicationReferenceMetricTable.COLUMN_ERROR_DURATION_SUM));
                applicationReferenceMetric.setSatisfiedCount(rs.getLong(ApplicationReferenceMetricTable.COLUMN_SATISFIED_COUNT));
                applicationReferenceMetric.setToleratingCount(rs.getLong(ApplicationReferenceMetricTable.COLUMN_TOLERATING_COUNT));
                applicationReferenceMetric.setFrustratedCount(rs.getLong(ApplicationReferenceMetricTable.COLUMN_FRUSTRATED_COUNT));
                applicationReferenceMetric.setTimeBucket(rs.getLong(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET));
                return applicationReferenceMetric;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(ApplicationReferenceMetric data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(ApplicationReferenceMetricTable.COLUMN_ID, data.getId());
        source.put(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ApplicationReferenceMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_SATISFIED_COUNT, data.getSatisfiedCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_TOLERATING_COUNT, data.getToleratingCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_FRUSTRATED_COUNT, data.getFrustratedCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchInsertSql(ApplicationReferenceMetricTable.TABLE, source.keySet());
        entity.setSql(sql);

        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(ApplicationReferenceMetric data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ApplicationReferenceMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ApplicationReferenceMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
        source.put(ApplicationReferenceMetricTable.COLUMN_SATISFIED_COUNT, data.getSatisfiedCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_TOLERATING_COUNT, data.getToleratingCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_FRUSTRATED_COUNT, data.getFrustratedCount());
        source.put(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchUpdateSql(ApplicationReferenceMetricTable.TABLE, source.keySet(), ApplicationReferenceMetricTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
