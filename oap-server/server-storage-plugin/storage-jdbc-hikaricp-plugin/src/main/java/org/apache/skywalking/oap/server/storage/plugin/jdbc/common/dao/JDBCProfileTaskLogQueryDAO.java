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
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class JDBCProfileTaskLogQueryDAO implements IProfileTaskLogQueryDAO {
    private final JDBCClient jdbcClient;

    @Override
    @SneakyThrows
    public List<ProfileTaskLog> getTaskLogList() {
        final StringBuilder sql = new StringBuilder();
        final ArrayList<Object> condition = new ArrayList<>(1);
        sql.append("select * from ").append(ProfileTaskLogRecord.INDEX_NAME).append(" where 1=1 ");

        sql.append("ORDER BY ").append(ProfileTaskLogRecord.OPERATION_TIME).append(" DESC ");

        return jdbcClient.executeQuery(sql.toString(), resultSet -> {
            final List<ProfileTaskLog> tasks = new ArrayList<>();
            while (resultSet.next()) {
                tasks.add(parseLog(resultSet));
            }
            return tasks;
        }, condition.toArray(new Object[0]));
    }

    private ProfileTaskLog parseLog(ResultSet data) throws SQLException {
        return ProfileTaskLog.builder()
                             .id(data.getString("id"))
                             .taskId(data.getString(ProfileTaskLogRecord.TASK_ID))
                             .instanceId(data.getString(ProfileTaskLogRecord.INSTANCE_ID))
                             .operationType(ProfileTaskLogOperationType.parse(data.getInt(ProfileTaskLogRecord.OPERATION_TYPE)))
                             .operationTime(data.getLong(ProfileTaskLogRecord.OPERATION_TIME))
                             .build();
    }
}
