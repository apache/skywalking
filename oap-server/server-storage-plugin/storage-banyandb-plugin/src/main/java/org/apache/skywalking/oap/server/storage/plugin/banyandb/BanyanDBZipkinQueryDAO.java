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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.banyandb.v1.client.AbstractCriteria;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceRelationTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceSpanTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

public class BanyanDBZipkinQueryDAO extends AbstractBanyanDBDAO implements IZipkinQueryDAO {
    private final static int QUERY_MAX_SIZE = Integer.MAX_VALUE;
    private static final Set<String> SERVICE_TRAFFIC_TAGS = ImmutableSet.of(ZipkinServiceTraffic.SERVICE_NAME);
    private static final Set<String> REMOTE_SERVICE_TRAFFIC_TAGS = ImmutableSet.of(
        ZipkinServiceRelationTraffic.REMOTE_SERVICE_NAME);
    private static final Set<String> SPAN_TRAFFIC_TAGS = ImmutableSet.of(ZipkinServiceSpanTraffic.SPAN_NAME);
    private static final Set<String> TRACE_ID = ImmutableSet.of(ZipkinSpanRecord.TRACE_ID);
    private static final Set<String> TRACE_TAGS = ImmutableSet.of(
        ZipkinSpanRecord.TRACE_ID,
        ZipkinSpanRecord.SPAN_ID,
        ZipkinSpanRecord.PARENT_ID,
        ZipkinSpanRecord.KIND,
        ZipkinSpanRecord.TIMESTAMP,
        ZipkinSpanRecord.TIMESTAMP_MILLIS,
        ZipkinSpanRecord.DURATION,
        ZipkinSpanRecord.NAME,
        ZipkinSpanRecord.DEBUG,
        ZipkinSpanRecord.SHARED,
        ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME,
        ZipkinSpanRecord.LOCAL_ENDPOINT_IPV4,
        ZipkinSpanRecord.LOCAL_ENDPOINT_IPV6,
        ZipkinSpanRecord.LOCAL_ENDPOINT_PORT,
        ZipkinSpanRecord.REMOTE_ENDPOINT_SERVICE_NAME,
        ZipkinSpanRecord.REMOTE_ENDPOINT_IPV4,
        ZipkinSpanRecord.REMOTE_ENDPOINT_IPV6,
        ZipkinSpanRecord.REMOTE_ENDPOINT_PORT,
        ZipkinSpanRecord.TAGS,
        ZipkinSpanRecord.ANNOTATIONS
    );

    public BanyanDBZipkinQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<String> getServiceNames() throws IOException {
        MeasureQueryResponse resp =
            query(ZipkinServiceTraffic.INDEX_NAME,
                  SERVICE_TRAFFIC_TAGS,
                  Collections.emptySet(), new QueryBuilder<MeasureQuery>() {

                    @Override
                    protected void apply(MeasureQuery query) {
                        query.setLimit(QUERY_MAX_SIZE);
                    }
                }
            );
        final List<String> services = new ArrayList<>();
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            services.add(dataPoint.getTagValue(ZipkinServiceTraffic.SERVICE_NAME));
        }
        return services;
    }

    @Override
    public List<String> getRemoteServiceNames(final String serviceName) throws IOException {
        MeasureQueryResponse resp =
            query(ZipkinServiceRelationTraffic.INDEX_NAME,
                  REMOTE_SERVICE_TRAFFIC_TAGS,
                  Collections.emptySet(), new QueryBuilder<MeasureQuery>() {

                    @Override
                    protected void apply(MeasureQuery query) {
                        if (StringUtil.isNotEmpty(serviceName)) {
                            query.and(eq(ZipkinServiceRelationTraffic.SERVICE_NAME, serviceName));
                        }
                        query.setLimit(QUERY_MAX_SIZE);
                    }
                }
            );
        final List<String> remoteServices = new ArrayList<>();
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            remoteServices.add(dataPoint.getTagValue(ZipkinServiceRelationTraffic.REMOTE_SERVICE_NAME));
        }
        return remoteServices;
    }

    @Override
    public List<String> getSpanNames(final String serviceName) throws IOException {
        MeasureQueryResponse resp =
            query(ZipkinServiceSpanTraffic.INDEX_NAME,
                  SPAN_TRAFFIC_TAGS,
                  Collections.emptySet(), new QueryBuilder<MeasureQuery>() {

                    @Override
                    protected void apply(MeasureQuery query) {
                        if (StringUtil.isNotEmpty(serviceName)) {
                            query.and(eq(ZipkinServiceSpanTraffic.SERVICE_NAME, serviceName));
                        }
                        query.setLimit(QUERY_MAX_SIZE);
                    }
                }
            );
        final List<String> spanNames = new ArrayList<>();
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            spanNames.add(dataPoint.getTagValue(ZipkinServiceSpanTraffic.SPAN_NAME));
        }
        return spanNames;
    }

    @Override
    public List<Span> getTrace(final String traceId) throws IOException {
        StreamQueryResponse resp =
            query(ZipkinSpanRecord.INDEX_NAME, TRACE_TAGS,
                  new QueryBuilder<StreamQuery>() {

                      @Override
                      protected void apply(StreamQuery query) {
                          query.and(eq(ZipkinSpanRecord.TRACE_ID, traceId));
                          query.setLimit(QUERY_MAX_SIZE);
                      }
                  }
            );

        List<Span> trace = new ArrayList<>(resp.getElements().size());

        for (final RowEntity rowEntity : resp.getElements()) {
            ZipkinSpanRecord spanRecord = new ZipkinSpanRecord.Builder().storage2Entity(
                new BanyanDBConverter.StorageToStream(ZipkinSpanRecord.INDEX_NAME, rowEntity));
            trace.add(ZipkinSpanRecord.buildSpanFromRecord(spanRecord));
        }

        return trace;
    }

    @Override
    public List<List<Span>> getTraces(final QueryRequest request, Duration duration) throws IOException {
        final int tracesLimit = request.limit();
        int scrollLimit = 1000;
        long scrollEndTime = duration.getEndTimestamp();
        Set<String> traceIds = new HashSet<>();
        while (traceIds.size() < tracesLimit) {
            List<ZipkinSpanRecord> spans = getSpans(request, duration, scrollEndTime, scrollLimit);
            for (ZipkinSpanRecord span : spans) {
                traceIds.add(span.getTraceId());
                if (traceIds.size() >= tracesLimit) {
                    break;
                }
            }
            if (spans.size() < scrollLimit) {
                break;
            }
            scrollEndTime = spans.get(spans.size() - 1).getTimestampMillis();
        }

        return getTraces(traceIds);
    }

    private List<ZipkinSpanRecord> getSpans(final QueryRequest request,
                                    Duration duration,
                                    long scrollEndTime,
                                    int limit) throws IOException {
        final long startTimeMillis = duration.getStartTimestamp();
        TimestampRange tsRange = null;
        if (startTimeMillis > 0 && scrollEndTime > 0) {
            tsRange = new TimestampRange(startTimeMillis, scrollEndTime);
        }
        final QueryBuilder<StreamQuery> queryBuilder = new QueryBuilder<StreamQuery>() {

            @Override
            public void apply(final StreamQuery query) {
                if (!StringUtil.isEmpty(request.serviceName())) {
                    query.and(eq(ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME, request.serviceName()));
                }

                if (!StringUtil.isEmpty(request.remoteServiceName())) {
                    query.and(eq(ZipkinSpanRecord.REMOTE_ENDPOINT_SERVICE_NAME, request.remoteServiceName()));
                }

                if (!StringUtil.isEmpty(request.spanName())) {
                    query.and(eq(ZipkinSpanRecord.NAME, request.spanName()));
                }

                if (!CollectionUtils.isEmpty(request.annotationQuery())) {
                    for (Map.Entry<String, String> annotation : request.annotationQuery().entrySet()) {
                        if (annotation.getValue().isEmpty()) {
                            query.and(eq(ZipkinSpanRecord.QUERY, annotation.getKey()));
                        } else {
                            query.and(eq(ZipkinSpanRecord.QUERY, annotation.getKey() + "=" + annotation.getValue()));
                        }
                    }
                }

                if (request.minDuration() != null) {
                    query.and(gte(ZipkinSpanRecord.DURATION, request.minDuration()));
                }
                if (request.maxDuration() != null) {
                    query.and(lte(ZipkinSpanRecord.DURATION, request.maxDuration()));
                }
                query.setOrderBy(new StreamQuery.OrderBy(ZipkinSpanRecord.TIMESTAMP_MILLIS, AbstractQuery.Sort.DESC));
                query.setLimit(limit);
            }
        };
        StreamQueryResponse resp = query(ZipkinSpanRecord.INDEX_NAME, TRACE_TAGS, tsRange, queryBuilder);
        List<ZipkinSpanRecord> spans = new ArrayList<>(); //needs to keep order here
        for (final RowEntity rowEntity : resp.getElements()) {
            ZipkinSpanRecord spanRecord = new ZipkinSpanRecord.Builder().storage2Entity(
                new BanyanDBConverter.StorageToStream(ZipkinSpanRecord.INDEX_NAME, rowEntity));
            spans.add(spanRecord);
        }
        return spans;
    }

    @Override
    public List<List<Span>> getTraces(final Set<String> traceIds) throws IOException {
        if (CollectionUtils.isEmpty(traceIds)) {
            return Collections.EMPTY_LIST;
        }
        List<AbstractCriteria> conditions = new ArrayList<>(traceIds.size());
        StreamQueryResponse resp =
            query(ZipkinSpanRecord.INDEX_NAME, TRACE_TAGS,
                  new QueryBuilder<StreamQuery>() {

                      @Override
                      protected void apply(StreamQuery query) {
                          for (String traceId : traceIds) {
                              conditions.add(eq(ZipkinSpanRecord.TRACE_ID, traceId));
                          }
                          if (conditions.size() == 1) {
                              query.criteria(conditions.get(0));
                          } else if (conditions.size() > 1) {
                              query.criteria(or(conditions));
                          }
                          query.setOrderBy(
                              new StreamQuery.OrderBy(ZipkinSpanRecord.TIMESTAMP_MILLIS, AbstractQuery.Sort.DESC));
                          query.setLimit(QUERY_MAX_SIZE);
                      }
                  }
            );
        return buildTraces(resp);
    }

    private List<List<Span>> buildTraces(StreamQueryResponse resp) {
        Map<String, List<Span>> groupedByTraceId = new LinkedHashMap<String, List<Span>>();
        for (final RowEntity rowEntity : resp.getElements()) {
            ZipkinSpanRecord spanRecord = new ZipkinSpanRecord.Builder().storage2Entity(
                new BanyanDBConverter.StorageToStream(ZipkinSpanRecord.INDEX_NAME, rowEntity));
            Span span = ZipkinSpanRecord.buildSpanFromRecord(spanRecord);
            String traceId = span.traceId();
            groupedByTraceId.putIfAbsent(traceId, new ArrayList<>());
            groupedByTraceId.get(traceId).add(span);
        }
        return new ArrayList<>(groupedByTraceId.values());
    }
}
