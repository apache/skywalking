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
import org.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.skywalking.apm.collector.storage.dao.IServiceReferenceDAO;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReference;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReferenceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ServiceReferenceH2DAO extends H2DAO implements IServiceReferenceDAO, IPersistenceDAO<H2SqlEntity, H2SqlEntity, ServiceReference> {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceH2DAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    @Override
    public ServiceReference get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, ServiceReferenceTable.TABLE, ServiceReferenceTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                ServiceReference serviceReference = new ServiceReference(id);
                serviceReference.setEntryServiceId(rs.getInt(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID));
                serviceReference.setEntryServiceName(rs.getString(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME));
                serviceReference.setFrontServiceId(rs.getInt(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID));
                serviceReference.setFrontServiceName(rs.getString(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME));
                serviceReference.setBehindServiceId(rs.getInt(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID));
                serviceReference.setBehindServiceName(rs.getString(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME));
                serviceReference.setS1Lte(rs.getLong(ServiceReferenceTable.COLUMN_S1_LTE));
                serviceReference.setS3Lte(rs.getLong(ServiceReferenceTable.COLUMN_S3_LTE));
                serviceReference.setS5Lte(rs.getLong(ServiceReferenceTable.COLUMN_S5_LTE));
                serviceReference.setS5Gt(rs.getLong(ServiceReferenceTable.COLUMN_S5_GT));
                serviceReference.setSummary(rs.getLong(ServiceReferenceTable.COLUMN_SUMMARY));
                serviceReference.setError(rs.getLong(ServiceReferenceTable.COLUMN_ERROR));
                serviceReference.setCostSummary(rs.getLong(ServiceReferenceTable.COLUMN_COST_SUMMARY));
                serviceReference.setTimeBucket(rs.getLong(ServiceReferenceTable.COLUMN_TIME_BUCKET));
                return serviceReference;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public H2SqlEntity prepareBatchInsert(ServiceReference data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceTable.COLUMN_ID, data.getId());
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, data.getEntryServiceName());
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, data.getFrontServiceName());
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, data.getBehindServiceName());
        source.put(ServiceReferenceTable.COLUMN_S1_LTE, data.getS1Lte());
        source.put(ServiceReferenceTable.COLUMN_S3_LTE, data.getS3Lte());
        source.put(ServiceReferenceTable.COLUMN_S5_LTE, data.getS5Lte());
        source.put(ServiceReferenceTable.COLUMN_S5_GT, data.getS5Gt());
        source.put(ServiceReferenceTable.COLUMN_SUMMARY, data.getSummary());
        source.put(ServiceReferenceTable.COLUMN_ERROR, data.getError());
        source.put(ServiceReferenceTable.COLUMN_COST_SUMMARY, data.getCostSummary());
        source.put(ServiceReferenceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        String sql = SqlBuilder.buildBatchInsertSql(ServiceReferenceTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override
    public H2SqlEntity prepareBatchUpdate(ServiceReference data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, data.getEntryServiceName());
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, data.getFrontServiceName());
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, data.getBehindServiceName());
        source.put(ServiceReferenceTable.COLUMN_S1_LTE, data.getS1Lte());
        source.put(ServiceReferenceTable.COLUMN_S3_LTE, data.getS3Lte());
        source.put(ServiceReferenceTable.COLUMN_S5_LTE, data.getS5Lte());
        source.put(ServiceReferenceTable.COLUMN_S5_GT, data.getS5Gt());
        source.put(ServiceReferenceTable.COLUMN_SUMMARY, data.getSummary());
        source.put(ServiceReferenceTable.COLUMN_ERROR, data.getError());
        source.put(ServiceReferenceTable.COLUMN_COST_SUMMARY, data.getCostSummary());
        source.put(ServiceReferenceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        String sql = SqlBuilder.buildBatchUpdateSql(ServiceReferenceTable.TABLE, source.keySet(), ServiceReferenceTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }
}
