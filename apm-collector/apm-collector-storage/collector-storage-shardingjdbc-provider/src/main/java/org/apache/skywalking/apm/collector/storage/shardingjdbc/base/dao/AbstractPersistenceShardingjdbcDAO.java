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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.data.CommonTable;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public abstract class AbstractPersistenceShardingjdbcDAO<STREAM_DATA extends StreamData> extends ShardingjdbcDAO implements IPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, STREAM_DATA> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractPersistenceShardingjdbcDAO.class);

    public AbstractPersistenceShardingjdbcDAO(ShardingjdbcClient client) {
        super(client);
    }

    private static final String GET_SQL = "select * from {0} where {1} = ?";

    protected abstract STREAM_DATA shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException;

    protected abstract String tableName();

    @Override public STREAM_DATA get(String id) {
        String sql = SqlBuilder.buildSql(GET_SQL, tableName(), CommonTable.ID.getName());

        Object[] params = new Object[] {id};
        try (
                ResultSet rs = getClient().executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                return shardingjdbcDataToStreamData(rs);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    protected abstract Map<String, Object> streamDataToShardingjdbcData(STREAM_DATA streamData);

    @Override public final ShardingjdbcSqlEntity prepareBatchInsert(STREAM_DATA streamData) {
        Map<String, Object> source = streamDataToShardingjdbcData(streamData);
        source.put(CommonTable.ID.getName(), streamData.getId());

        ShardingjdbcSqlEntity entity = new ShardingjdbcSqlEntity();

        String sql = SqlBuilder.buildBatchInsertSql(tableName(), source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public final ShardingjdbcSqlEntity prepareBatchUpdate(STREAM_DATA streamData) {
        Map<String, Object> source = streamDataToShardingjdbcData(streamData);

        ShardingjdbcSqlEntity entity = new ShardingjdbcSqlEntity();
        String sql = SqlBuilder.buildBatchUpdateSql(tableName(), source.keySet(), CommonTable.ID.getName());
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(streamData.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }
    
    protected abstract String timeBucketColumnNameForDelete();

    @Override public void deleteHistory(Long timeBucketBefore) {
        ShardingjdbcClient client = getClient();
        
        String dynamicSql = "delete from {0} where {1} <= ?";
        String sql = SqlBuilder.buildSql(dynamicSql, tableName(), timeBucketColumnNameForDelete());
        
        Object[] params = new Object[] {timeBucketBefore};
        
        try {
            client.execute(sql, params);
            logger.info("Deleted history rows from {} table.", tableName());
        } catch (ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
