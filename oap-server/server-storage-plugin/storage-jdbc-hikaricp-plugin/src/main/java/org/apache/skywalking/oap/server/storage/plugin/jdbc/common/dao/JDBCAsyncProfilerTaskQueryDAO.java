/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerEventType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class JDBCAsyncProfilerTaskQueryDAO implements IAsyncProfilerTaskQueryDAO {
    private static final Gson GSON = new Gson();

    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<AsyncProfilerTask> getTaskList(String serviceId, Long startTimeBucket, Long endTimeBucket, Integer limit) throws IOException {
        final var results = new ArrayList<AsyncProfilerTask>();
        final var tables = startTimeBucket == null || endTimeBucket == null ?
                tableHelper.getTablesWithinTTL(AsyncProfilerTaskRecord.INDEX_NAME) :
                tableHelper.getTablesForRead(AsyncProfilerTaskRecord.INDEX_NAME, startTimeBucket, endTimeBucket);
        for (final var table : tables) {
            List<Object> condition = new ArrayList<>(4);
            StringBuilder sql = new StringBuilder()
                    .append("select * from ").append(table)
                    .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            condition.add(AsyncProfilerTaskRecord.INDEX_NAME);

            if (StringUtil.isNotEmpty(serviceId)) {
                sql.append(" and ").append(AsyncProfilerTaskRecord.SERVICE_ID).append("=? ");
                condition.add(serviceId);
            }

            if (startTimeBucket != null) {
                sql.append(" and ").append(AsyncProfilerTaskRecord.TIME_BUCKET).append(" >= ? ");
                condition.add(startTimeBucket);
            }

            if (endTimeBucket != null) {
                sql.append(" and ").append(AsyncProfilerTaskRecord.TIME_BUCKET).append(" <= ? ");
                condition.add(endTimeBucket);
            }

            sql.append(" ORDER BY ").append(AsyncProfilerTaskRecord.CREATE_TIME).append(" DESC ");

            if (limit != null) {
                sql.append(" LIMIT ").append(limit);
            }

            results.addAll(
                    jdbcClient.executeQuery(
                            sql.toString(),
                            resultSet -> {
                                final var tasks = new ArrayList<AsyncProfilerTask>();
                                while (resultSet.next()) {
                                    tasks.add(buildAsyncProfilerTask(resultSet));
                                }
                                return tasks;
                            },
                            condition.toArray(new Object[0]))
            );
        }
        return limit == null ?
                results :
                results
                        .stream()
                        .limit(limit)
                        .collect(toList());
    }

    @Override
    @SneakyThrows
    public AsyncProfilerTask getById(String id) throws IOException {
        final var tables = tableHelper.getTablesWithinTTL(AsyncProfilerTaskRecord.INDEX_NAME);
        for (String table : tables) {
            final StringBuilder sql = new StringBuilder();
            final List<Object> condition = new ArrayList<>(1);
            sql.append("select * from ").append(table)
                    .append(" where ")
                    .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ")
                    .append(" and ")
                    .append(AsyncProfilerTaskRecord.TASK_ID + "=? LIMIT 1");
            condition.add(AsyncProfilerTaskRecord.INDEX_NAME);
            condition.add(id);

            final var r = jdbcClient.executeQuery(
                    sql.toString(),
                    resultSet -> {
                        if (resultSet.next()) {
                            return buildAsyncProfilerTask(resultSet);
                        }
                        return null;
                    },
                    condition.toArray(new Object[0]));
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private AsyncProfilerTask buildAsyncProfilerTask(ResultSet data) throws SQLException {
        Type listType = new TypeToken<List<String>>() {
        }.getType();
        String events = data.getString(AsyncProfilerTaskRecord.EVENT_TYPES);
        List<String> eventList = GSON.fromJson(events, listType);
        String serviceInstanceIds = data.getString(AsyncProfilerTaskRecord.SERVICE_INSTANCE_IDS);
        List<String> serviceInstanceIdList = GSON.fromJson(serviceInstanceIds, listType);
        return AsyncProfilerTask.builder()
                .id(data.getString(AsyncProfilerTaskRecord.TASK_ID))
                .serviceId(data.getString(AsyncProfilerTaskRecord.SERVICE_ID))
                .serviceInstanceIds(serviceInstanceIdList)
                .createTime(data.getLong(AsyncProfilerTaskRecord.CREATE_TIME))
                .duration(data.getInt(AsyncProfilerTaskRecord.DURATION))
                .events(AsyncProfilerEventType.valueOfList(eventList))
                .execArgs(data.getString(AsyncProfilerTaskRecord.EXEC_ARGS))
                .build();
    }
}
