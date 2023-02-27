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
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static java.util.stream.Collectors.joining;

@RequiredArgsConstructor
public class JDBCEBPFProfilingDataDAO implements IEBPFProfilingDataDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<EBPFProfilingDataRecord> queryData(List<String> scheduleIdList, long beginTime, long endTime) {
        final var tables = tableHelper.getTablesForRead(
            EBPFProfilingDataRecord.INDEX_NAME,
            TimeBucket.getTimeBucket(beginTime, DownSampling.Day),
            TimeBucket.getTimeBucket(endTime, DownSampling.Day)
        );
        final var results = new ArrayList<EBPFProfilingDataRecord>();

        for (final var table : tables) {
            final var sqlAndParameters = buildSQL(scheduleIdList, beginTime, endTime, table);
            results.addAll(
                jdbcClient.executeQuery(
                    sqlAndParameters.sql(),
                    this::buildDataList,
                    sqlAndParameters.parameters()
                )
            );
        }
        return results;
    }

    protected SQLAndParameters buildSQL(
        final List<String> scheduleIdList,
        final long beginTime,
        final long endTime,
        final String table) {
        final var sql = new StringBuilder();
        final var conditions = new StringBuilder();
        final var parameters = new ArrayList<>(scheduleIdList.size() + 2);
        sql.append("select * from ").append(table);

        appendConditions(conditions, parameters, EBPFProfilingDataRecord.SCHEDULE_ID, scheduleIdList);
        appendCondition(conditions, parameters, EBPFProfilingDataRecord.UPLOAD_TIME, ">=", beginTime);
        appendCondition(conditions, parameters, EBPFProfilingDataRecord.UPLOAD_TIME, "<", endTime);

        if (conditions.length() > 0) {
            sql.append(" where ").append(conditions);
        }
        return new SQLAndParameters(sql.toString(), parameters);
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

    private void appendCondition(StringBuilder conditionSql, List<Object> condition, String field, String compare, Object data) {
        if (conditionSql.length() > 0) {
            conditionSql.append(" and ");
        }
        conditionSql.append(field).append(compare).append("?");
        condition.add(data);
    }

    private <T> void appendConditions(StringBuilder conditionSql, List<Object> condition, String field, List<T> data) {
        if (conditionSql.length() > 0) {
            conditionSql.append(" and ");
        }
        conditionSql.append(field).append(" in ")
                    .append(data.stream().map(it -> "?").collect(joining(", ", "(", ")")));
        condition.addAll(data);
    }
}
