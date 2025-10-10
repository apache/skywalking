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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.trace;

import javax.annotation.Nullable;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.banyandb.v1.client.TraceQuery;
import org.apache.skywalking.banyandb.v1.client.TraceQueryResponse;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.query.PaginationUtils;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.RetrievedTimeRange;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TracesQueryResult;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryV2DAO;
import org.apache.skywalking.oap.server.core.storage.query.proto.Source;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

public class BanyanDBTraceQueryDAO extends AbstractBanyanDBDAO implements ITraceQueryV2DAO {
    private final int segmentQueryMaxSize;
    private final ModuleManager moduleManager;
    private NamingControl namingControl;

    public BanyanDBTraceQueryDAO(BanyanDBStorageClient client, int segmentQueryMaxSize, ModuleManager moduleManager) {
        super(client);
        this.segmentQueryMaxSize = segmentQueryMaxSize;
        this.moduleManager = moduleManager;
    }

    @Override
    public TraceBrief queryBasicTraces(Duration duration, long minDuration, long maxDuration, String serviceId, String serviceInstanceId, String endpointId, String traceId, int limit, int from, TraceState traceState, QueryOrder queryOrder, List<Tag> tags) throws IOException {
        throw new UnsupportedOperationException("BanyanDB Trace Model changed, please use queryTraces");
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId, @Nullable Duration duration) throws IOException {
        throw new UnsupportedOperationException("BanyanDB Trace Model changed, please use queryByTraceIdV2");
    }

    @Override
    public List<SegmentRecord> queryBySegmentIdList(List<String> segmentIdList, @Nullable Duration duration) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        TraceQueryResponse resp = queryTraceDebuggable(isColdStage, SegmentRecord.INDEX_NAME, getTimestampRange(duration),
            new QueryBuilder<TraceQuery>() {
                @Override
                public void apply(TraceQuery query) {
                    query.and(in(SegmentRecord.SEGMENT_ID, segmentIdList));
                    query.setLimit(segmentQueryMaxSize);
                    query.setOrderBy(new TraceQuery.OrderBy(SegmentRecord.START_TIME, AbstractQuery.Sort.DESC));
                }
            });
        return buildRecords(resp, segmentIdList, null, true);
    }

    @Override
    public List<SegmentRecord> queryByTraceIdWithInstanceId(List<String> traceIdList, List<String> instanceIdList, @Nullable Duration duration) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        TraceQueryResponse resp = queryTraceDebuggable(isColdStage, SegmentRecord.INDEX_NAME, getTimestampRange(duration),
                                         new QueryBuilder<TraceQuery>() {
                                             @Override
                                             public void apply(TraceQuery query) {
                                                 query.and(in(SegmentRecord.TRACE_ID, traceIdList));
                                                 query.and(in(SegmentRecord.SERVICE_INSTANCE_ID, instanceIdList));
                                                 query.setLimit(segmentQueryMaxSize);
                                             }
                                         });

        return buildRecords(resp, null, instanceIdList, false);
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<SpanWrapper> queryByTraceIdV2(final String traceId,
                                              @Nullable final Duration duration) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final QueryBuilder<TraceQuery> query = new QueryBuilder<TraceQuery>() {
            @Override
            public void apply(TraceQuery query) {
                query.and(eq(SegmentRecord.TRACE_ID, traceId));
                query.setLimit(segmentQueryMaxSize);
            }
        };
        TraceQueryResponse resp = queryTraceDebuggable(isColdStage, SegmentRecord.INDEX_NAME, getTimestampRange(duration), query);
        if (resp.getTraces().isEmpty()) {
            return new ArrayList<>();
        }
        if (resp.getTraces().size() > 1) {
            throw new IOException("More than one trace returned for traceId: " + traceId);
        }
        List<SpanWrapper> trace = new ArrayList<>();
        for (var span : resp.getTraces().get(0).getSpansList()) {
            trace.add(SpanWrapper.parseFrom(span.getSpan()));
        }

        return trace;
    }

    public TracesQueryResult queryTraces(final TraceQueryCondition condition) throws IOException {
        Duration duration = condition.getQueryDuration();
        final boolean isColdStage = duration != null && duration.isColdStage();
        final QueryBuilder<TraceQuery> query = new QueryBuilder<TraceQuery>() {
                @Override
                public void apply(TraceQuery query) {
                    if (StringUtil.isNotBlank(condition.getTraceId())) {
                        query.and(eq(SegmentRecord.TRACE_ID, condition.getTraceId()));
                    }
                    if (condition.getMinTraceDuration() != 0) {
                        // duration >= minDuration
                        query.and(gte(SegmentRecord.LATENCY, condition.getMinTraceDuration()));
                    }
                    if (condition.getMaxTraceDuration() != 0) {
                        // duration <= maxDuration
                        query.and(lte(SegmentRecord.LATENCY, condition.getMaxTraceDuration()));
                    }

                    if (StringUtil.isNotEmpty(condition.getServiceId())) {
                        query.and(eq(SegmentRecord.SERVICE_ID, condition.getServiceId()));
                    }

                    if (StringUtil.isNotEmpty(condition.getServiceInstanceId())) {
                        if (StringUtil.isEmpty(condition.getServiceId())) {
                            IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID.analysisId(
                                condition.getServiceInstanceId());
                            query.and(eq(SegmentRecord.SERVICE_ID, instanceIDDefinition.getServiceId()));
                        }
                        query.and(eq(SegmentRecord.SERVICE_INSTANCE_ID, condition.getServiceInstanceId()));
                    }

                    if (StringUtil.isNotEmpty(condition.getEndpointId())) {
                        if (StringUtil.isEmpty(condition.getServiceId())) {
                            IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition = IDManager.EndpointID.analysisId(
                                condition.getEndpointId());
                            query.and(eq(SegmentRecord.SERVICE_ID, endpointIDDefinition.getServiceId()));
                        }
                        query.and(eq(SegmentRecord.ENDPOINT_ID, condition.getEndpointId()));
                    }

                    switch (condition.getTraceState()) {
                        case ERROR:
                            query.and(eq(SegmentRecord.IS_ERROR, BooleanUtils.TRUE));
                            break;
                        case SUCCESS:
                            query.and(eq(SegmentRecord.IS_ERROR, BooleanUtils.FALSE));
                            break;
                    }

                    switch (condition.getQueryOrder()) {
                        case BY_START_TIME:
                            query.setOrderBy(new TraceQuery.OrderBy(SegmentRecord.START_TIME, AbstractQuery.Sort.DESC));
                            break;
                        case BY_DURATION:
                            query.setOrderBy(new TraceQuery.OrderBy(SegmentRecord.LATENCY, AbstractQuery.Sort.DESC));
                            break;
                    }
                    List<Tag> tags = condition.getTags();
                    if (CollectionUtils.isNotEmpty(tags)) {
                        List<String> tagsConditions = new ArrayList<>(tags.size());
                        for (final Tag tag : tags) {
                            tagsConditions.add(tag.toString());
                        }
                        query.and(having(SegmentRecord.TAGS, tagsConditions));
                    }
                    PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());
                    query.setLimit(page.getLimit());
                    query.setOffset(page.getFrom());
                }
            };
        TimestampRange timestampRange = getTimestampRange(duration);
        TraceQueryResponse resp = queryTraceDebuggable(
            isColdStage, SegmentRecord.INDEX_NAME, timestampRange, query);
        List<List<SpanWrapper>> traces = new ArrayList<>();
        for (var t : resp.getTraces()) {
            List<SpanWrapper> trace = new ArrayList<>();
            for (var span : t.getSpansList()) {
                trace.add(SpanWrapper.parseFrom(span.getSpan()));
            }
            traces.add(trace);
        }
        return new TracesQueryResult(traces, new RetrievedTimeRange(timestampRange.getBegin(), timestampRange.getEnd()));
    }

    /**
     * Notice: this method not build the full SegmentRecord, only build the fields needed by ProfiledTraceSegments and ProfiledSegment
     */
    private List<SegmentRecord> buildRecords(TraceQueryResponse resp,
                                             @Nullable List<String> segmentIdList,
                                             @Nullable List<String> instanceIdList,
                                             boolean filterBySegmentId) throws IOException {
        List<SegmentRecord> segmentRecords = new ArrayList<>();
        for (var t : resp.getTraces()) {
            for (var wrapper : t.getSpansList()) {
                SpanWrapper spanWrapper = SpanWrapper.parseFrom(wrapper.getSpan());
                if (spanWrapper.getSource().equals(Source.SKYWALKING)) {
                    SegmentObject segmentObject = SegmentObject.parseFrom(spanWrapper.getSpan());
                    SegmentRecord segmentRecord = new SegmentRecord();
                    if (filterBySegmentId) {
                        if (segmentIdList == null || !segmentIdList.contains(segmentObject.getTraceSegmentId())) {
                            continue;
                        }
                    }
                    segmentRecord.setSegmentId(segmentObject.getTraceSegmentId());
                    segmentRecord.setTraceId(segmentObject.getTraceId());
                    String serviceName = Const.EMPTY_STRING;
                    String instanceName = Const.EMPTY_STRING;
                    String endpointName = Const.EMPTY_STRING;
                    serviceName = getNamingControl().formatServiceName(segmentObject.getService());
                    instanceName = getNamingControl().formatInstanceName(segmentObject.getServiceInstance());
                    String serviceId = IDManager.ServiceID.buildId(serviceName, true);
                    segmentRecord.setServiceId(serviceId);
                    String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, instanceName);
                    if (!filterBySegmentId) {
                        if (instanceIdList == null || !instanceIdList.contains(serviceInstanceId)) {
                            continue;
                        }
                    }
                    segmentRecord.setServiceInstanceId(serviceInstanceId);
                    long startTimestamp = 0;
                    long endTimestamp = 0;

                    for (SpanObject span : segmentObject.getSpansList()) {
                        if (startTimestamp == 0 || startTimestamp > span.getStartTime()) {
                            startTimestamp = span.getStartTime();
                        }
                        if (span.getEndTime() > endTimestamp) {
                            endTimestamp = span.getEndTime();
                        }
                        if (span.getSpanId() == 0) {
                            endpointName = getNamingControl().formatEndpointName(serviceName, span.getOperationName());
                        }
                        if (SpanType.Entry.equals(span.getSpanType())) {
                            endpointName = getNamingControl().formatEndpointName(serviceName, span.getOperationName());
                        }
                    }
                    String endpointId = IDManager.EndpointID.buildId(serviceId, endpointName);
                    segmentRecord.setEndpointId(endpointId);
                    final long accurateDuration = endTimestamp - startTimestamp;
                    int duration = accurateDuration > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) accurateDuration;
                    segmentRecord.setLatency(duration);
                    segmentRecord.setStartTime(startTimestamp);
                    segmentRecord.setDataBinary(spanWrapper.getSpan().toByteArray());
                    segmentRecords.add(segmentRecord);
                }
            }
        }
        return segmentRecords;
    }

    private NamingControl getNamingControl() {
        if (namingControl == null) {
            this.namingControl = moduleManager.find(CoreModule.NAME)
                                              .provider()
                                              .getService(NamingControl.class);
        }
        return namingControl;
    }
}
