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

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

public class H2ProfileTaskQueryDAO implements IProfileTaskQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2ProfileTaskQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public List<ProfileTask> getTaskList(String serviceId, String endpointName, Long startTimeBucket,
                                         Long endTimeBucket, Integer limit) throws IOException {
        final StringBuilder sql = new StringBuilder();
        final ArrayList<Object> condition = new ArrayList<>(4);
        sql.append("select * from ").append(ProfileTaskRecord.INDEX_NAME).append(" where 1=1 ");

        if (startTimeBucket != null) {
            sql.append(" and ").append(ProfileTaskRecord.TIME_BUCKET).append(" >= ? ");
            condition.add(startTimeBucket);
        }

        if (endTimeBucket != null) {
            sql.append(" and ").append(ProfileTaskRecord.TIME_BUCKET).append(" <= ? ");
            condition.add(endTimeBucket);
        }

        if (StringUtil.isNotEmpty(serviceId)) {
            sql.append(" and ").append(ProfileTaskRecord.SERVICE_ID).append("=? ");
            condition.add(serviceId);
        }

        if (StringUtil.isNotEmpty(endpointName)) {
            sql.append(" and ").append(ProfileTaskRecord.ENDPOINT_NAME).append("=?");
            condition.add(endpointName);
        }

        sql.append(" ORDER BY ").append(ProfileTaskRecord.START_TIME).append(" DESC ");

        if (limit != null) {
            sql.append(" LIMIT ").append(limit);
        }

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                final LinkedList<ProfileTask> tasks = new LinkedList<>();
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
    public ProfileTask getById(String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }

        final StringBuilder sql = new StringBuilder();
        final ArrayList<Object> condition = new ArrayList<>(1);
        sql.append("select * from ").append(ProfileTaskRecord.INDEX_NAME).append(" where id=? LIMIT 1");
        condition.add(id);

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                if (resultSet.next()) {
                    return parseTask(resultSet);
                }
            }
        } catch (SQLException | JDBCClientException e) {
            throw new IOException(e);
        }
        return null;
    }

    /**
     * parse profile task data
     */
    private ProfileTask parseTask(ResultSet data) throws SQLException {
        return ProfileTask.builder()
                          .id(data.getString("id"))
                          .serviceId(data.getString(ProfileTaskRecord.SERVICE_ID))
                          .endpointName(data.getString(ProfileTaskRecord.ENDPOINT_NAME))
                          .startTime(data.getLong(ProfileTaskRecord.START_TIME))
                          .createTime(data.getLong(ProfileTaskRecord.CREATE_TIME))
                          .duration(data.getInt(ProfileTaskRecord.DURATION))
                          .minDurationThreshold(data.getInt(ProfileTaskRecord.MIN_DURATION_THRESHOLD))
                          .dumpPeriod(data.getInt(ProfileTaskRecord.DUMP_PERIOD))
                          .maxSamplingCount(data.getInt(ProfileTaskRecord.MAX_SAMPLING_COUNT))
                          .build();
    }
}
