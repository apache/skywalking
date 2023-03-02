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

import lombok.AllArgsConstructor;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class JDBCEBPFProfilingTaskDAO extends JDBCSQLExecutor implements IEBPFProfilingTaskDAO {
    private JDBCHikariCPClient jdbcClient;

    @Override
    public List<EBPFProfilingTaskRecord> queryTasksByServices(List<String> serviceIdList, EBPFProfilingTriggerType triggerType, long taskStartTime, long latestUpdateTime) throws IOException {
        final StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>();
        sql.append("select * from ").append(EBPFProfilingTaskRecord.INDEX_NAME);

        StringBuilder conditionSql = new StringBuilder();

        appendListCondition(conditionSql, condition, EBPFProfilingTaskRecord.SERVICE_ID, serviceIdList);
        if (taskStartTime > 0) {
            appendCondition(conditionSql, condition,
                EBPFProfilingTaskRecord.START_TIME, ">=", taskStartTime);
        }
        if (latestUpdateTime > 0) {
            appendCondition(conditionSql, condition,
                EBPFProfilingTaskRecord.LAST_UPDATE_TIME, ">", latestUpdateTime);
        }
        if (triggerType != null) {
            appendCondition(conditionSql, condition,
                EBPFProfilingTaskRecord.TRIGGER_TYPE, "=", triggerType.value());
        }

        if (conditionSql.length() > 0) {
            sql.append(" where ").append(conditionSql);
        }

        try (Connection connection = jdbcClient.getConnection()) {
            try (ResultSet resultSet = jdbcClient.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                return buildTasks(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<EBPFProfilingTaskRecord> queryTasksByTargets(String serviceId, String serviceInstanceId, List<EBPFProfilingTargetType> targetTypes,
                                                             EBPFProfilingTriggerType triggerType, long taskStartTime, long latestUpdateTime) throws IOException {
        final StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>();
        sql.append("select * from ").append(EBPFProfilingTaskRecord.INDEX_NAME);

        StringBuilder conditionSql = new StringBuilder();

        if (StringUtil.isNotEmpty(serviceId)) {
            appendCondition(conditionSql, condition, EBPFProfilingTaskRecord.SERVICE_ID, serviceId);
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            appendCondition(conditionSql, condition, EBPFProfilingTaskRecord.INSTANCE_ID, serviceInstanceId);
        }
        appendListCondition(conditionSql, condition, EBPFProfilingTaskRecord.TARGET_TYPE, targetTypes.stream()
            .map(EBPFProfilingTargetType::value).collect(Collectors.toList()));
        if (taskStartTime > 0) {
            appendCondition(conditionSql, condition,
                EBPFProfilingTaskRecord.START_TIME, ">=", taskStartTime);
        }
        if (latestUpdateTime > 0) {
            appendCondition(conditionSql, condition,
                EBPFProfilingTaskRecord.LAST_UPDATE_TIME, ">", latestUpdateTime);
        }
        if (triggerType != null) {
            appendCondition(conditionSql, condition, EBPFProfilingTaskRecord.TRIGGER_TYPE, "=", triggerType.value());
        }

        if (conditionSql.length() > 0) {
            sql.append(" where ").append(conditionSql);
        }

        try (Connection connection = jdbcClient.getConnection()) {
            try (ResultSet resultSet = jdbcClient.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                return buildTasks(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<EBPFProfilingTaskRecord> getTaskRecord(String id) throws IOException {
        final StringBuilder sql = new StringBuilder();
        sql.append("select * from ").append(EBPFProfilingTaskRecord.INDEX_NAME)
            .append(" where ").append(EBPFProfilingTaskRecord.LOGICAL_ID).append("=?");

        try (Connection connection = jdbcClient.getConnection()) {
            try (ResultSet resultSet = jdbcClient.executeQuery(connection, sql.toString(), id)) {
                return buildTasks(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private List<EBPFProfilingTaskRecord> buildTasks(ResultSet resultSet) throws SQLException {
        List<EBPFProfilingTaskRecord> tasks = new ArrayList<>();
        StorageData data;
        while ((data = toStorageData(resultSet, EBPFProfilingTaskRecord.INDEX_NAME, new EBPFProfilingTaskRecord.Builder())) != null) {
            tasks.add((EBPFProfilingTaskRecord) data);
        }
        return tasks;
    }

    private void appendCondition(StringBuilder conditionSql, List<Object> condition, String filed, Object data) {
        appendCondition(conditionSql, condition, filed, "=", data);
    }

    private void appendCondition(StringBuilder conditionSql, List<Object> condition, String filed, String compare, Object data) {
        if (conditionSql.length() > 0) {
            conditionSql.append(" and ");
        }
        conditionSql.append(filed).append(compare).append("?");
        condition.add(data);
    }

    private <T> void appendListCondition(StringBuilder conditionSql, List<Object> condition, String filed, List<T> data) {
        if (conditionSql.length() > 0) {
            conditionSql.append(" and ");
        }
        conditionSql.append(filed).append(" in (");
        for (int i = 0; i < data.size(); i++) {
            if (i > 0) {
                conditionSql.append(",");
            }
            conditionSql.append("?");
        }
        conditionSql.append(")");
        condition.addAll(data);
    }
}
