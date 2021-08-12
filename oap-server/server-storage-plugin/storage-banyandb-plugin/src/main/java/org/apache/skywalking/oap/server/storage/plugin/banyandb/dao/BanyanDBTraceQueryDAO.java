package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.client.request.TraceFetchRequest;
import org.apache.skywalking.banyandb.client.request.TraceSearchQuery;
import org.apache.skywalking.banyandb.client.request.TraceSearchRequest;
import org.apache.skywalking.banyandb.client.response.BanyanDBQueryResponse;
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
            queryBuilder.timeRange(TraceSearchRequest.TimeRange.builder()
                    .startTime(startSecondTB)
                    .endTime(endSecondTB).build());
        }
        if (minDuration != 0) {
            // duration >= minDuration
            queryBuilder.query(TraceSearchQuery.Ge("duration", minDuration));
        }
        if (maxDuration != 0) {
            // duration <= maxDuration
            queryBuilder.query(TraceSearchQuery.Le("duration", maxDuration));
        }

        if (!Strings.isNullOrEmpty(serviceId)) {
            queryBuilder.query(TraceSearchQuery.Eq("service_id", serviceId));
        }

        if (!Strings.isNullOrEmpty(serviceInstanceId)) {
            queryBuilder.query(TraceSearchQuery.Eq("service_instance_id", serviceInstanceId));
        }

        if (!Strings.isNullOrEmpty(endpointId)) {
            queryBuilder.query(TraceSearchQuery.Eq("endpoint_id", endpointId));
        }

        switch (traceState) {
            case ERROR:
                queryBuilder.query(TraceSearchQuery.Eq("state", BanyanDBSchema.TraceState.ERROR.getState()));
                break;
            case SUCCESS:
                queryBuilder.query(TraceSearchQuery.Eq("state", BanyanDBSchema.TraceState.SUCCESS.getState()));
                break;
            default:
                queryBuilder.query(TraceSearchQuery.Eq("state", BanyanDBSchema.TraceState.ALL.getState()));
                break;
        }

        switch (queryOrder) {
            case BY_START_TIME:
                queryBuilder.queryOrderField("start_time").queryOrderSort(TraceSearchRequest.SortOrder.DESC);
                break;
            case BY_DURATION:
                queryBuilder.queryOrderField("duration").queryOrderSort(TraceSearchRequest.SortOrder.DESC);
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
            trace.setStart(String.valueOf(entity.getFields().get("start_time")));
            trace.setSegmentId(entity.getEntityId());
            trace.setError(((Long) entity.getFields().get("state")).intValue() == 1);
            trace.getTraceIds().add((String) entity.getFields().get("trace_id"));
            // TODO: endpoint names?
            return trace;
        }).collect(Collectors.toList()));
        return brief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        TraceFetchRequest.TraceFetchRequestBuilder queryBuilder = TraceFetchRequest.builder()
                .traceId(traceId)
                .projections(BanyanDBSchema.FIELD_NAMES)
                .projection("data_binary");
        BanyanDBQueryResponse response = this.getClient().queryByTraceId(queryBuilder.build());
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
            record.setTimeBucket(entity.getTimestampSeconds());
            return record;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        return Collections.emptyList();
    }
}
