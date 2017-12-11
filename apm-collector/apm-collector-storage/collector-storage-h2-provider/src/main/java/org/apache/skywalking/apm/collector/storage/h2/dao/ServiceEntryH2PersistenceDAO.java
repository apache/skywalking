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
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IServiceEntryPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceEntry;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceEntryTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ServiceEntryH2PersistenceDAO extends H2DAO implements IServiceEntryPersistenceDAO<H2SqlEntity, H2SqlEntity, ServiceEntry> {

    private final Logger logger = LoggerFactory.getLogger(ServiceEntryH2PersistenceDAO.class);
    private static final String GET_SERVICE_ENTRY_SQL = "select * from {0} where {1} = ?";

    public ServiceEntryH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public ServiceEntry get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SERVICE_ENTRY_SQL, ServiceEntryTable.TABLE, ServiceEntryTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                ServiceEntry serviceEntry = new ServiceEntry(id);
                serviceEntry.setApplicationId(rs.getInt(ServiceEntryTable.COLUMN_APPLICATION_ID));
                serviceEntry.setEntryServiceId(rs.getInt(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID));
                serviceEntry.setEntryServiceName(rs.getString(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME));
                serviceEntry.setRegisterTime(rs.getLong(ServiceEntryTable.COLUMN_REGISTER_TIME));
                serviceEntry.setNewestTime(rs.getLong(ServiceEntryTable.COLUMN_NEWEST_TIME));
                return serviceEntry;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(ServiceEntry data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceEntryTable.COLUMN_ID, data.getId());
        source.put(ServiceEntryTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, data.getEntryServiceName());
        source.put(ServiceEntryTable.COLUMN_REGISTER_TIME, data.getRegisterTime());
        source.put(ServiceEntryTable.COLUMN_NEWEST_TIME, data.getNewestTime());
        String sql = SqlBuilder.buildBatchInsertSql(ServiceEntryTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(ServiceEntry data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceEntryTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID, data.getEntryServiceId());
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, data.getEntryServiceName());
        source.put(ServiceEntryTable.COLUMN_REGISTER_TIME, data.getRegisterTime());
        source.put(ServiceEntryTable.COLUMN_NEWEST_TIME, data.getNewestTime());
        String sql = SqlBuilder.buildBatchUpdateSql(ServiceEntryTable.TABLE, source.keySet(), ServiceEntryTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
