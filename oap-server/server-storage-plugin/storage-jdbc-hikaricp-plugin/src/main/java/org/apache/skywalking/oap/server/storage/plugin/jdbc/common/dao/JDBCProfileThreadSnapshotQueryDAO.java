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
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class JDBCProfileThreadSnapshotQueryDAO implements IProfileThreadSnapshotQueryDAO {
    private final JDBCClient jdbcClient;

    @Override
    @SneakyThrows
    public List<BasicTrace> queryProfiledSegments(String taskId) {
        // search segment id list
        StringBuilder sql = new StringBuilder();
        sql.append("select ")
           .append(ProfileThreadSnapshotRecord.SEGMENT_ID)
           .append(" from ")
           .append(ProfileThreadSnapshotRecord.INDEX_NAME);

        sql.append(" where ")
           .append(ProfileThreadSnapshotRecord.TASK_ID)
           .append(" = ? and ")
           .append(ProfileThreadSnapshotRecord.SEQUENCE)
           .append(" = 0");

        final var segments = jdbcClient.executeQuery(sql.toString(), resultSet -> {
            final var results = new ArrayList<String>();
            while (resultSet.next()) {
                results.add(resultSet.getString(ProfileThreadSnapshotRecord.SEGMENT_ID));
            }
            return results;
        }, taskId);

        if (CollectionUtils.isEmpty(segments)) {
            return Collections.emptyList();
        }

        // search traces
        sql = new StringBuilder();
        sql.append("select * from ").append(SegmentRecord.INDEX_NAME).append(" where ");
        for (int i = 0; i < segments.size(); i++) {
            sql.append(i > 0 ? " or " : "").append(SegmentRecord.SEGMENT_ID).append(" = ? ");
        }
        sql.append(" order by ").append(SegmentRecord.START_TIME).append(" ").append("desc");


        return jdbcClient.executeQuery(
            sql.toString(),
            resultSet -> {
                ArrayList<BasicTrace> result = new ArrayList<>(segments.size());
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

                    result.add(basicTrace);
                }
                return result;
            },
            segments.toArray(new String[segments.size()]));
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
        StringBuilder sql = new StringBuilder();
        sql.append("select * from ").append(ProfileThreadSnapshotRecord.INDEX_NAME).append(" where ");
        sql.append(" 1=1 ");
        sql.append(" and ").append(ProfileThreadSnapshotRecord.SEGMENT_ID).append(" = ? ");
        sql.append(" and ").append(ProfileThreadSnapshotRecord.SEQUENCE).append(" >= ? ");
        sql.append(" and ").append(ProfileThreadSnapshotRecord.SEQUENCE).append(" < ? ");

        Object[] params = new Object[] {
            segmentId,
            minSequence,
            maxSequence
        };


        return jdbcClient.executeQuery(sql.toString(), resultSet -> {
            ArrayList<ProfileThreadSnapshotRecord> result = new ArrayList<>(maxSequence - minSequence);
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

                result.add(record);
            }
            return result;
        }, params);
    }

    @Override
    @SneakyThrows
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        return jdbcClient.executeQuery(
            "select * from " + SegmentRecord.INDEX_NAME + " where " + SegmentRecord.SEGMENT_ID + " = ?",
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
            segmentId
        );
    }

    @SneakyThrows
    private int querySequenceWithAgg(String aggType, String segmentId, long start, long end) throws IOException {
        StringBuilder sql = new StringBuilder();
        sql.append("select ")
           .append(aggType)
           .append("(")
           .append(ProfileThreadSnapshotRecord.SEQUENCE)
           .append(") from ")
           .append(ProfileThreadSnapshotRecord.INDEX_NAME)
           .append(" where ");
        sql.append(" 1=1 ");
        sql.append(" and ").append(ProfileThreadSnapshotRecord.SEGMENT_ID).append(" = ? ");
        sql.append(" and ").append(ProfileThreadSnapshotRecord.DUMP_TIME).append(" >= ? ");
        sql.append(" and ").append(ProfileThreadSnapshotRecord.DUMP_TIME).append(" <= ? ");

        Object[] params = new Object[] {
            segmentId,
            start,
            end
        };

        return jdbcClient.executeQuery(sql.toString(), resultSet -> {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
            return -1;
        } ,params);
    }

}
