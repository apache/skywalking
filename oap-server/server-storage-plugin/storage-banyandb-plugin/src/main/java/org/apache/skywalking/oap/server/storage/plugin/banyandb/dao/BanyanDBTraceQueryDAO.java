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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.client.request.TraceFetchRequest;
import org.apache.skywalking.banyandb.client.request.TraceSearchQuery;
import org.apache.skywalking.banyandb.client.request.TraceSearchRequest;
import org.apache.skywalking.banyandb.client.response.BanyanDBQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBSchema;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BanyanDBTraceQueryDAO extends AbstractDAO<BanyanDBClient> implements ITraceQueryDAO {
    private static final Set<String> DEFAULT_PROJECTION = ImmutableSet.of("duration", "state", "start_time", "trace_id");

    public BanyanDBTraceQueryDAO(BanyanDBClient client) {
        super(client);
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration, String serviceId, String serviceInstanceId, String endpointId, String traceId, int limit, int from, TraceState traceState, QueryOrder queryOrder, List<Tag> tags) throws IOException {
        TraceSearchRequest.TraceSearchRequestBuilder queryBuilder = TraceSearchRequest.builder();
        if (startSecondTB != 0 && endSecondTB != 0) {
            queryBuilder.timeRange(new TraceSearchRequest.TimeRange(startSecondTB, endSecondTB));
        }
        if (minDuration != 0) {
            // duration >= minDuration
            queryBuilder.query(TraceSearchQuery.ge("duration", minDuration));
        }
        if (maxDuration != 0) {
            // duration <= maxDuration
            queryBuilder.query(TraceSearchQuery.le("duration", maxDuration));
        }

        if (!Strings.isNullOrEmpty(serviceId)) {
            queryBuilder.query(TraceSearchQuery.eq("service_id", serviceId));
        }

        if (!Strings.isNullOrEmpty(serviceInstanceId)) {
            queryBuilder.query(TraceSearchQuery.eq("service_instance_id", serviceInstanceId));
        }

        if (!Strings.isNullOrEmpty(endpointId)) {
            queryBuilder.query(TraceSearchQuery.eq("endpoint_id", endpointId));
        }

        switch (traceState) {
            case ERROR:
                queryBuilder.query(TraceSearchQuery.eq("state", BanyanDBSchema.TraceState.ERROR.getState()));
                break;
            case SUCCESS:
                queryBuilder.query(TraceSearchQuery.eq("state", BanyanDBSchema.TraceState.SUCCESS.getState()));
                break;
            default:
                queryBuilder.query(TraceSearchQuery.eq("state", BanyanDBSchema.TraceState.ALL.getState()));
                break;
        }

        switch (queryOrder) {
            case BY_START_TIME:
                queryBuilder.orderBy(new TraceSearchRequest.OrderBy("start_time", TraceSearchRequest.SortOrder.DESC));
                break;
            case BY_DURATION:
                queryBuilder.orderBy(new TraceSearchRequest.OrderBy("duration", TraceSearchRequest.SortOrder.DESC));
                break;
        }

        queryBuilder.projections(DEFAULT_PROJECTION);
        queryBuilder.limit(limit);
        queryBuilder.offset(from);

        // build request
        BanyanDBQueryResponse response = this.getClient().queryBasicTraces(queryBuilder.build());
        TraceBrief brief = new TraceBrief();
        brief.setTotal(response.getTotal());
        brief.getTraces().addAll(response.getEntities().stream().map(entity -> {
            BasicTrace trace = new BasicTrace();
            trace.setDuration(((Long) entity.getFields().get("duration")).intValue());
            trace.setStart(String.valueOf(entity.getFields().get(SegmentRecord.START_TIME)));
            trace.setSegmentId(entity.getEntityId());
            trace.setError(((Long) entity.getFields().get("state")).intValue() == 1);
            trace.getTraceIds().add((String) entity.getFields().get(SegmentRecord.TRACE_ID));
            trace.getEndpointNames().add(IDManager.EndpointID.analysisId(
                    (String) entity.getFields().get(SegmentRecord.ENDPOINT_ID)
            ).getEndpointName());
            return trace;
        }).collect(Collectors.toList()));
        return brief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        TraceFetchRequest request = TraceFetchRequest.builder()
                .traceId(traceId)
                .projections(BanyanDBSchema.FIELD_NAMES)
                .projection("data_binary").build();
        BanyanDBQueryResponse response = this.getClient().queryByTraceId(request);
        return response.getEntities().stream().map(entity -> {
            SegmentRecord record = new SegmentRecord();
            record.setSegmentId(entity.getEntityId());
            record.setTraceId((String) entity.getFields().get(SegmentRecord.TRACE_ID));
            record.setServiceId((String) entity.getFields().get(SegmentRecord.SERVICE_ID));
            record.setServiceInstanceId((String) entity.getFields().get(SegmentRecord.SERVICE_INSTANCE_ID));
            record.setEndpointId((String) entity.getFields().get(SegmentRecord.ENDPOINT_ID));
            record.setStartTime(((Number) entity.getFields().get(SegmentRecord.START_TIME)).longValue());
            record.setLatency(((Number) entity.getFields().get("duration")).intValue());
            record.setIsError(((Number) entity.getFields().get("state")).intValue());
            record.setDataBinary(entity.getBinaryData());
            return record;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        return Collections.emptyList();
    }
}
