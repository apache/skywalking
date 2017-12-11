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
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ApplicationComponentH2PersistenceDAO extends H2DAO implements IApplicationComponentPersistenceDAO<H2SqlEntity, H2SqlEntity, ApplicationComponent> {
    private final Logger logger = LoggerFactory.getLogger(ApplicationComponentH2PersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public ApplicationComponentH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override
    public ApplicationComponent get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, ApplicationComponentTable.TABLE, ApplicationComponentTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                ApplicationComponent applicationComponent = new ApplicationComponent(id);
                applicationComponent.setComponentId(rs.getInt(ApplicationComponentTable.COLUMN_COMPONENT_ID));
                applicationComponent.setPeerId(rs.getInt(ApplicationComponentTable.COLUMN_PEER_ID));
                applicationComponent.setTimeBucket(rs.getLong(ApplicationComponentTable.COLUMN_TIME_BUCKET));
                return applicationComponent;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public H2SqlEntity prepareBatchInsert(ApplicationComponent data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(ApplicationComponentTable.COLUMN_ID, data.getId());
        source.put(ApplicationComponentTable.COLUMN_COMPONENT_ID, data.getComponentId());
        source.put(ApplicationComponentTable.COLUMN_PEER_ID, data.getPeerId());
        source.put(ApplicationComponentTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        String sql = SqlBuilder.buildBatchInsertSql(ApplicationComponentTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override
    public H2SqlEntity prepareBatchUpdate(ApplicationComponent data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(ApplicationComponentTable.COLUMN_COMPONENT_ID, data.getComponentId());
        source.put(ApplicationComponentTable.COLUMN_PEER_ID, data.getPeerId());
        source.put(ApplicationComponentTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchUpdateSql(ApplicationComponentTable.TABLE, source.keySet(), ApplicationComponentTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
