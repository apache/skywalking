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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingScheduleRecord;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.H2TableInstaller;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class JDBCEBPFProfilingScheduleDAO implements IEBPFProfilingScheduleDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<EBPFProfilingSchedule> querySchedules(String taskId) {
        final var tables = tableHelper.getTablesForRead(EBPFProfilingScheduleRecord.INDEX_NAME);
        final var schedules = new ArrayList<EBPFProfilingSchedule>();
        for (final var table : tables) {
            final var sqlAndParameters = buildSQL(taskId, table);
            schedules.addAll(
                jdbcClient.executeQuery(
                    sqlAndParameters.sql(),
                    this::buildSchedules,
                    sqlAndParameters.parameters()
                )
            );
        }
        return schedules;
    }

    protected SQLAndParameters buildSQL(
        final String taskId,
        final String table) {
        final var sql = new StringBuilder();
        final var conditions = new StringBuilder();
        final var parameters = new ArrayList<>(4);
        sql.append("select * from ").append(table);
        conditions.append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
        parameters.add(EBPFProfilingScheduleRecord.INDEX_NAME);

        appendCondition(conditions, parameters, EBPFProfilingScheduleRecord.TASK_ID, "=", taskId);

        if (conditions.length() > 0) {
            sql.append(" where ").append(conditions);
        }
        return new SQLAndParameters(sql.toString(), parameters);
    }

    private List<EBPFProfilingSchedule> buildSchedules(ResultSet resultSet) throws SQLException {
        List<EBPFProfilingSchedule> schedules = new ArrayList<>();
        while (resultSet.next()) {
            EBPFProfilingSchedule schedule = new EBPFProfilingSchedule();
            schedule.setScheduleId(resultSet.getString(H2TableInstaller.ID_COLUMN));
            schedule.setTaskId(resultSet.getString(EBPFProfilingScheduleRecord.TASK_ID));
            schedule.setProcessId(resultSet.getString(EBPFProfilingScheduleRecord.PROCESS_ID));
            schedule.setStartTime(resultSet.getLong(EBPFProfilingScheduleRecord.START_TIME));
            schedule.setEndTime(resultSet.getLong(EBPFProfilingScheduleRecord.END_TIME));

            schedules.add(schedule);
        }
        return schedules;
    }

    private void appendCondition(StringBuilder conditionSql, List<Object> condition, String filed, String compare, Object data) {
        if (conditionSql.length() > 0) {
            conditionSql.append(" and ");
        }
        conditionSql.append(filed).append(compare).append("?");
        condition.add(data);
    }
}
