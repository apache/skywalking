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
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingProcessFinderType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.EBPFProfilingProcessFinder;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class H2EBPFProfilingTaskDAO implements IEBPFProfilingTaskDAO {
    private JDBCHikariCPClient h2Client;

    @Override
    public List<EBPFProfilingTask> queryTasks(EBPFProfilingProcessFinder finder, EBPFProfilingTargetType targetType, long taskStartTime, long latestUpdateTime) throws IOException {
        final StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(7);
        sql.append("select * from ").append(EBPFProfilingTaskRecord.INDEX_NAME);

        StringBuilder conditionSql = new StringBuilder();

        if (finder.getFinderType() != null) {
            appendCondition(conditionSql, condition,
                    EBPFProfilingTaskRecord.PROCESS_FIND_TYPE, finder.getFinderType().value());
        }
        if (StringUtil.isNotEmpty(finder.getServiceId())) {
            appendCondition(conditionSql, condition,
                    EBPFProfilingTaskRecord.SERVICE_ID, finder.getServiceId());
        }
        if (StringUtil.isNotEmpty(finder.getInstanceId())) {
            appendCondition(conditionSql, condition,
                    EBPFProfilingTaskRecord.INSTANCE_ID, finder.getInstanceId());
        }
        if (CollectionUtils.isNotEmpty(finder.getProcessIdList())) {
            appendListCondition(conditionSql, condition,
                    EBPFProfilingTaskRecord.PROCESS_ID, finder.getProcessIdList());
        }
        if (targetType != null) {
            appendCondition(conditionSql, condition,
                    EBPFProfilingTaskRecord.TARGET_TYPE, targetType.value());
        }
        if (taskStartTime > 0) {
            appendCondition(conditionSql, condition,
                    EBPFProfilingTaskRecord.START_TIME, ">=", taskStartTime);
        }
        if (latestUpdateTime > 0) {
            appendCondition(conditionSql, condition,
                    EBPFProfilingTaskRecord.LAST_UPDATE_TIME, ">", latestUpdateTime);
        }

        if (conditionSql.length() > 0) {
            sql.append(" where ").append(conditionSql);
        }

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                    connection, sql.toString(), condition.toArray(new Object[0]))) {
                return buildTasks(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private List<EBPFProfilingTask> buildTasks(ResultSet resultSet) throws SQLException {
        List<EBPFProfilingTask> tasks = new ArrayList<>();
        while (resultSet.next()) {
            EBPFProfilingTask task = new EBPFProfilingTask();
            task.setTaskId(resultSet.getString(H2TableInstaller.ID_COLUMN));
            task.setProcessFinderType(EBPFProfilingProcessFinderType.valueOf(
                    resultSet.getInt(EBPFProfilingTaskRecord.PROCESS_FIND_TYPE)));
            final String serviceId = resultSet.getString(EBPFProfilingTaskRecord.SERVICE_ID);
            task.setServiceId(serviceId);
            task.setServiceName(IDManager.ServiceID.analysisId(serviceId).getName());
            final String instanceId = resultSet.getString(EBPFProfilingTaskRecord.INSTANCE_ID);
            task.setInstanceId(instanceId);
            task.setInstanceName(IDManager.ServiceInstanceID.analysisId(instanceId).getName());
            task.setProcessId(resultSet.getString(EBPFProfilingTaskRecord.PROCESS_ID));
            task.setProcessName(resultSet.getString(EBPFProfilingTaskRecord.PROCESS_NAME));
            task.setTaskStartTime(resultSet.getLong(EBPFProfilingTaskRecord.START_TIME));
            task.setTriggerType(EBPFProfilingTriggerType.valueOf(
                    resultSet.getInt(EBPFProfilingTaskRecord.TRIGGER_TYPE)));
            task.setFixedTriggerDuration(resultSet.getInt(EBPFProfilingTaskRecord.FIXED_TRIGGER_DURATION));
            task.setTargetType(EBPFProfilingTargetType.valueOf(
                    resultSet.getInt(EBPFProfilingTaskRecord.TARGET_TYPE)));
            task.setCreateTime(resultSet.getLong(EBPFProfilingTaskRecord.CREATE_TIME));
            task.setLastUpdateTime(resultSet.getLong(EBPFProfilingTaskRecord.LAST_UPDATE_TIME));

            tasks.add(task);
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