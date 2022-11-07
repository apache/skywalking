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
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@AllArgsConstructor
public class JDBCEBPFProfilingDataDAO implements IEBPFProfilingDataDAO {
    private JDBCHikariCPClient jdbcClient;

    @Override
    public List<EBPFProfilingDataRecord> queryData(List<String> scheduleIdList, long beginTime, long endTime) throws IOException {
        final StringBuilder sql = new StringBuilder();
        final StringBuilder conditionSql = new StringBuilder();
        List<Object> condition = new ArrayList<>(scheduleIdList.size() + 2);
        sql.append("select * from ").append(EBPFProfilingDataRecord.INDEX_NAME);

        appendListCondition(conditionSql, condition, EBPFProfilingDataRecord.SCHEDULE_ID, scheduleIdList);
        appendCondition(conditionSql, condition, EBPFProfilingDataRecord.UPLOAD_TIME, ">=", beginTime);
        appendCondition(conditionSql, condition, EBPFProfilingDataRecord.UPLOAD_TIME, "<", endTime);

        if (conditionSql.length() > 0) {
            sql.append(" where ").append(conditionSql);
        }

        try (Connection connection = jdbcClient.getConnection()) {
            try (ResultSet resultSet = jdbcClient.executeQuery(
                    connection, sql.toString(), condition.toArray(new Object[0]))) {
                return buildDataList(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private List<EBPFProfilingDataRecord> buildDataList(ResultSet resultSet) throws SQLException {
        List<EBPFProfilingDataRecord> dataList = new ArrayList<>();
        while (resultSet.next()) {
            EBPFProfilingDataRecord data = new EBPFProfilingDataRecord();
            data.setScheduleId(resultSet.getString(EBPFProfilingDataRecord.SCHEDULE_ID));
            data.setTaskId(resultSet.getString(EBPFProfilingDataRecord.TASK_ID));
            data.setStackIdList(resultSet.getString(EBPFProfilingDataRecord.STACK_ID_LIST));
            String dataBinaryBase64 = resultSet.getString(EBPFProfilingDataRecord.DATA_BINARY);
            if (StringUtil.isNotEmpty(dataBinaryBase64)) {
                data.setDataBinary(Base64.getDecoder().decode(dataBinaryBase64));
            }
            data.setTargetType(resultSet.getInt(EBPFProfilingDataRecord.TARGET_TYPE));
            data.setUploadTime(resultSet.getLong(EBPFProfilingDataRecord.UPLOAD_TIME));

            dataList.add(data);
        }
        return dataList;
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
                conditionSql.append(", ");
            }
            conditionSql.append("?");
            condition.add(data.get(i));
        }
        conditionSql.append(")");
    }
}
