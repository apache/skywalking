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

import lombok.AllArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingScheduleTraffic;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class H2EBPFProfilingScheduleDAO implements IEBPFProfilingScheduleDAO {
    private JDBCHikariCPClient h2Client;

    @Override
    public List<EBPFProfilingSchedule> querySchedules(String taskId, long startTimeBucket, long endTimeBucket) throws IOException {
        final StringBuilder sql = new StringBuilder();
        final StringBuilder conditionSql = new StringBuilder();
        List<Object> condition = new ArrayList<>(4);
        sql.append("select * from ").append(EBPFProfilingScheduleTraffic.INDEX_NAME);

        if (StringUtil.isNotEmpty(taskId)) {
            appendCondition(conditionSql, condition, EBPFProfilingScheduleTraffic.TASK_ID, "=", taskId);
        }
        if (startTimeBucket > 0) {
            appendCondition(conditionSql, condition, EBPFProfilingTaskRecord.START_TIME, ">=", TimeBucket.getTimestamp(startTimeBucket));
        }
        if (endTimeBucket > 0) {
            appendCondition(conditionSql, condition, EBPFProfilingTaskRecord.START_TIME, "<=", TimeBucket.getTimestamp(endTimeBucket));
        }

        if (conditionSql.length() > 0) {
            sql.append(" where ").append(conditionSql);
        }

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                    connection, sql.toString(), condition.toArray(new Object[0]))) {
                return buildSchedules(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private List<EBPFProfilingSchedule> buildSchedules(ResultSet resultSet) throws SQLException {
        List<EBPFProfilingSchedule> schedules = new ArrayList<>();
        while (resultSet.next()) {
            EBPFProfilingSchedule schedule = new EBPFProfilingSchedule();
            schedule.setScheduleId(resultSet.getString(H2TableInstaller.ID_COLUMN));
            schedule.setTaskId(resultSet.getString(EBPFProfilingScheduleTraffic.TASK_ID));
            schedule.setProcessId(resultSet.getString(EBPFProfilingScheduleTraffic.PROCESS_ID));
            schedule.setStartTime(resultSet.getLong(EBPFProfilingScheduleTraffic.START_TIME));
            schedule.setEndTime(resultSet.getLong(EBPFProfilingScheduleTraffic.END_TIME));

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