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

package org.apache.skywalking.apm.collector.storage.h2.base.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.data.CommonTable;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractPersistenceH2DAO<STREAM_DATA extends StreamData> extends H2DAO implements IPersistenceDAO<H2SqlEntity, H2SqlEntity, STREAM_DATA> {

    private final Logger logger = LoggerFactory.getLogger(AbstractPersistenceH2DAO.class);

    public AbstractPersistenceH2DAO(H2Client client) {
        super(client);
    }

    private static final String GET_SQL = "select * from {0} where {1} = ?";

    protected abstract STREAM_DATA h2DataToStreamData(ResultSet resultSet) throws SQLException;

    protected abstract String tableName();

    @Override public final STREAM_DATA get(String id) {
        String sql = SqlBuilder.buildSql(GET_SQL, tableName(), CommonTable.ID.getName());

        Object[] params = new Object[] {id};
        try (ResultSet resultSet = getClient().executeQuery(sql, params)) {
            if (resultSet.next()) {
                return h2DataToStreamData(resultSet);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    protected abstract Map<String, Object> streamDataToH2Data(STREAM_DATA streamData);

    @Override public final H2SqlEntity prepareBatchInsert(STREAM_DATA streamData) {
        Map<String, Object> source = streamDataToH2Data(streamData);
        source.put(CommonTable.ID.getName(), streamData.getId());

        H2SqlEntity entity = new H2SqlEntity();

        String sql = SqlBuilder.buildBatchInsertSql(tableName(), source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public final H2SqlEntity prepareBatchUpdate(STREAM_DATA streamData) {
        Map<String, Object> source = streamDataToH2Data(streamData);

        H2SqlEntity entity = new H2SqlEntity();
        String sql = SqlBuilder.buildBatchUpdateSql(tableName(), source.keySet(), CommonTable.ID.getName());
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(streamData.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public final void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
