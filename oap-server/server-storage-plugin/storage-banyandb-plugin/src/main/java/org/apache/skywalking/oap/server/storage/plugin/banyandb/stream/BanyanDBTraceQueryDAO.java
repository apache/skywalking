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
import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBSchema;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.BasicTraceMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.RowEntityMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.SegmentRecordMapper;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BanyanDBTraceQueryDAO extends AbstractBanyanDBDAO implements ITraceQueryDAO {
    private static final RowEntityMapper<SegmentRecord> SEGMENT_RECORD_MAPPER = new SegmentRecordMapper();
    private static final RowEntityMapper<BasicTrace> BASIC_TRACE_MAPPER = new BasicTraceMapper();

    private static final DateTimeFormatter YYYYMMDDHHMMSS = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    public BanyanDBTraceQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration, String serviceId, String serviceInstanceId, String endpointId, String traceId, int limit, int from, TraceState traceState, QueryOrder queryOrder, List<Tag> tags) throws IOException {
        final QueryBuilder builder = new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
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
            }
        };

        final List<BasicTrace> basicTraces;
        if (startSecondTB != 0 && endSecondTB != 0) {
            basicTraces = query(BasicTrace.class, builder, TimeBucket.getTimestamp(startSecondTB), TimeBucket.getTimestamp(endSecondTB));
        } else {
            basicTraces = query(BasicTrace.class, builder);
        }

        TraceBrief brief = new TraceBrief();
        brief.setTotal(basicTraces.size());
        brief.getTraces().addAll(basicTraces);
        return brief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        return query(SegmentRecord.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", "trace_id", traceId));
            }
        });
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        return Collections.emptyList();
    }
}
