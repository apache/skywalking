/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import com.google.common.base.Strings;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

import static java.util.Objects.nonNull;

@RequiredArgsConstructor
public class H2BrowserLogQueryDAO implements IBrowserLogQueryDAO {
    private final JDBCHikariCPClient h2Client;

    @Override
    public BrowserErrorLogs queryBrowserErrorLogs(String serviceId,
                                                  String serviceVersionId,
                                                  String pagePathId,
                                                  String pagePath,
                                                  BrowserErrorCategory category,
                                                  long startSecondTB,
                                                  long endSecondTB,
                                                  int limit,
                                                  int from) throws IOException {
        StringBuilder sql = new StringBuilder();

        List<Object> parameters = new ArrayList<>(9);

        sql.append("from ").append(BrowserErrorLogRecord.INDEX_NAME)
           .append(" where ").append(" 1=1 ");

        if (startSecondTB != 0 && endSecondTB != 0) {
            sql.append(" and ").append(BrowserErrorLogRecord.TIME_BUCKET).append(" >= ?");
            parameters.add(startSecondTB);
            sql.append(" and ").append(BrowserErrorLogRecord.TIME_BUCKET).append(" <= ?");
            parameters.add(endSecondTB);
        }

        if (StringUtil.isNotEmpty(serviceId)) {
            sql.append(" and ").append(BrowserErrorLogRecord.SERVICE_ID).append(" = ?");
            parameters.add(serviceId);
        }
        if (StringUtil.isNotEmpty(serviceVersionId)) {
            sql.append(" and ").append(BrowserErrorLogRecord.SERVICE_VERSION_ID).append(" = ?");
            parameters.add(serviceVersionId);
        }
        if (StringUtil.isNotEmpty(pagePathId)) {
            sql.append(" and ").append(BrowserErrorLogRecord.PAGE_PATH_ID).append(" = ?");
            parameters.add(pagePathId);
        }
        if (nonNull(category)) {
            sql.append(" and ").append(BrowserErrorLogRecord.ERROR_CATEGORY).append(" = ?");
            parameters.add(category.getValue());
        }
        if (!Strings.isNullOrEmpty(pagePath)) {
            sql.append(" and ").append(BrowserErrorLogRecord.PAGE_PATH).append(" like concat('%',?,'%')");
            parameters.add(pagePath);
        }

        BrowserErrorLogs logs = new BrowserErrorLogs();
        try (Connection connection = h2Client.getConnection()) {

            try (ResultSet resultSet = h2Client.executeQuery(connection, buildCountStatement(sql.toString()), parameters
                .toArray(new Object[0]))) {
                while (resultSet.next()) {
                    logs.setTotal(resultSet.getInt("total"));
                }
            }

            buildLimit(sql, from, limit);

            try (ResultSet resultSet = h2Client.executeQuery(
                connection, "select " + BrowserErrorLogRecord.DATA_BINARY + " " + sql.toString(),
                parameters.toArray(new Object[0])
            )) {
                while (resultSet.next()) {
                    String dataBinaryBase64 = resultSet.getString(BrowserErrorLogRecord.DATA_BINARY);
                    if (nonNull(dataBinaryBase64)) {
                        BrowserErrorLog log = parserDataBinary(dataBinaryBase64);
                        logs.getLogs().add(log);
                    }
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
        sql.append(" limit ").append(limit);
        sql.append(" offset ").append(from);
    }
}
