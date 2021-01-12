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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import com.google.common.base.Strings;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.LogState;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;

import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.CONTENT;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.CONTENT_TYPE;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.ENDPOINT_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.ENDPOINT_NAME;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.SERVICE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.SERVICE_INSTANCE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.STATUS_CODE;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TIMESTAMP;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TRACE_ID;

public class H2LogQueryDAO implements ILogQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2LogQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public Logs queryLogs(String metricName, int serviceId, int serviceInstanceId, String endpointId, String traceId,
                          LogState state, String stateCode, Pagination paging, int from, int limit, long startSecondTB,
                          long endSecondTB) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>(10);

        sql.append("from ").append(metricName).append(" where ");
        sql.append(" 1=1 ");
        if (startSecondTB != 0 && endSecondTB != 0) {
            sql.append(" and ").append(AbstractLogRecord.TIME_BUCKET).append(" >= ?");
            parameters.add(startSecondTB);
            sql.append(" and ").append(AbstractLogRecord.TIME_BUCKET).append(" <= ?");
            parameters.add(endSecondTB);
        }

        if (serviceId != Const.NONE) {
            sql.append(" and ").append(SERVICE_ID).append(" = ?");
            parameters.add(serviceId);
        }
        if (serviceInstanceId != Const.NONE) {
            sql.append(" and ").append(AbstractLogRecord.SERVICE_INSTANCE_ID).append(" = ?");
            parameters.add(serviceInstanceId);
        }
        if (StringUtil.isNotEmpty(endpointId)) {
            sql.append(" and ").append(AbstractLogRecord.ENDPOINT_ID).append(" = ?");
            parameters.add(endpointId);
        }
        if (!Strings.isNullOrEmpty(stateCode)) {
            sql.append(" and ").append(AbstractLogRecord.STATUS_CODE).append(" = ?");
            parameters.add(stateCode);
        }
        if (!Strings.isNullOrEmpty(traceId)) {
            sql.append(" and ").append(TRACE_ID).append(" = ?");
            parameters.add(traceId);
        }
        if (LogState.ERROR.equals(state)) {
            sql.append(" and ").append(AbstractLogRecord.IS_ERROR).append(" = ?");
            parameters.add(BooleanUtils.booleanToValue(true));
        } else if (LogState.SUCCESS.equals(state)) {
            sql.append(" and ").append(AbstractLogRecord.IS_ERROR).append(" = ?");
            parameters.add(BooleanUtils.booleanToValue(false));
        }

        Logs logs = new Logs();
        try (Connection connection = h2Client.getConnection()) {

            try (ResultSet resultSet = h2Client.executeQuery(connection, buildCountStatement(sql.toString()), parameters
                .toArray(new Object[0]))) {
                while (resultSet.next()) {
                    logs.setTotal(resultSet.getInt("total"));
                }
            }

            buildLimit(sql, from, limit);

            try (ResultSet resultSet = h2Client.executeQuery(
                connection, "select * " + sql.toString(), parameters.toArray(new Object[0]))) {
                while (resultSet.next()) {
                    Log log = new Log();
                    log.setServiceId(resultSet.getString(SERVICE_ID));
                    log.setServiceInstanceId(resultSet.getString(SERVICE_INSTANCE_ID));
                    log.setEndpointId(resultSet.getString(ENDPOINT_ID));
                    log.setEndpointName(resultSet.getString(ENDPOINT_NAME));
                    log.setTraceId(resultSet.getString(TRACE_ID));
                    log.setTimestamp(resultSet.getString(TIMESTAMP));
                    log.setStatusCode(resultSet.getString(STATUS_CODE));
                    log.setContentType(ContentType.instanceOf(resultSet.getInt(CONTENT_TYPE)));
                    log.setContent(resultSet.getString(CONTENT));
                    logs.getLogs().add(log);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        return logs;
    }

    protected String buildCountStatement(String sql) {
        return "select count(1) total from (select 1 " + sql + " )";
    }

    protected void buildLimit(StringBuilder sql, int from, int limit) {
        sql.append(" LIMIT ").append(limit);
        sql.append(" OFFSET ").append(from);
    }
}
