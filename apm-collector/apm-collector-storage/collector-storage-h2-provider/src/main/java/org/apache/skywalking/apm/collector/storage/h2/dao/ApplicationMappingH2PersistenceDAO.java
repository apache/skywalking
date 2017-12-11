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
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMappingPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMapping;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMappingTable;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ApplicationMappingH2PersistenceDAO extends H2DAO implements IApplicationMappingPersistenceDAO<H2SqlEntity, H2SqlEntity, ApplicationMapping> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationMappingH2PersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public ApplicationMappingH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public ApplicationMapping get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, ApplicationMappingTable.TABLE, ApplicationMappingTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                ApplicationMapping applicationMapping = new ApplicationMapping(id);
                applicationMapping.setApplicationId(rs.getInt(ApplicationMappingTable.COLUMN_APPLICATION_ID));
                applicationMapping.setAddressId(rs.getInt(ApplicationMappingTable.COLUMN_ADDRESS_ID));
                applicationMapping.setTimeBucket(rs.getLong(ApplicationMappingTable.COLUMN_TIME_BUCKET));
                return applicationMapping;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(ApplicationMapping applicationMapping) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(ApplicationMappingTable.COLUMN_ID, applicationMapping.getId());
        source.put(ApplicationMappingTable.COLUMN_APPLICATION_ID, applicationMapping.getApplicationId());
        source.put(ApplicationMappingTable.COLUMN_ADDRESS_ID, applicationMapping.getAddressId());
        source.put(ApplicationMappingTable.COLUMN_TIME_BUCKET, applicationMapping.getTimeBucket());
        String sql = SqlBuilder.buildBatchInsertSql(ApplicationMappingTable.TABLE, source.keySet());
        entity.setSql(sql);

        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(ApplicationMapping applicationMapping) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(ApplicationMappingTable.COLUMN_APPLICATION_ID, applicationMapping.getApplicationId());
        source.put(ApplicationMappingTable.COLUMN_ADDRESS_ID, applicationMapping.getAddressId());
        source.put(ApplicationMappingTable.COLUMN_TIME_BUCKET, applicationMapping.getTimeBucket());
        String sql = SqlBuilder.buildBatchUpdateSql(ApplicationMappingTable.TABLE, source.keySet(), ApplicationMappingTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(applicationMapping.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
