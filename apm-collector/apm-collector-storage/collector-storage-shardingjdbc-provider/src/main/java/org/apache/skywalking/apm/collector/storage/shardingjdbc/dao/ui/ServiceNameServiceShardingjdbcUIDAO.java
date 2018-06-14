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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ui;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceNameServiceUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceInfo;
import org.apache.skywalking.apm.network.proto.SpanType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class ServiceNameServiceShardingjdbcUIDAO extends ShardingjdbcDAO implements IServiceNameServiceUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNameServiceShardingjdbcUIDAO.class);

    public ServiceNameServiceShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public int getCount(long startTimeMillis) {
        String dynamicSql = "select count({0}) as cnt from {1} where {2} = ? and {3} >= ?";
        String sql = SqlBuilder.buildSql(dynamicSql, ServiceNameTable.SERVICE_ID.getName(), ServiceNameTable.TABLE, ServiceNameTable.SRC_SPAN_TYPE.getName(), ServiceNameTable.HEARTBEAT_TIME.getName());
        Object[] params = new Object[] {SpanType.Entry_VALUE, startTimeMillis};

        try (
                ResultSet rs = getClient().executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                return rs.getInt("cnt");
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override public List<ServiceInfo> searchService(String keyword, int applicationId, long startTimeMillis, int topN) {
        String dynamicSql = "select {0},{1} from {2} where {3} like ? and {4} = ? and {5} = ? and {6} >= ? limit ?";
        String sql = SqlBuilder.buildSql(dynamicSql, ServiceNameTable.SERVICE_ID.getName(), ServiceNameTable.SERVICE_NAME.getName(), ServiceNameTable.TABLE, 
                ServiceNameTable.SERVICE_NAME.getName(), ServiceNameTable.SRC_SPAN_TYPE.getName(), ServiceNameTable.APPLICATION_ID.getName(), ServiceNameTable.HEARTBEAT_TIME.getName());
        Object[] params = new Object[] {keyword == null ? "%%" : "%" + keyword + "%", SpanType.Entry_VALUE, applicationId, startTimeMillis, topN};

        List<ServiceInfo> serviceInfos = new LinkedList<>();
        try (
                ResultSet rs = getClient().executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                ServiceInfo serviceInfo = new ServiceInfo();
                serviceInfo.setId(rs.getInt(ServiceNameTable.SERVICE_ID.getName()));
                serviceInfo.setName(rs.getString(ServiceNameTable.SERVICE_NAME.getName()));
                serviceInfos.add(serviceInfo);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return serviceInfos;
    }
}
