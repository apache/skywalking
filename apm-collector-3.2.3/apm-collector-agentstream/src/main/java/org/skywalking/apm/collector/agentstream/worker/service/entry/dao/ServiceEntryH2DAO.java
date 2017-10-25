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

package org.skywalking.apm.collector.agentstream.worker.service.entry.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.service.ServiceEntryTable;
import org.skywalking.apm.collector.storage.h2.SqlBuilder;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.define.H2SqlEntity;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ServiceEntryH2DAO extends H2DAO implements IServiceEntryDAO, IPersistenceDAO<H2SqlEntity, H2SqlEntity> {
    private final Logger logger = LoggerFactory.getLogger(ServiceEntryH2DAO.class);
    private static final String GET_SERVICE_ENTRY_SQL = "select * from {0} where {1} = ?";

    @Override public Data get(String id, DataDefine dataDefine) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SERVICE_ENTRY_SQL, ServiceEntryTable.TABLE, ServiceEntryTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                Data data = dataDefine.build(id);
                data.setDataInteger(0, rs.getInt(ServiceEntryTable.COLUMN_APPLICATION_ID));
                data.setDataInteger(1, rs.getInt(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID));
                data.setDataString(1, rs.getString(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME));
                data.setDataLong(0, rs.getLong(ServiceEntryTable.COLUMN_REGISTER_TIME));
                data.setDataLong(1, rs.getLong(ServiceEntryTable.COLUMN_NEWEST_TIME));
                return data;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(Data data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceEntryTable.COLUMN_ID, data.getDataString(0));
        source.put(ServiceEntryTable.COLUMN_APPLICATION_ID, data.getDataInteger(0));
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID, data.getDataInteger(1));
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, data.getDataString(1));
        source.put(ServiceEntryTable.COLUMN_REGISTER_TIME, data.getDataLong(0));
        source.put(ServiceEntryTable.COLUMN_NEWEST_TIME, data.getDataLong(1));
        String sql = SqlBuilder.buildBatchInsertSql(ServiceEntryTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(Data data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceEntryTable.COLUMN_APPLICATION_ID, data.getDataInteger(0));
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID, data.getDataInteger(1));
        source.put(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, data.getDataString(1));
        source.put(ServiceEntryTable.COLUMN_REGISTER_TIME, data.getDataLong(0));
        source.put(ServiceEntryTable.COLUMN_NEWEST_TIME, data.getDataLong(1));
        String id = data.getDataString(0);
        String sql = SqlBuilder.buildBatchUpdateSql(ServiceEntryTable.TABLE, source.keySet(), ServiceEntryTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(id);
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }
}
