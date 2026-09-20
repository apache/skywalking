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
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.skywalking.banyandb.trace.v1.BanyandbTrace;
import org.apache.skywalking.library.banyandb.v1.client.DataPoint;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.TraceQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceRelationTraffic;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceSpanTraffic;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceTraffic;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.storage.query.IOTLPTraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.Conditions;

/**
 * Reads {@code otlp_span} through the BanyanDB trace model, which returns the spans of each matched trace already
 * grouped, and the three name catalogs through their index-mode measures.
 */
public class BanyanDBOTLPTraceQueryDAO extends AbstractBanyanDBDAO implements IOTLPTraceQueryDAO {
    private static final int QUERY_MAX_SIZE = Integer.MAX_VALUE;
    private static final Set<String> SERVICE_TRAFFIC_TAGS = ImmutableSet.of(OTLPServiceTraffic.SERVICE_NAME);
    private static final Set<String> SPAN_TRAFFIC_TAGS = ImmutableSet.of(OTLPServiceSpanTraffic.SPAN_NAME);
    private static final Set<String> RELATION_TRAFFIC_TAGS = ImmutableSet.of(OTLPServiceRelationTraffic.PEER_SERVICE);

    public BanyanDBOTLPTraceQueryDAO(final BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<String> getServiceNames(@Nullable final Duration duration) throws IOException {
        final MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(
            OTLPServiceTraffic.INDEX_NAME, DownSampling.Minute);
        final MeasureQueryResponse resp = queryDebuggable(
            false, schema, SERVICE_TRAFFIC_TAGS, Collections.emptySet(),
            getTimestampRange(duration),
            Conditions.create().limit(QUERY_MAX_SIZE));
        final List<String> services = new ArrayList<>();
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            services.add(dataPoint.getTagValue(OTLPServiceTraffic.SERVICE_NAME));
        }
        return services;
    }

    @Override
    public List<String> getSpanNames(final String serviceName, @Nullable final Duration duration) throws IOException {
        final MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(
            OTLPServiceSpanTraffic.INDEX_NAME, DownSampling.Minute);
        final Conditions where = Conditions.create();
        if (StringUtil.isNotEmpty(serviceName)) {
            where.eq(OTLPServiceSpanTraffic.SERVICE_NAME, serviceName);
        }
        where.limit(QUERY_MAX_SIZE);
        final MeasureQueryResponse resp = queryDebuggable(
            false, schema, SPAN_TRAFFIC_TAGS, Collections.emptySet(), getTimestampRange(duration), where);
        final List<String> spanNames = new ArrayList<>();
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            spanNames.add(dataPoint.getTagValue(OTLPServiceSpanTraffic.SPAN_NAME));
        }
        return spanNames;
    }

    @Override
    public List<String> getPeerServiceNames(final String serviceName,
                                            @Nullable final Duration duration) throws IOException {
        final MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(
            OTLPServiceRelationTraffic.INDEX_NAME, DownSampling.Minute);
        final Conditions where = Conditions.create();
        if (StringUtil.isNotEmpty(serviceName)) {
            where.eq(OTLPServiceRelationTraffic.SERVICE_NAME, serviceName);
        }
        where.limit(QUERY_MAX_SIZE);
        final MeasureQueryResponse resp = queryDebuggable(
            false, schema, RELATION_TRAFFIC_TAGS, Collections.emptySet(), getTimestampRange(duration), where);
        final List<String> peerServices = new ArrayList<>();
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            peerServices.add(dataPoint.getTagValue(OTLPServiceRelationTraffic.PEER_SERVICE));
        }
        return peerServices;
    }

    @Override
    public List<SpanWrapper> queryTraceById(final String traceId, @Nullable final Duration duration) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final Conditions where = Conditions.create()
                                           .eq(OTLPSpanRecord.TRACE_ID, traceId)
                                           .limit(QUERY_MAX_SIZE);
        final TraceQueryResponse resp = queryTraceDebuggable(
            isColdStage, OTLPSpanRecord.INDEX_NAME, getTimestampRange(duration), where);
        if (resp.getTraces().isEmpty()) {
            return new ArrayList<>();
        }
        if (resp.getTraces().size() > 1) {
            throw new IOException("More than one trace returned for traceId: " + traceId);
        }
        return unwrap(resp.getTraces().get(0).getSpansList());
    }

    @Override
    public List<List<SpanWrapper>> queryTraces(final OTLPTraceQueryCondition condition) throws IOException {
        final Duration duration = condition.getQueryDuration();
        final boolean isColdStage = duration != null && duration.isColdStage();
        final Conditions where = Conditions.create();
        if (StringUtil.isNotEmpty(condition.getTraceId())) {
            where.eq(OTLPSpanRecord.TRACE_ID, condition.getTraceId());
        }
        if (StringUtil.isNotEmpty(condition.getServiceName())) {
            where.eq(OTLPSpanRecord.SERVICE_NAME, condition.getServiceName());
        }
        if (StringUtil.isNotEmpty(condition.getServiceInstance())) {
            where.eq(OTLPSpanRecord.SERVICE_INSTANCE, condition.getServiceInstance());
        }
        if (StringUtil.isNotEmpty(condition.getScopeName())) {
            where.eq(OTLPSpanRecord.SCOPE_NAME, condition.getScopeName());
        }
        if (StringUtil.isNotEmpty(condition.getSpanName())) {
            where.eq(OTLPSpanRecord.NAME, condition.getSpanName());
        }
        if (StringUtil.isNotEmpty(condition.getPeerService())) {
            where.eq(OTLPSpanRecord.PEER_SERVICE, condition.getPeerService());
        }
        if (condition.getKind() != null) {
            where.eq(OTLPSpanRecord.KIND, condition.getKind().longValue());
        }
        if (condition.getStatusCode() != null) {
            where.eq(OTLPSpanRecord.STATUS_CODE, condition.getStatusCode().longValue());
        }
        if (condition.getMinDurationNanos() > 0) {
            where.gte(OTLPSpanRecord.DURATION, condition.getMinDurationNanos());
        }
        if (condition.getMaxDurationNanos() > 0) {
            where.lte(OTLPSpanRecord.DURATION, condition.getMaxDurationNanos());
        }
        if (CollectionUtils.isNotEmpty(condition.getTags())) {
            final List<String> tagConditions = new ArrayList<>(condition.getTags().size());
            for (final Tag tag : condition.getTags()) {
                tagConditions.add(tag.getKey() + "=" + tag.getValue());
            }
            where.having(OTLPSpanRecord.TAGS, tagConditions);
        }
        if (condition.getQueryOrder() == QueryOrder.BY_DURATION) {
            where.orderByDesc(OTLPSpanRecord.DURATION);
        } else {
            where.orderByDesc(OTLPSpanRecord.START_TIME);
        }
        where.limit(condition.getLimit());

        final TraceQueryResponse resp = queryTraceDebuggable(
            isColdStage, OTLPSpanRecord.INDEX_NAME, getTimestampRange(duration), where);
        final List<List<SpanWrapper>> traces = new ArrayList<>(resp.getTraces().size());
        for (final var trace : resp.getTraces()) {
            traces.add(unwrap(trace.getSpansList()));
        }
        return traces;
    }

    private static List<SpanWrapper> unwrap(final List<BanyandbTrace.Span> spans) throws IOException {
        final List<SpanWrapper> trace = new ArrayList<>(spans.size());
        for (final var span : spans) {
            trace.add(SpanWrapper.parseFrom(span.getSpan()));
        }
        return trace;
    }
}
