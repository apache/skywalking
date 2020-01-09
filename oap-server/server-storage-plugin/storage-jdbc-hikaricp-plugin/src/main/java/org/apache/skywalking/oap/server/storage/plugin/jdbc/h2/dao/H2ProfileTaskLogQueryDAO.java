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

import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author MrPro
 */
public class H2ProfileTaskLogQueryDAO implements IProfileTaskLogQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2ProfileTaskLogQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public List<ProfileTaskLog> getTaskLogList(String taskId) throws IOException {
        final StringBuilder sql = new StringBuilder();
        final ArrayList<Object> condition = new ArrayList<>(1);
        sql.append("select * from ").append(ProfileTaskLogRecord.INDEX_NAME).append(" where 1=1 ");

        if (taskId != null) {
            sql.append(" and ").append(ProfileTaskLogRecord.TASK_ID).append(" = ?");
        }

        sql.append("ORDER BY ").append(ProfileTaskLogRecord.OPERATION_TIME).append(" DESC ");

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), condition.toArray(new Object[0]))) {
                final LinkedList<ProfileTaskLog> tasks = new LinkedList<>();
                while (resultSet.next()) {
                    tasks.add(parseLog(resultSet));
                }
                return tasks;
            }
        } catch (SQLException | JDBCClientException e) {
            throw new IOException(e);
        }
    }

    private ProfileTaskLog parseLog(ResultSet data) throws SQLException {
        return ProfileTaskLog.builder()
                .id(data.getString("id"))
                .taskId(data.getString(ProfileTaskLogRecord.TASK_ID))
                .instanceId(data.getInt(ProfileTaskLogRecord.INSTANCE_ID))
                .operationType(ProfileTaskLogOperationType.parse(data.getInt(ProfileTaskLogRecord.OPERATION_TYPE)))
                .operationTime(data.getLong(ProfileTaskLogRecord.OPERATION_TIME)).build();
    }
}
