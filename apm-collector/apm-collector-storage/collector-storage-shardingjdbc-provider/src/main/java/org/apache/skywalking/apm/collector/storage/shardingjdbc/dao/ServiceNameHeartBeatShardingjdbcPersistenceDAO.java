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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IServiceNameHeartBeatPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class ServiceNameHeartBeatShardingjdbcPersistenceDAO extends ShardingjdbcDAO implements IServiceNameHeartBeatPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, ServiceName> {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNameHeartBeatShardingjdbcPersistenceDAO.class);

    public ServiceNameHeartBeatShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }

    private static final String GET_SERVICENAME_HEARTBEAT_SQL = "select * from {0} where {1} = ?";
    
    @GraphComputingMetric(name = "/persistence/get/" + ServiceNameTable.TABLE + "/heartbeat")
    @Override public ServiceName get(String id) {
        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(GET_SERVICENAME_HEARTBEAT_SQL, ServiceNameTable.TABLE, ServiceNameTable.SERVICE_ID.getName());
        Object[] params = new Object[] {id};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                ServiceName serviceName = new ServiceName();
                serviceName.setId(id);
                serviceName.setServiceId(rs.getInt(ServiceNameTable.SERVICE_ID.getName()));
                serviceName.setHeartBeatTime(rs.getLong(ServiceNameTable.HEARTBEAT_TIME.getName()));
                return serviceName;
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public ShardingjdbcSqlEntity prepareBatchInsert(ServiceName data) {
        throw new UnexpectedException("Received an service name heart beat message under service id= " + data.getId() + " , which doesn't exist.");
    }

    @Override public ShardingjdbcSqlEntity prepareBatchUpdate(ServiceName data) {
        ShardingjdbcSqlEntity entity = new ShardingjdbcSqlEntity();
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceNameTable.HEARTBEAT_TIME.getName(), data.getHeartBeatTime());
        String sql = SqlBuilder.buildBatchUpdateSql(ServiceNameTable.TABLE, target.keySet(), ServiceNameTable.SERVICE_ID.getName());
        entity.setSql(sql);
        List<Object> params = new ArrayList<>(target.values());
        params.add(data.getId());
        entity.setParams(params.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long timeBucketBefore) {
    }
}
