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

import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

public class H2ProfileThreadSnapshotQueryDAO implements IProfileThreadSnapshotQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2ProfileThreadSnapshotQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public List<String> getProfiledSegmentList(String taskId) throws IOException {
        final StringBuilder sql = new StringBuilder();
        sql.append("select ").append(ProfileThreadSnapshotRecord.SEGMENT_ID).append(" from ").append(ProfileThreadSnapshotRecord.INDEX_NAME);

        sql.append(" where ").append(ProfileThreadSnapshotRecord.TASK_ID).append(" = ? and ").append(ProfileThreadSnapshotRecord.SEQUENCE).append(" = 0");
        sql.append(" ORDER BY ").append(ProfileThreadSnapshotRecord.DUMP_TIME).append(" DESC ");

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), taskId)) {
                final LinkedList<String> tasks = new LinkedList<>();
                while (resultSet.next()) {
                    tasks.add(resultSet.getString(ProfileThreadSnapshotRecord.SEGMENT_ID));
                }
                return tasks;
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
