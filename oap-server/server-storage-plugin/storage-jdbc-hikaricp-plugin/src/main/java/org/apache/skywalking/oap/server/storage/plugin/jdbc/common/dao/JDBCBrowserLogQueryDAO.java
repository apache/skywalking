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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.util.ArrayList;

import static java.util.Comparator.comparing;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class JDBCBrowserLogQueryDAO implements IBrowserLogQueryDAO {
    protected final JDBCClient jdbcClient;
    protected final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public BrowserErrorLogs queryBrowserErrorLogs(String serviceId,
                                                  String serviceVersionId,
                                                  String pagePathId,
                                                  BrowserErrorCategory category,
                                                  Duration duration,
                                                  int limit,
                                                  int from) {
        final var logs = new ArrayList<BrowserErrorLog>();
        final var tables = tableHelper.getTablesForRead(
            BrowserErrorLogRecord.INDEX_NAME, duration.getStartTimeBucket(), duration.getEndTimeBucket()
        );

        for (String table : tables) {
            final var sqlAndParameters = buildSQL(
                serviceId, serviceVersionId, pagePathId,
                category, duration, limit, from, table);

            jdbcClient.executeQuery(
                sqlAndParameters.sql(),
                resultSet -> {
                    while (resultSet.next()) {
                        String dataBinaryBase64 = resultSet.getString(BrowserErrorLogRecord.DATA_BINARY);
                        if (nonNull(dataBinaryBase64)) {
                            BrowserErrorLog log = parserDataBinary(dataBinaryBase64);
                            logs.add(log);
                        }
                    }

                    return null;
                },
                sqlAndParameters.parameters()
            );
        }

        return new BrowserErrorLogs(
            logs
                .stream()
                .sorted(comparing(BrowserErrorLog::getTime).reversed())
                .skip(from)
                .limit(limit)
                .collect(toList())
        );
    }

    protected SQLAndParameters buildSQL(
        String serviceId, String serviceVersionId, String pagePathId,
        BrowserErrorCategory category, Duration duration, int limit, int from,
        String table) {
        final var sql = new StringBuilder("select " + BrowserErrorLogRecord.DATA_BINARY);
        final var parameters = new ArrayList<>(9);
        sql.append(" from ").append(table)
           .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
        parameters.add(BrowserErrorLogRecord.INDEX_NAME);

        long startSecondTB = 0, endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }
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

        sql.append(" order by ").append(BrowserErrorLogRecord.TIMESTAMP).append(" DESC ");
        sql.append(" limit ").append(from + limit);

        return new SQLAndParameters(sql.toString(), parameters);
    }
}
