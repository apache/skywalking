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

import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SWSpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class JDBCSpanAttachedEventQueryDAO implements ISpanAttachedEventQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<SpanAttachedEventRecord> queryZKSpanAttachedEvents(List<String> traceIds, @Nullable Duration duration) {
        final var tables = tableHelper.getTablesWithinTTL(SpanAttachedEventRecord.INDEX_NAME);
        final var results = new ArrayList<SpanAttachedEventRecord>();

        for (String table : tables) {
            final var sqlAndParameters = buildZKSQL(traceIds, table);

            jdbcClient.executeQuery(
                sqlAndParameters.sql(),
                resultSet -> {
                    while (resultSet.next()) {
                        SpanAttachedEventRecord record = new SpanAttachedEventRecord();
                        record.setStartTimeSecond(resultSet.getLong(SpanAttachedEventRecord.START_TIME_SECOND));
                        record.setStartTimeNanos(resultSet.getInt(SpanAttachedEventRecord.START_TIME_NANOS));
                        record.setEvent(resultSet.getString(SpanAttachedEventRecord.EVENT));
                        record.setEndTimeSecond(resultSet.getLong(SpanAttachedEventRecord.END_TIME_SECOND));
                        record.setEndTimeNanos(resultSet.getInt(SpanAttachedEventRecord.END_TIME_NANOS));
                        record.setTraceRefType(resultSet.getInt(SpanAttachedEventRecord.TRACE_REF_TYPE));
                        record.setRelatedTraceId(resultSet.getString(SpanAttachedEventRecord.RELATED_TRACE_ID));
                        record.setTraceSegmentId(resultSet.getString(SpanAttachedEventRecord.TRACE_SEGMENT_ID));
                        record.setTraceSpanId(resultSet.getString(SpanAttachedEventRecord.TRACE_SPAN_ID));
                        String dataBinaryBase64 = resultSet.getString(SpanAttachedEventRecord.DATA_BINARY);
                        if (StringUtil.isNotEmpty(dataBinaryBase64)) {
                            record.setDataBinary(Base64.getDecoder().decode(dataBinaryBase64));
                        }
                        results.add(record);
                    }

                    return null;
                },
                sqlAndParameters.parameters());
        }
        return results
            .stream()
            .sorted(comparing(SpanAttachedEventRecord::getStartTimeSecond).thenComparing(SpanAttachedEventRecord::getStartTimeNanos))
            .collect(toList());
    }

    @Override
    @SneakyThrows
    public List<SWSpanAttachedEventRecord> querySWSpanAttachedEvents(List<String> traceIds, @Nullable Duration duration) {
        final var tables = tableHelper.getTablesWithinTTL(SWSpanAttachedEventRecord.INDEX_NAME);
        final var results = new ArrayList<SWSpanAttachedEventRecord>();

        for (String table : tables) {
            final var sqlAndParameters = buildSWSQL(traceIds, table);

            jdbcClient.executeQuery(
                    sqlAndParameters.sql(),
                    resultSet -> {
                        while (resultSet.next()) {
                            SWSpanAttachedEventRecord record = new SWSpanAttachedEventRecord();
                            record.setStartTimeSecond(resultSet.getLong(SWSpanAttachedEventRecord.START_TIME_SECOND));
                            record.setStartTimeNanos(resultSet.getInt(SWSpanAttachedEventRecord.START_TIME_NANOS));
                            record.setEvent(resultSet.getString(SWSpanAttachedEventRecord.EVENT));
                            record.setEndTimeSecond(resultSet.getLong(SWSpanAttachedEventRecord.END_TIME_SECOND));
                            record.setEndTimeNanos(resultSet.getInt(SWSpanAttachedEventRecord.END_TIME_NANOS));
                            record.setTraceRefType(resultSet.getInt(SWSpanAttachedEventRecord.TRACE_REF_TYPE));
                            record.setRelatedTraceId(resultSet.getString(SWSpanAttachedEventRecord.RELATED_TRACE_ID));
                            record.setTraceSegmentId(resultSet.getString(SWSpanAttachedEventRecord.TRACE_SEGMENT_ID));
                            record.setTraceSpanId(resultSet.getString(SWSpanAttachedEventRecord.TRACE_SPAN_ID));
                            String dataBinaryBase64 = resultSet.getString(SWSpanAttachedEventRecord.DATA_BINARY);
                            if (StringUtil.isNotEmpty(dataBinaryBase64)) {
                                record.setDataBinary(Base64.getDecoder().decode(dataBinaryBase64));
                            }
                            results.add(record);
                        }

                        return null;
                    },
                    sqlAndParameters.parameters());
        }
        return results
                .stream()
                .sorted(comparing(SWSpanAttachedEventRecord::getStartTimeSecond).thenComparing(SWSpanAttachedEventRecord::getStartTimeNanos))
                .collect(toList());
    }

    private static SQLAndParameters buildZKSQL(List<String> traceIds, String table) {
        final var sql = new StringBuilder("select * from " + table + " where ");
        final var parameters = new ArrayList<>(traceIds.size() + 1);

        sql.append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
        parameters.add(SpanAttachedEventRecord.INDEX_NAME);

        sql.append(" and ").append(SpanAttachedEventRecord.RELATED_TRACE_ID).append(" in ");
        sql.append(
            traceIds
                .stream()
                .map(it -> "?")
                .collect(joining(",", "(", ")"))
        );
        parameters.addAll(traceIds);

        sql.append(" order by ").append(SpanAttachedEventRecord.START_TIME_SECOND)
           .append(",").append(SpanAttachedEventRecord.START_TIME_NANOS).append(" ASC ");

        return new SQLAndParameters(sql.toString(), parameters);
    }

    private static SQLAndParameters buildSWSQL(List<String> traceIds, String table) {
        final var sql = new StringBuilder("select * from " + table + " where ");
        final var parameters = new ArrayList<>(traceIds.size() + 1);

        sql.append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
        parameters.add(SWSpanAttachedEventRecord.INDEX_NAME);

        sql.append(" and ").append(SWSpanAttachedEventRecord.RELATED_TRACE_ID).append(" in ");
        sql.append(
                traceIds
                        .stream()
                        .map(it -> "?")
                        .collect(joining(",", "(", ")"))
        );
        parameters.addAll(traceIds);

        sql.append(" order by ").append(SWSpanAttachedEventRecord.START_TIME_SECOND)
                .append(",").append(SWSpanAttachedEventRecord.START_TIME_NANOS).append(" ASC ");

        return new SQLAndParameters(sql.toString(), parameters);
    }
}
