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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao;

import java.sql.*;
import java.util.*;
import org.apache.skywalking.apm.collector.client.shardingjdbc.*;
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceHeartBeatPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.register.*;
import org.slf4j.*;

/**
 * @author linjiaqi
 */
public class InstanceHeartBeatShardingjdbcPersistenceDAO extends ShardingjdbcDAO implements IInstanceHeartBeatPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, Instance> {

    private static final Logger logger = LoggerFactory.getLogger(InstanceHeartBeatShardingjdbcPersistenceDAO.class);

    public InstanceHeartBeatShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }

    private static final String GET_INSTANCE_HEARTBEAT_SQL = "select * from {0} where {1} = ?";

    @Override public Instance get(String id) {
        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(GET_INSTANCE_HEARTBEAT_SQL, InstanceTable.TABLE, InstanceTable.INSTANCE_ID.getName());
        Object[] params = new Object[] {id};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                Instance instance = new Instance();
                instance.setId(id);
                instance.setInstanceId(rs.getInt(InstanceTable.INSTANCE_ID.getName()));
                instance.setHeartBeatTime(rs.getLong(InstanceTable.HEARTBEAT_TIME.getName()));
                return instance;
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public ShardingjdbcSqlEntity prepareBatchInsert(Instance data) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public ShardingjdbcSqlEntity prepareBatchUpdate(Instance data) {
        ShardingjdbcSqlEntity entity = new ShardingjdbcSqlEntity();
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceTable.HEARTBEAT_TIME.getName(), data.getHeartBeatTime());
        String sql = SqlBuilder.buildBatchUpdateSql(InstanceTable.TABLE, target.keySet(), InstanceTable.INSTANCE_ID.getName());
        entity.setSql(sql);
        List<Object> params = new ArrayList<>(target.values());
        params.add(data.getId());
        entity.setParams(params.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long timeBucketBefore) {
    }
}
