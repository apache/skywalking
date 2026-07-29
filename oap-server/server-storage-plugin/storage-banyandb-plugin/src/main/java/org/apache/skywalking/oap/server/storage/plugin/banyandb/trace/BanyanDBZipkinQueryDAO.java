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

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.skywalking.library.banyandb.v1.client.DataPoint;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.TraceQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryV2DAO;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceRelationTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceSpanTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.Conditions;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

public class BanyanDBZipkinQueryDAO extends AbstractBanyanDBDAO implements IZipkinQueryV2DAO {
    private final static int QUERY_MAX_SIZE = Integer.MAX_VALUE;
    private static final Set<String> SERVICE_TRAFFIC_TAGS = ImmutableSet.of(ZipkinServiceTraffic.SERVICE_NAME);
    private static final Set<String> REMOTE_SERVICE_TRAFFIC_TAGS = ImmutableSet.of(
        ZipkinServiceRelationTraffic.REMOTE_SERVICE_NAME);
    private static final Set<String> SPAN_TRAFFIC_TAGS = ImmutableSet.of(ZipkinServiceSpanTraffic.SPAN_NAME);

    public BanyanDBZipkinQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<String> getServiceNames() throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(ZipkinServiceTraffic.INDEX_NAME, DownSampling.Minute);
        MeasureQueryResponse resp = queryDebuggable(
            false, schema, SERVICE_TRAFFIC_TAGS, Collections.emptySet(),
            null,
            Conditions.create().limit(QUERY_MAX_SIZE));
        final List<String> services = new ArrayList<>();
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            services.add(dataPoint.getTagValue(ZipkinServiceTraffic.SERVICE_NAME));
        }
        return services;
    }

    @Override
    public List<String> getRemoteServiceNames(final String serviceName) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(ZipkinServiceRelationTraffic.INDEX_NAME, DownSampling.Minute);
        final Conditions where = Conditions.create();
        if (StringUtil.isNotEmpty(serviceName)) {
            where.eq(ZipkinServiceRelationTraffic.SERVICE_NAME, serviceName);
        }
        where.limit(QUERY_MAX_SIZE);
        MeasureQueryResponse resp = queryDebuggable(
            false, schema, REMOTE_SERVICE_TRAFFIC_TAGS, Collections.emptySet(),
            null,
            where);
        final List<String> remoteServices = new ArrayList<>();
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            remoteServices.add(dataPoint.getTagValue(ZipkinServiceRelationTraffic.REMOTE_SERVICE_NAME));
        }
        return remoteServices;
    }

    @Override
    public List<String> getSpanNames(final String serviceName) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(ZipkinServiceSpanTraffic.INDEX_NAME, DownSampling.Minute);
        final Conditions where = Conditions.create();
        if (StringUtil.isNotEmpty(serviceName)) {
            where.eq(ZipkinServiceSpanTraffic.SERVICE_NAME, serviceName);
        }
        where.limit(QUERY_MAX_SIZE);
        MeasureQueryResponse resp = queryDebuggable(
            false, schema, SPAN_TRAFFIC_TAGS, Collections.emptySet(),
            null,
            where);
        final List<String> spanNames = new ArrayList<>();
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            spanNames.add(dataPoint.getTagValue(ZipkinServiceSpanTraffic.SPAN_NAME));
        }
        return spanNames;
    }

    @Override
    public List<Span> getTrace(final String traceId, @Nullable final Duration duration) throws IOException {
        throw new UnsupportedOperationException("BanyanDB Trace Model changed, please use getTraceV2.");
    }

    @Override
    public List<List<Span>> getTraces(final QueryRequest request, Duration duration) throws IOException {
        throw new UnsupportedOperationException("BanyanDB Trace Model changed, please use getTracesV2.");
    }

    @Override
    public List<List<Span>> getTraces(final Set<String> traceIds, @Nullable final Duration duration) throws IOException {
        throw new UnsupportedOperationException("BanyanDB Trace Model changed, please use getTracesV2.");
    }

    @Override
    public List<SpanWrapper> getTraceV2(final String traceId, @Nullable final Duration duration) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final Conditions where = Conditions.create()
            .eq(ZipkinSpanRecord.TRACE_ID, traceId)
            .limit(QUERY_MAX_SIZE);
        TraceQueryResponse resp = queryTraceDebuggable(isColdStage, ZipkinSpanRecord.INDEX_NAME, getTimestampRange(duration), where);
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

    @Override
    public List<List<SpanWrapper>> getTracesV2(final Set<String> traceIds, final Duration duration) throws IOException {
        if (CollectionUtils.isEmpty(traceIds)) {
            return Collections.emptyList();
        }
        final boolean isColdStage = duration != null && duration.isColdStage();
        final Conditions where = Conditions.create()
            .in(ZipkinSpanRecord.TRACE_ID, new ArrayList<>(traceIds))
            .orderByDesc(ZipkinSpanRecord.TIMESTAMP_MILLIS)
            .limit(QUERY_MAX_SIZE);
        TraceQueryResponse resp = queryTraceDebuggable(
            isColdStage, ZipkinSpanRecord.INDEX_NAME, getTimestampRange(duration), where);
        List<List<SpanWrapper>> traces = new ArrayList<>();
        for (var t : resp.getTraces()) {
            List<SpanWrapper> trace = new ArrayList<>();
            for (var span : t.getSpansList()) {
                trace.add(SpanWrapper.parseFrom(span.getSpan()));
            }
            traces.add(trace);
        }

        return traces;
    }

    @Override
    public List<List<SpanWrapper>> getTracesV2(final QueryRequest request, final Duration duration) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();

        final Conditions where = Conditions.create();
        if (!StringUtil.isEmpty(request.serviceName())) {
            where.eq(ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME, request.serviceName());
        }
        if (!StringUtil.isEmpty(request.remoteServiceName())) {
            where.eq(ZipkinSpanRecord.REMOTE_ENDPOINT_SERVICE_NAME, request.remoteServiceName());
        }
        if (!StringUtil.isEmpty(request.spanName())) {
            where.eq(ZipkinSpanRecord.NAME, request.spanName());
        }
        if (!CollectionUtils.isEmpty(request.annotationQuery())) {
            List<String> queryConditions = new ArrayList<>();
            for (Map.Entry<String, String> annotation : request.annotationQuery().entrySet()) {
                if (annotation.getValue().isEmpty()) {
                    queryConditions.add(annotation.getKey());
                } else {
                    queryConditions.add(annotation.getKey() + "=" + annotation.getValue());
                }
            }
            where.having(ZipkinSpanRecord.QUERY, queryConditions);
        }
        if (request.minDuration() != null) {
            where.gte(ZipkinSpanRecord.DURATION, request.minDuration());
        }
        if (request.maxDuration() != null) {
            where.lte(ZipkinSpanRecord.DURATION, request.maxDuration());
        }
        where.orderByDesc(ZipkinSpanRecord.TIMESTAMP_MILLIS).limit(request.limit());
        TraceQueryResponse resp = queryTraceDebuggable(
            isColdStage, ZipkinSpanRecord.INDEX_NAME, getTimestampRange(duration), where);
        List<List<SpanWrapper>> traces = new ArrayList<>();
        for (var t : resp.getTraces()) {
            List<SpanWrapper> trace = new ArrayList<>();
            for (var span : t.getSpansList()) {
                trace.add(SpanWrapper.parseFrom(span.getSpan()));
            }
            traces.add(trace);
        }

        return traces;
    }
}
