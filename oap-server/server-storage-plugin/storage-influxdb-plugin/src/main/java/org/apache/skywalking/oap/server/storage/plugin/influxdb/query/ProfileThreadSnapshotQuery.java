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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.elasticsearch.common.Strings;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class ProfileThreadSnapshotQuery implements IProfileThreadSnapshotQueryDAO {
    private final InfluxClient client;

    public ProfileThreadSnapshotQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> countQuery = select(ProfileThreadSnapshotRecord.SEGMENT_ID)
            .from(client.getDatabase(), ProfileThreadSnapshotRecord.INDEX_NAME)
            .where();

        countQuery.and(eq(ProfileThreadSnapshotRecord.TASK_ID, taskId))
                  .and(eq(ProfileThreadSnapshotRecord.SEQUENCE, 0));

        final LinkedList<String> segments = new LinkedList<>();
        QueryResult.Series series = client.queryForSingleSeries(countQuery);
        if (Objects.isNull(series)) {
            return Collections.emptyList();
        }
        series.getValues().forEach(values -> segments.add((String) values.get(1)));

        if (segments.isEmpty()) {
            return Collections.emptyList();
        }

        final WhereQueryImpl<SelectQueryImpl> whereQuery = select()
            .function(InfluxConstants.SORT_ASC, SegmentRecord.START_TIME, segments.size())
            .column(SegmentRecord.SEGMENT_ID)
            .column(SegmentRecord.START_TIME)
            .column(SegmentRecord.ENDPOINT_NAME)
            .column(SegmentRecord.LATENCY)
            .column(SegmentRecord.IS_ERROR)
            .column(SegmentRecord.TRACE_ID)
            .from(client.getDatabase(), SegmentRecord.INDEX_NAME)
            .where();
        whereQuery.and(contains(SegmentRecord.SEGMENT_ID, Joiner.on("|").join(segments)));

        ArrayList<BasicTrace> result = Lists.newArrayListWithCapacity(segments.size());
        client.queryForSingleSeries(whereQuery)
              .getValues()
              .stream()
              .sorted((a, b) -> Long.compare(((Number) b.get(1)).longValue(), ((Number) a.get(1)).longValue()))
              .forEach(values -> {
                  final BasicTrace basicTrace = new BasicTrace();

                  basicTrace.setSegmentId((String) values.get(2));
                  basicTrace.setStart(String.valueOf(((Number) values.get(3)).longValue()));
                  basicTrace.getEndpointNames().add((String) values.get(4));
                  basicTrace.setDuration(((Number) values.get(5)).intValue());
                  basicTrace.setError(BooleanUtils.valueToBoolean(((Number) values.get(6)).intValue()));
                  String traceIds = (String) values.get(7);
                  basicTrace.getTraceIds().add(traceIds);

                  result.add(basicTrace);
              });

        return result;
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
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId, int minSequence,
                                                          int maxSequence) throws IOException {
        WhereQueryImpl<SelectQueryImpl> whereQuery = select(
            ProfileThreadSnapshotRecord.TASK_ID,
            ProfileThreadSnapshotRecord.SEGMENT_ID,
            ProfileThreadSnapshotRecord.DUMP_TIME,
            ProfileThreadSnapshotRecord.SEQUENCE,
            ProfileThreadSnapshotRecord.STACK_BINARY
        )
            .from(client.getDatabase(), ProfileThreadSnapshotRecord.INDEX_NAME)
            .where(eq(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId));

        whereQuery.and(gte(ProfileThreadSnapshotRecord.SEQUENCE, minSequence))
                  .and(lte(ProfileThreadSnapshotRecord.SEQUENCE, maxSequence));

        final QueryResult.Series series = client.queryForSingleSeries(whereQuery);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result: {}", whereQuery.getCommand(), series);
        }
        if (Objects.isNull(series)) {
            return Collections.emptyList();
        }
        final ArrayList<ProfileThreadSnapshotRecord> result = new ArrayList<>(maxSequence - minSequence);
        series.getValues().forEach(values -> {
            ProfileThreadSnapshotRecord record = new ProfileThreadSnapshotRecord();

            record.setTaskId((String) values.get(1));
            record.setSegmentId((String) values.get(2));
            record.setDumpTime(((Number) values.get(3)).longValue());
            record.setSequence(((Number) values.get(4)).intValue());
            String dataBinaryBase64 = String.valueOf(values.get(5));
            if (StringUtil.isNotEmpty(dataBinaryBase64)) {
                record.setStackBinary(Base64.getDecoder().decode(dataBinaryBase64));
            }

            result.add(record);
        });

        return result;
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        WhereQueryImpl<SelectQueryImpl> whereQuery = select()
            .column(SegmentRecord.SEGMENT_ID)
            .column(SegmentRecord.TRACE_ID)
            .column(SegmentRecord.SERVICE_ID)
            .column(SegmentRecord.ENDPOINT_NAME)
            .column(SegmentRecord.START_TIME)
            .column(SegmentRecord.END_TIME)
            .column(SegmentRecord.LATENCY)
            .column(SegmentRecord.IS_ERROR)
            .column(SegmentRecord.DATA_BINARY)
            .column(SegmentRecord.VERSION)
            .from(client.getDatabase(), SegmentRecord.INDEX_NAME)
            .where();

        whereQuery.and(eq(SegmentRecord.SEGMENT_ID, segmentId));
        List<QueryResult.Series> series = client.queryForSeries(whereQuery);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", whereQuery.getCommand(), series);
        }
        if (Objects.isNull(series) || series.isEmpty()) {
            return null;
        }

        final List<Object> values = series.get(0).getValues().get(0);
        final SegmentRecord segmentRecord = new SegmentRecord();

        segmentRecord.setSegmentId((String) values.get(1));
        segmentRecord.setTraceId((String) values.get(2));
        segmentRecord.setServiceId((String) values.get(3));
        segmentRecord.setEndpointName((String) values.get(4));
        segmentRecord.setStartTime(((Number) values.get(5)).longValue());
        segmentRecord.setEndTime(((Number) values.get(6)).longValue());
        segmentRecord.setLatency(((Number) values.get(7)).intValue());
        segmentRecord.setIsError(((Number) values.get(8)).intValue());
        segmentRecord.setVersion(((Number) values.get(10)).intValue());

        final String base64 = (String) values.get(9);
        if (!Strings.isNullOrEmpty(base64)) {
            segmentRecord.setDataBinary(Base64.getDecoder().decode(base64));
        }

        return segmentRecord;
    }

    private int querySequenceWithAgg(String function, String segmentId, long start, long end) throws IOException {
        WhereQueryImpl<SelectQueryImpl> query = select()
            .function(function, ProfileThreadSnapshotRecord.SEQUENCE)
            .from(client.getDatabase(), ProfileThreadSnapshotRecord.INDEX_NAME)
            .where();

        query.and(eq(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
             .and(gte(ProfileThreadSnapshotRecord.DUMP_TIME, start))
             .and(lte(ProfileThreadSnapshotRecord.DUMP_TIME, end));
        return client.getCounter(query);
    }
}
