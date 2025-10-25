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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.PprofTaskLog;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskLogQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

@RequiredArgsConstructor
public class JDBCPprofTaskLogQueryDAO implements IPprofTaskLogQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<PprofTaskLog> getTaskLogList() {
        List<String> tables = tableHelper.getTablesWithinTTL(PprofTaskLogRecord.INDEX_NAME);
        final List<PprofTaskLog> results = new ArrayList<PprofTaskLog>();
        for (String table : tables) {
            SQLAndParameters sqlAndParameters = buildSQL(table);
            List<PprofTaskLog> logs = jdbcClient.executeQuery(
                sqlAndParameters.sql(),
                resultSet -> {
                    final List<PprofTaskLog> tasks = new ArrayList<>();
                    while (resultSet.next()) {
                        tasks.add(parseLog(resultSet));
                    }
                    return tasks;
                },
                sqlAndParameters.parameters()
            );
            results.addAll(logs);
        }
        return results;
    }

    private SQLAndParameters buildSQL(String table) {
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>(2);
        sql.append("select * from ").append(table)
           .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
        parameters.add(PprofTaskLogRecord.INDEX_NAME);
        sql.append(" order by ").append(PprofTaskLogRecord.OPERATION_TIME).append(" desc");
        return new SQLAndParameters(sql.toString(), parameters);
    }

    private PprofTaskLog parseLog(ResultSet data) throws SQLException {
        return PprofTaskLog.builder()
                           .id(data.getString(PprofTaskLogRecord.TASK_ID))
                           .instanceId(data.getString(PprofTaskLogRecord.INSTANCE_ID))
                           .operationType(
                               PprofTaskLogOperationType.parse(data.getInt(PprofTaskLogRecord.OPERATION_TYPE)))
                           .operationTime(data.getLong(PprofTaskLogRecord.OPERATION_TIME))
                           .build();
    }
}
