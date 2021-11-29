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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.*;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.*;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBSchema;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.converter.BasicTraceMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.converter.RowEntityMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.converter.SegmentRecordMapper;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BanyanDBTraceQueryDAO extends AbstractDAO<BanyanDBStorageClient> implements ITraceQueryDAO {
    private static final RowEntityMapper<SegmentRecord> SEGMENT_RECORD_MAPPER = new SegmentRecordMapper();
    private static final RowEntityMapper<BasicTrace> BASIC_TRACE_MAPPER = new BasicTraceMapper();

    private static final DateTimeFormatter YYYYMMDDHHMMSS = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    private static final List<String> BASIC_QUERY_PROJ = ImmutableList.of("trace_id", "state", "endpoint_id", "duration", "start_time");
    private static final List<String> TRACE_ID_QUERY_PROJ = ImmutableList.of("trace_id", "state", "service_id", "service_instance_id", "endpoint_id", "duration", "start_time");

    public BanyanDBTraceQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration, String serviceId, String serviceInstanceId, String endpointId, String traceId, int limit, int from, TraceState traceState, QueryOrder queryOrder, List<Tag> tags) throws IOException {
        StreamQuery query;
        if (startSecondTB != 0 && endSecondTB != 0) {
            query = new StreamQuery(BanyanDBSchema.NAME, new TimestampRange(parseMillisFromStartSecondTB(startSecondTB), parseMillisFromEndSecondTB(endSecondTB)), BASIC_QUERY_PROJ);
        } else {
            query = new StreamQuery(BanyanDBSchema.NAME, BASIC_QUERY_PROJ);
        }
        if (minDuration != 0) {
            // duration >= minDuration
            query.appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable", "duration", minDuration));
        }
        if (maxDuration != 0) {
            // duration <= maxDuration
            query.appendCondition(PairQueryCondition.LongQueryCondition.le("searchable", "duration", maxDuration));
        }

        if (!Strings.isNullOrEmpty(serviceId)) {
            query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", "service_id", serviceId));
        }

        if (!Strings.isNullOrEmpty(serviceInstanceId)) {
            query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", "service_instance_id", serviceInstanceId));
        }

        if (!Strings.isNullOrEmpty(endpointId)) {
            query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", "endpoint_id", endpointId));
        }

        switch (traceState) {
            case ERROR:
                query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", "state", (long) BanyanDBSchema.TraceState.ERROR.getState()));
                break;
            case SUCCESS:
                query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", "state", (long) BanyanDBSchema.TraceState.SUCCESS.getState()));
                break;
            default:
                query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", "state", (long) BanyanDBSchema.TraceState.ALL.getState()));
                break;
        }

        switch (queryOrder) {
            case BY_START_TIME:
                query.setOrderBy(new StreamQuery.OrderBy("start_time", StreamQuery.OrderBy.Type.DESC));
                break;
            case BY_DURATION:
                query.setOrderBy(new StreamQuery.OrderBy("duration", StreamQuery.OrderBy.Type.DESC));
                break;
        }

        if (CollectionUtils.isNotEmpty(tags)) {
            for (final Tag tag : tags) {
                if (BanyanDBSchema.INDEX_FIELDS.contains(tag.getKey())) {
                    query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", tag.getKey(), tag.getValue()));
                }
            }
        }

        query.setLimit(limit);
        query.setOffset(from);

        // build request
        StreamQueryResponse response = this.getClient().query(query);
        TraceBrief brief = new TraceBrief();
        brief.setTotal(response.size());
        brief.getTraces().addAll(response.getElements().stream().map(BASIC_TRACE_MAPPER::map).collect(Collectors.toList()));
        return brief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        StreamQuery query = new StreamQuery(BanyanDBSchema.NAME, TRACE_ID_QUERY_PROJ);
        query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", "trace_id", traceId));
        query.setDataBinary(true);
        StreamQueryResponse response = this.getClient().query(query);
        return response.getElements().stream().map(SEGMENT_RECORD_MAPPER::map).collect(Collectors.toList());
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        return Collections.emptyList();
    }

    static long parseMillisFromStartSecondTB(long startSecondTB) {
        return YYYYMMDDHHMMSS.withZone(DateTimeZone.UTC).parseMillis(String.valueOf(startSecondTB));
    }

    static long parseMillisFromEndSecondTB(long endSecondTB) {
        long t = endSecondTB;
        long second = t % 100;
        if (second > 59) {
            second = 0;
        }
        t = t / 100;
        long minute = t % 100;
        if (minute > 59) {
            minute = 0;
        }
        t = t / 100;
        long hour = t % 100;
        if (hour > 23) {
            hour = 0;
        }
        t = t / 100;
        return YYYYMMDDHHMMSS.withZone(DateTimeZone.UTC)
                .parseMillis(String.valueOf(((t * 100 + hour) * 100 + minute) * 100 + second));
    }
}
