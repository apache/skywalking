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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.cache;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.cache.IServiceNameCacheDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class ServiceNameShardingjdbcCacheDAO extends ShardingjdbcDAO implements IServiceNameCacheDAO {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNameShardingjdbcCacheDAO.class);

    private static final String GET_SERVICE_NAME_SQL = "select {0},{1} from {2} where {3} = ?";
    private static final String GET_SERVICE_ID_SQL = "select {0} from {1} where {2} = ? and {3} = ? and {4} = ? limit 1";

    public ServiceNameShardingjdbcCacheDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public ServiceName get(int serviceId) {
        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(GET_SERVICE_NAME_SQL, ServiceNameTable.APPLICATION_ID.getName(), ServiceNameTable.SERVICE_NAME.getName(),
            ServiceNameTable.TABLE, ServiceNameTable.SERVICE_ID.getName());
        Object[] params = new Object[] {serviceId};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                ServiceName serviceName = new ServiceName();
                serviceName.setServiceId(serviceId);
                serviceName.setApplicationId(rs.getInt(ServiceNameTable.APPLICATION_ID.getName()));
                serviceName.setServiceName(rs.getString(ServiceNameTable.SERVICE_NAME.getName()));
                return serviceName;
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public int getServiceId(int applicationId, int srcSpanType, String serviceName) {
        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(GET_SERVICE_ID_SQL, ServiceNameTable.SERVICE_ID.getName(), ServiceNameTable.TABLE,
            ServiceNameTable.APPLICATION_ID.getName(), ServiceNameTable.SRC_SPAN_TYPE.getName(), ServiceNameTable.SERVICE_NAME.getName());

        Object[] params = new Object[] {applicationId, srcSpanType, serviceName};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                return rs.getInt(ServiceNameTable.SERVICE_ID.getName());
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }
}
