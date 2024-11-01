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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.AsyncProfilerTaskLog;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskLogQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class JDBCAsyncProfilerTaskLogQueryDAO implements IAsyncProfilerTaskLogQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<AsyncProfilerTaskLog> getTaskLogList() {
        List<String> tables = tableHelper.getTablesWithinTTL(AsyncProfilerTaskLogRecord.INDEX_NAME);
        final List<AsyncProfilerTaskLog> results = new ArrayList<AsyncProfilerTaskLog>();
        for (String table : tables) {
            SQLAndParameters sqlAndParameters = buildSQL(table);
            List<AsyncProfilerTaskLog> logs = jdbcClient.executeQuery(
                    sqlAndParameters.sql(),
                    resultSet -> {
                        final List<AsyncProfilerTaskLog> tasks = new ArrayList<>();
                        while (resultSet.next()) {
                            tasks.add(parseLog(resultSet));
                        }
                        return tasks;
                    },
                    sqlAndParameters.parameters());
            results.addAll(logs);
        }
        return results;
    }

    private SQLAndParameters buildSQL(String table) {
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>(2);
        sql.append("select * from ").append(table)
                .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
        parameters.add(AsyncProfilerTaskLogRecord.INDEX_NAME);
        sql.append(" order by ").append(AsyncProfilerTaskLogRecord.OPERATION_TIME).append(" desc");
        return new SQLAndParameters(sql.toString(), parameters);
    }

    private AsyncProfilerTaskLog parseLog(ResultSet data) throws SQLException {
        return AsyncProfilerTaskLog.builder()
                .id(data.getString("id"))
                .taskId(data.getString(AsyncProfilerTaskLogRecord.TASK_ID))
                .instanceId(data.getString(AsyncProfilerTaskLogRecord.INSTANCE_ID))
                .operationType(AsyncProfilerTaskLogOperationType.parse(data.getInt(AsyncProfilerTaskLogRecord.OPERATION_TYPE)))
                .operationTime(data.getLong(AsyncProfilerTaskLogRecord.OPERATION_TIME))
                .build();
    }
}
