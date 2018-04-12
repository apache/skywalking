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
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceHeartBeatPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class InstanceHeartBeatH2PersistenceDAO extends H2DAO implements IInstanceHeartBeatPersistenceDAO<H2SqlEntity, H2SqlEntity, Instance> {

    private static final Logger logger = LoggerFactory.getLogger(InstanceHeartBeatH2PersistenceDAO.class);

    public InstanceHeartBeatH2PersistenceDAO(H2Client client) {
        super(client);
    }

    private static final String GET_INSTANCE_HEARTBEAT_SQL = "select * from {0} where {1} = ?";

    @Override public Instance get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_INSTANCE_HEARTBEAT_SQL, InstanceTable.TABLE, InstanceTable.INSTANCE_ID.getName());
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                Instance instance = new Instance();
                instance.setId(id);
                instance.setInstanceId(rs.getInt(InstanceTable.INSTANCE_ID.getName()));
                instance.setHeartBeatTime(rs.getLong(InstanceTable.HEARTBEAT_TIME.getName()));
                return instance;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(Instance data) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public H2SqlEntity prepareBatchUpdate(Instance data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceTable.HEARTBEAT_TIME.getName(), data.getHeartBeatTime());
        String sql = SqlBuilder.buildBatchUpdateSql(InstanceTable.TABLE, target.keySet(), InstanceTable.INSTANCE_ID.getName());
        entity.setSql(sql);
        List<Object> params = new ArrayList<>(target.values());
        params.add(data.getId());
        entity.setParams(params.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
