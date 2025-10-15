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
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

@RequiredArgsConstructor
public class JDBCProfileThreadSnapshotQueryDAO implements IProfileThreadSnapshotQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @SneakyThrows
    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String taskId)  throws IOException {
        final var snapshotTables = tableHelper.getTablesWithinTTL(ProfileThreadSnapshotRecord.INDEX_NAME);
        final var segments = new ArrayList<ProfileThreadSnapshotRecord>();
        for (String table : snapshotTables) {
            segments.addAll(querySegments(taskId, table));
        }
        return segments;
    }

    protected ArrayList<ProfileThreadSnapshotRecord> querySegments(String taskId, String table) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("select ")
           .append("*")
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
            final var records = new ArrayList<ProfileThreadSnapshotRecord>();
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
                records.add(record);
            }
            return records;
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
