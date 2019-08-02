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
import java.util.*;
import lombok.Setter;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.terms.*;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.metrics.min.Min;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

import static org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpanRecord.*;

public class ZipkinTraceQueryEsDAO extends EsDAO implements ITraceQueryDAO {
    @Setter
    private ServiceInventoryCache serviceInventoryCache;

    public ZipkinTraceQueryEsDAO(
        ElasticSearchClient client) {
        super(client);
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration,
        String endpointName, int serviceId, int serviceInstanceId, int endpointId, String traceId, int limit, int from,
        TraceState traceState, QueryOrder queryOrder) throws IOException {

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
            boolQueryBuilder.must().add(rangeQueryBuilder);
        }
        if (!Strings.isNullOrEmpty(endpointName)) {
            mustQueryList.add(QueryBuilders.matchPhraseQuery(ENDPOINT_NAME, endpointName));
        }
        if (serviceId != 0) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SERVICE_ID, serviceId));
        }
        if (serviceInstanceId != 0) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (endpointId != 0) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(ENDPOINT_ID, endpointId));
        }
        if (!Strings.isNullOrEmpty(traceId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(TRACE_ID, traceId));
        }
        switch (traceState) {
            case ERROR:
                mustQueryList.add(QueryBuilders.matchQuery(IS_ERROR, BooleanUtils.TRUE));
                break;
            case SUCCESS:
                mustQueryList.add(QueryBuilders.matchQuery(IS_ERROR, BooleanUtils.FALSE));
                break;
        }

        TermsAggregationBuilder builder = AggregationBuilders.terms(TRACE_ID).field(TRACE_ID).size(limit)
            .subAggregation(
                AggregationBuilders.max(LATENCY).field(LATENCY)
            )
            .subAggregation(
                AggregationBuilders.min(START_TIME).field(START_TIME)
            );
        switch (queryOrder) {
            case BY_START_TIME:
                builder.order(BucketOrder.aggregation(START_TIME, false));
                break;
            case BY_DURATION:
                builder.order(BucketOrder.aggregation(LATENCY, false));
                break;
        }
        sourceBuilder.aggregation(builder);

        SearchResponse response = getClient().search(ZipkinSpanRecord.INDEX_NAME, sourceBuilder);

        TraceBrief traceBrief = new TraceBrief();

        Terms terms = response.getAggregations().get(TRACE_ID);

        for (Terms.Bucket termsBucket : terms.getBuckets()) {
            BasicTrace basicTrace = new BasicTrace();

            basicTrace.setSegmentId(termsBucket.getKeyAsString());
            Min startTime = termsBucket.getAggregations().get(START_TIME);
            Max latency = termsBucket.getAggregations().get(LATENCY);
            basicTrace.setStart(String.valueOf((long)startTime.getValue()));
            basicTrace.getEndpointNames().add("");
            basicTrace.setDuration((int)latency.getValue());
            basicTrace.setError(false);
            basicTrace.getTraceIds().add(termsBucket.getKeyAsString());
            traceBrief.getTraces().add(basicTrace);
        }

        return traceBrief;
    }

    @Override public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        return Collections.emptyList();
    }

    @Override public List<org.apache.skywalking.oap.server.core.query.entity.Span> doFlexibleTraceQuery(
        String traceId) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.termQuery(TRACE_ID, traceId));
        sourceBuilder.sort(START_TIME, SortOrder.ASC);
        sourceBuilder.size(1000);

        SearchResponse response = getClient().search(ZipkinSpanRecord.INDEX_NAME, sourceBuilder);

        List<org.apache.skywalking.oap.server.core.query.entity.Span> spanList = new ArrayList<>();

        for (SearchHit searchHit : response.getHits().getHits()) {
            int serviceId = ((Number)searchHit.getSourceAsMap().get(SERVICE_ID)).intValue();
            String dataBinaryBase64 = (String)searchHit.getSourceAsMap().get(SegmentRecord.DATA_BINARY);
            Span span = SpanBytesDecoder.PROTO3.decodeOne(Base64.getDecoder().decode(dataBinaryBase64));

            org.apache.skywalking.oap.server.core.query.entity.Span swSpan = new org.apache.skywalking.oap.server.core.query.entity.Span();

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
            if (serviceId != Const.NONE) {
                swSpan.setServiceCode(serviceInventoryCache.get(serviceId).getName());
            }
            swSpan.setSpanId(0);
            swSpan.setParentSpanId(-1);
            swSpan.setSegmentSpanId(span.id());
            swSpan.setSegmentId(span.id());
            //Add default Span.kind to SERVER in case span.kind is null
            Span.Kind kind = span.kind() == null ? Span.Kind.SERVER : span.kind();
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
