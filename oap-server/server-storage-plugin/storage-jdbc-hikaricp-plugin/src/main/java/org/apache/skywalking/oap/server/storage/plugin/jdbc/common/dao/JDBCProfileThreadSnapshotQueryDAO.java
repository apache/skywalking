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

import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class JDBCProfileThreadSnapshotQueryDAO implements IProfileThreadSnapshotQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<BasicTrace> queryProfiledSegments(String taskId) {
        final var tables = tableHelper.getTablesWithinTTL(ProfileThreadSnapshotRecord.INDEX_NAME);
        final var results = new ArrayList<BasicTrace>();
        final var segments = new ArrayList<>();

        for (String table : tables) {
            segments.addAll(querySegments(taskId, table));
        }

        if (segments.isEmpty()) {
            return Collections.emptyList();
        }

        final var segmentTables = tableHelper.getTablesWithinTTL(SegmentRecord.INDEX_NAME);
        for (String table : segmentTables) {
            final var sql = new StringBuilder();
            final var parameters = new ArrayList<>();

            sql.append("select * from ").append(table).append(" where ")
                .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? and ");
            parameters.add(SegmentRecord.INDEX_NAME);

            final var segmentQuery =
                segments
                    .stream()
                    .map(it -> SegmentRecord.SEGMENT_ID + " = ? ")
                    .collect(joining(" or ", "(", ")"));
            sql.append(segmentQuery);
            parameters.addAll(segments);
            sql.append(" order by ").append(SegmentRecord.START_TIME).append(" ").append("desc");

            final var sqlAndParameters = new SQLAndParameters(sql.toString(), parameters);

            jdbcClient.executeQuery(
                sqlAndParameters.sql(),
                resultSet -> {
                    while (resultSet.next()) {
                        BasicTrace basicTrace = new BasicTrace();

                        basicTrace.setSegmentId(resultSet.getString(SegmentRecord.SEGMENT_ID));
                        basicTrace.setStart(resultSet.getString(SegmentRecord.START_TIME));
                        basicTrace.getEndpointNames().add(
                            IDManager.EndpointID.analysisId(
                                resultSet.getString(SegmentRecord.ENDPOINT_ID)).getEndpointName()
                        );
                        basicTrace.setDuration(resultSet.getInt(SegmentRecord.LATENCY));
                        basicTrace.setError(BooleanUtils.valueToBoolean(resultSet.getInt(SegmentRecord.IS_ERROR)));
                        String traceIds = resultSet.getString(SegmentRecord.TRACE_ID);
                        basicTrace.getTraceIds().add(traceIds);

                        results.add(basicTrace);
                    }
                    return null;
                },
                sqlAndParameters.parameters());
        }
        return results
            .stream()
            .sorted(Comparator.<BasicTrace, Long>comparing(it -> Long.parseLong(it.getStart())).reversed())
            .collect(toList());
    }

    protected ArrayList<String> querySegments(String taskId, String table) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("select ")
           .append(ProfileThreadSnapshotRecord.SEGMENT_ID)
           .append(" from ")
           .append(table);

        sql.append(" where ")
           .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?")
           .append(" and ")
           .append(ProfileThreadSnapshotRecord.TASK_ID)
           .append(" = ? and ")
           .append(ProfileThreadSnapshotRecord.SEQUENCE)
           .append(" = 0");

        return jdbcClient.executeQuery(sql.toString(), resultSet -> {
            final var segments = new ArrayList<String>();
            while (resultSet.next()) {
                segments.add(resultSet.getString(ProfileThreadSnapshotRecord.SEGMENT_ID));
            }
            return segments;
        }, ProfileThreadSnapshotRecord.INDEX_NAME, taskId);
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg("min", segmentId, start, end);
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg("max", segmentId, start, end);
    }

    @Override
    @SneakyThrows
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId,
                                                          int minSequence,
                                                          int maxSequence) throws IOException {
        final var tables = tableHelper.getTablesWithinTTL(ProfileThreadSnapshotRecord.INDEX_NAME);
        final var results = new ArrayList<ProfileThreadSnapshotRecord>();

        for (String table : tables) {
            StringBuilder sql = new StringBuilder();
            sql.append("select * from ").append(table).append(" where ");
            sql.append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
            sql.append(" and ").append(ProfileThreadSnapshotRecord.SEGMENT_ID).append(" = ? ");
            sql.append(" and ").append(ProfileThreadSnapshotRecord.SEQUENCE).append(" >= ? ");
            sql.append(" and ").append(ProfileThreadSnapshotRecord.SEQUENCE).append(" < ? ");

            Object[] params = new Object[]{
                ProfileThreadSnapshotRecord.INDEX_NAME,
                segmentId,
                minSequence,
                maxSequence
            };

            jdbcClient.executeQuery(sql.toString(), resultSet -> {
                while (resultSet.next()) {
                    ProfileThreadSnapshotRecord record = new ProfileThreadSnapshotRecord();

                    record.setTaskId(resultSet.getString(ProfileThreadSnapshotRecord.TASK_ID));
                    record.setSegmentId(resultSet.getString(ProfileThreadSnapshotRecord.SEGMENT_ID));
                    record.setDumpTime(resultSet.getLong(ProfileThreadSnapshotRecord.DUMP_TIME));
                    record.setSequence(resultSet.getInt(ProfileThreadSnapshotRecord.SEQUENCE));
                    String dataBinaryBase64 = resultSet.getString(ProfileThreadSnapshotRecord.STACK_BINARY);
                    if (StringUtil.isNotEmpty(dataBinaryBase64)) {
                        record.setStackBinary(Base64.getDecoder().decode(dataBinaryBase64));
                    }

                    results.add(record);
                }
                return null;
            }, params);
        }

        return results;
    }

    @Override
    @SneakyThrows
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        final var tables = tableHelper.getTablesWithinTTL(SegmentRecord.INDEX_NAME);
        for (final var table : tables) {
            final var r = jdbcClient.executeQuery(
                "select * from " + table +
                    " where " + JDBCTableInstaller.TABLE_COLUMN + " = ?" +
                    " and " + SegmentRecord.SEGMENT_ID + " = ?",
                resultSet -> {
                    if (resultSet.next()) {
                        SegmentRecord segmentRecord = new SegmentRecord();
                        segmentRecord.setSegmentId(resultSet.getString(SegmentRecord.SEGMENT_ID));
                        segmentRecord.setTraceId(resultSet.getString(SegmentRecord.TRACE_ID));
                        segmentRecord.setServiceId(resultSet.getString(SegmentRecord.SERVICE_ID));
                        segmentRecord.setServiceInstanceId(resultSet.getString(SegmentRecord.SERVICE_INSTANCE_ID));
                        segmentRecord.setStartTime(resultSet.getLong(SegmentRecord.START_TIME));
                        segmentRecord.setLatency(resultSet.getInt(SegmentRecord.LATENCY));
                        segmentRecord.setIsError(resultSet.getInt(SegmentRecord.IS_ERROR));
                        String dataBinaryBase64 = resultSet.getString(SegmentRecord.DATA_BINARY);
                        if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
                            segmentRecord.setDataBinary(Base64.getDecoder().decode(dataBinaryBase64));
                        }
                        return segmentRecord;
                    }
                    return null;
                },
                SegmentRecord.INDEX_NAME, segmentId
            );
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    @SneakyThrows
    private int querySequenceWithAgg(String aggType, String segmentId, long start, long end) throws IOException {
        final var tables = tableHelper.getTablesWithinTTL(ProfileThreadSnapshotRecord.INDEX_NAME);

        var result = IntStream.builder();

        for (String table : tables) {
            StringBuilder sql = new StringBuilder();
            sql.append("select ")
               .append(aggType)
               .append("(")
               .append(ProfileThreadSnapshotRecord.SEQUENCE)
               .append(") from ")
               .append(table)
               .append(" where ");
            sql.append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            sql.append(" and ").append(ProfileThreadSnapshotRecord.SEGMENT_ID).append(" = ? ");
            sql.append(" and ").append(ProfileThreadSnapshotRecord.DUMP_TIME).append(" >= ? ");
            sql.append(" and ").append(ProfileThreadSnapshotRecord.DUMP_TIME).append(" <= ? ");

            Object[] params = new Object[]{
                ProfileThreadSnapshotRecord.INDEX_NAME,
                segmentId,
                start,
                end
            };

            jdbcClient.executeQuery(sql.toString(), resultSet -> {
                if (resultSet.next()) {
                    result.add(resultSet.getInt(1));
                }
                return null;
            }, params);
        }
        switch (aggType) {
            case "min":
                return result.build().min().orElse(-1);
            case "max":
                return result.build().max().orElse(-1);
            default:
                throw new UnsupportedOperationException("Unsupported agg type: " + aggType);
        }
    }
}
