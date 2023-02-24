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

import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JDBCProfileTaskQueryDAO implements IProfileTaskQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    public JDBCProfileTaskQueryDAO(JDBCClient jdbcClient, ModuleManager moduleManager) {
        this.jdbcClient = jdbcClient;
        this.tableHelper = new TableHelper(moduleManager, jdbcClient);
    }

    @Override
    @SneakyThrows
    public List<ProfileTask> getTaskList(String serviceId, String endpointName, Long startTimeBucket,
                                         Long endTimeBucket, Integer limit) {
        final var results = new ArrayList<ProfileTask>();
        final var tables = tableHelper.getTablesForRead(ProfileTaskRecord.INDEX_NAME);
        for (final var table : tables) {
            final var condition = new ArrayList<>(4);
            final var sql = new StringBuilder()
                .append("select * from ").append(table)
                .append(" where 1 = 1");

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

            results.addAll(
                jdbcClient.executeQuery(
                    sql.toString(),
                    resultSet -> {
                        final var tasks = new ArrayList<ProfileTask>();
                        while (resultSet.next()) {
                            tasks.add(parseTask(resultSet));
                        }
                        return tasks;
                    },
                    condition.toArray(new Object[0]))
            );
        }
        return results;
    }

    @Override
    @SneakyThrows
    public ProfileTask getById(String id) {
        if (StringUtil.isEmpty(id)) {
            return null;
        }

        final StringBuilder sql = new StringBuilder();
        final ArrayList<Object> condition = new ArrayList<>(1);
        sql.append("select * from ").append(ProfileTaskRecord.INDEX_NAME)
            .append(" where " + ProfileTaskRecord.TASK_ID + "=? LIMIT 1");
        condition.add(id);

        return jdbcClient.executeQuery(
            sql.toString(),
            resultSet -> {
                if (resultSet.next()) {
                    return parseTask(resultSet);
                }
                return null;
            },
            condition.toArray(new Object[0]));
    }

    /**
     * parse profile task data
     */
    private ProfileTask parseTask(ResultSet data) throws SQLException {
        return ProfileTask.builder()
                          .id(data.getString(ProfileTaskRecord.TASK_ID))
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
