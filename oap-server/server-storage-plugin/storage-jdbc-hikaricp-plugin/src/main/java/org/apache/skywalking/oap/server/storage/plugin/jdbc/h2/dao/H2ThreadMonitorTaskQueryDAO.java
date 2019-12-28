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

import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.profile.ThreadMonitorTaskNoneStream;
import org.apache.skywalking.oap.server.core.query.entity.ThreadMonitorTask;
import org.apache.skywalking.oap.server.core.storage.profile.IThreadMonitorTaskQueryDAO;
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
public class H2ThreadMonitorTaskQueryDAO implements IThreadMonitorTaskQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2ThreadMonitorTaskQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public List<ThreadMonitorTask> getTaskListSearchOnStartTime(int serviceId, long taskStartTime, long taskEndTime) throws IOException {
        final StringBuilder sql = new StringBuilder();
        sql.append("select * from ").append(ThreadMonitorTaskNoneStream.INDEX_NAME).append(" where ");
        sql.append(ThreadMonitorTaskNoneStream.SERVICE_ID).append("=? and ")
                .append(ThreadMonitorTaskNoneStream.MONITOR_START_TIME).append(">=? and ")
                .append(ThreadMonitorTaskNoneStream.MONITOR_START_TIME).append("<= ?");

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), serviceId, taskStartTime, taskEndTime)) {
                final LinkedList<ThreadMonitorTask> tasks = new LinkedList<>();
                while (resultSet.next()) {
                    tasks.add(parseTask(resultSet));
                }
                return tasks;
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

    }

    @Override
    public List<ThreadMonitorTask> getTaskList(Integer serviceId, String endpointName, long startTimeBucket, long endTimeBucket) throws IOException {
        final StringBuilder sql = new StringBuilder();
        final ArrayList<Object> condition = new ArrayList<>(4);
        sql.append("select * from ").append(ThreadMonitorTaskNoneStream.INDEX_NAME).append(" where ");
        sql.append(" (").append(ThreadMonitorTaskNoneStream.TIME_BUCKET).append(">=? and ").append(ThreadMonitorTaskNoneStream.TIME_BUCKET).append("<=?)");
        condition.add(startTimeBucket);
        condition.add(endTimeBucket);

        if (serviceId != null) {
            sql.append(" and ").append(ThreadMonitorTaskNoneStream.SERVICE_ID).append("=? ");
            condition.add(serviceId);
        }

        if (!StringUtil.isBlank(endpointName)) {
            sql.append(" and ").append(ThreadMonitorTaskNoneStream.ENDPOINT_NAME).append("=?");
            condition.add(endpointName);
        }

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), condition.toArray(new Object[0]))) {
                final LinkedList<ThreadMonitorTask> tasks = new LinkedList<>();
                while (resultSet.next()) {
                    tasks.add(parseTask(resultSet));
                }
                return tasks;
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * parse thread monitor task data
     * @param data
     * @return
     */
    private ThreadMonitorTask parseTask(ResultSet data) throws SQLException {
        return ThreadMonitorTask.builder()
                .id(data.getString("id"))
                .serviceId(data.getInt(ThreadMonitorTaskNoneStream.SERVICE_ID))
                .endpointName(data.getString(ThreadMonitorTaskNoneStream.ENDPOINT_NAME))
                .startTime(data.getLong(ThreadMonitorTaskNoneStream.MONITOR_START_TIME))
                .duration(data.getInt(ThreadMonitorTaskNoneStream.MONITOR_DURATION))
                .minDurationThreshold(data.getInt(ThreadMonitorTaskNoneStream.MIN_DURATION_THRESHOLD))
                .dumpPeriod(data.getInt(ThreadMonitorTaskNoneStream.DUMP_PERIOD)).build();
    }
}
