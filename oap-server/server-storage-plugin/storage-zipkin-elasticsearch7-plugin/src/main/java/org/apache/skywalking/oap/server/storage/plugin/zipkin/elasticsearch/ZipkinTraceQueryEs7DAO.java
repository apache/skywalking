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

package org.apache.skywalking.oap.server.storage.plugin.zipkin.elasticsearch;

import com.google.common.base.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.LogEntity;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Ref;
import org.apache.skywalking.oap.server.core.query.type.RefType;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

import static org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord.ENDPOINT_ID;
import static org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord.ENDPOINT_NAME;
import static org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord.IS_ERROR;
import static org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord.LATENCY;
import static org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord.SERVICE_ID;
import static org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord.SERVICE_INSTANCE_ID;
import static org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord.START_TIME;
import static org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord.TAGS;
import static org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord.TIME_BUCKET;
import static org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord.TRACE_ID;

public class ZipkinTraceQueryEs7DAO extends EsDAO implements ITraceQueryDAO {

    public ZipkinTraceQueryEs7DAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB,
                                       long endSecondTB,
                                       long minDuration,
                                       long maxDuration,
                                       String endpointName,
                                       String serviceId,
                                       String serviceInstanceId,
                                       String endpointId,
                                       String traceId,
                                       int limit,
                                       int from,
                                       TraceState traceState,
                                       QueryOrder queryOrder,
                                       final List<Tag> tags) throws IOException {

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        List<QueryBuilder> mustQueryList = boolQueryBuilder.must();

        if (startSecondTB != 0 && endSecondTB != 0) {
            mustQueryList.add(QueryBuilders.rangeQuery(TIME_BUCKET).gte(startSecondTB).lte(endSecondTB));
        }

        if (minDuration != 0 || maxDuration != 0) {
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(LATENCY);
            if (minDuration != 0) {
                rangeQueryBuilder.gte(minDuration);
            }
            if (maxDuration != 0) {
                rangeQueryBuilder.lte(maxDuration);
            }
            mustQueryList.add(rangeQueryBuilder);
        }
        if (!Strings.isNullOrEmpty(endpointName)) {
            mustQueryList.add(QueryBuilders.matchPhraseQuery(ENDPOINT_NAME, endpointName));
        }
        if (StringUtil.isNotEmpty(serviceId)) {
            mustQueryList.add(QueryBuilders.termQuery(SERVICE_ID, serviceId));
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            mustQueryList.add(QueryBuilders.termQuery(SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (!Strings.isNullOrEmpty(endpointId)) {
            mustQueryList.add(QueryBuilders.termQuery(ENDPOINT_ID, endpointId));
        }
        if (!Strings.isNullOrEmpty(traceId)) {
            mustQueryList.add(QueryBuilders.termQuery(TRACE_ID, traceId));
        }
        switch (traceState) {
            case ERROR:
                mustQueryList.add(QueryBuilders.matchQuery(IS_ERROR, BooleanUtils.TRUE));
                break;
            case SUCCESS:
                mustQueryList.add(QueryBuilders.matchQuery(IS_ERROR, BooleanUtils.FALSE));
                break;
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            BoolQueryBuilder tagMatchQuery = QueryBuilders.boolQuery();
            tags.forEach(tag -> {
                tagMatchQuery.must(QueryBuilders.termQuery(TAGS, tag.toString()));
            });
            mustQueryList.add(tagMatchQuery);
        }

        switch (queryOrder) {
            case BY_START_TIME:
                sourceBuilder.sort(START_TIME, SortOrder.DESC);
                break;
            case BY_DURATION:
                sourceBuilder.sort(LATENCY, SortOrder.DESC);
                break;
        }
        sourceBuilder.size(limit);
        sourceBuilder.from(from);

        SearchResponse response = getClient().search(ZipkinSpanRecord.INDEX_NAME, sourceBuilder);

        TraceBrief traceBrief = new TraceBrief();
        traceBrief.setTotal((int) response.getHits().getTotalHits().value);

        for (SearchHit searchHit : response.getHits().getHits()) {
            BasicTrace basicTrace = new BasicTrace();

            final ZipkinSpanRecord zipkinSpanRecord = new ZipkinSpanRecord.Builder().storage2Entity(
                    searchHit.getSourceAsMap());

            basicTrace.setSegmentId(zipkinSpanRecord.getSpanId());
            basicTrace.setStart(String.valueOf((long) zipkinSpanRecord.getStartTime()));
            // Show trace id as the name
            basicTrace.getEndpointNames().add(zipkinSpanRecord.getEndpointName());
            basicTrace.setDuration(zipkinSpanRecord.getLatency());
            basicTrace.setError(BooleanUtils.valueToBoolean(zipkinSpanRecord.getIsError()));
            basicTrace.getTraceIds().add(zipkinSpanRecord.getTraceId());
            traceBrief.getTraces().add(basicTrace);
        }

        return traceBrief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<org.apache.skywalking.oap.server.core.query.type.Span> doFlexibleTraceQuery(
            String traceId) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.termQuery(TRACE_ID, traceId));
        sourceBuilder.sort(START_TIME, SortOrder.ASC);
        sourceBuilder.size(1000);

        SearchResponse response = getClient().search(ZipkinSpanRecord.INDEX_NAME, sourceBuilder);

        List<org.apache.skywalking.oap.server.core.query.type.Span> spanList = new ArrayList<>();

        for (SearchHit searchHit : response.getHits().getHits()) {
            String serviceId = (String) searchHit.getSourceAsMap().get(SERVICE_ID);
            String serviceInstanceId = (String) searchHit.getSourceAsMap().get(SERVICE_INSTANCE_ID);
            String dataBinaryBase64 = (String) searchHit.getSourceAsMap().get(SegmentRecord.DATA_BINARY);
            Span span = SpanBytesDecoder.PROTO3.decodeOne(Base64.getDecoder().decode(dataBinaryBase64));

            org.apache.skywalking.oap.server.core.query.type.Span swSpan = new org.apache.skywalking.oap.server.core.query.type.Span();

            swSpan.setTraceId(span.traceId());
            swSpan.setEndpointName(span.name());
            swSpan.setStartTime(span.timestamp() / 1000);
            swSpan.setEndTime(swSpan.getStartTime() + span.durationAsLong() / 1000);
            span.tags().forEach((key, value) -> {
                swSpan.getTags().add(new KeyValue(key, value));
            });
            span.annotations().forEach(annotation -> {
                LogEntity entity = new LogEntity();
                entity.setTime(annotation.timestamp() / 1000);
                entity.getData().add(new KeyValue("annotation", annotation.value()));
                swSpan.getLogs().add(entity);
            });
            if (StringUtil.isNotEmpty(serviceId)) {
                final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
                        serviceId);
                swSpan.setServiceCode(serviceIDDefinition.getName());
            }
            if (StringUtil.isNotEmpty(serviceInstanceId)) {
                final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID.analysisId(
                        serviceInstanceId);
                swSpan.setServiceInstanceName(instanceIDDefinition.getName());
            }
            swSpan.setSpanId(0);
            swSpan.setParentSpanId(-1);
            swSpan.setSegmentSpanId(span.id());
            swSpan.setSegmentId(span.id());
            Span.Kind kind = span.kind();
            switch (kind) {
                case CLIENT:
                case PRODUCER:
                    swSpan.setType("Entry");
                    break;
                case SERVER:
                case CONSUMER:
                    swSpan.setType("Exit");
                    break;
                default:
                    swSpan.setType("Local");

            }

            if (StringUtil.isEmpty(span.parentId())) {
                swSpan.setRoot(true);
                swSpan.setSegmentParentSpanId("");
            } else {
                Ref ref = new Ref();
                ref.setTraceId(span.traceId());
                ref.setParentSegmentId(span.parentId());
                ref.setType(RefType.CROSS_PROCESS);
                ref.setParentSpanId(0);

                swSpan.getRefs().add(ref);
                swSpan.setSegmentParentSpanId(span.parentId());
            }
            spanList.add(swSpan);
        }
        return spanList;
    }
}
