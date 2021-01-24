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

package org.apache.skywalking.oap.server.storage.plugin.jaeger.elasticsearch;

import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import io.jaegertracing.api_v2.Model;
import java.io.IOException;
import java.time.Instant;
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
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpanRecord;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.metrics.min.Min;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import static org.apache.skywalking.oap.server.core.analysis.record.Record.TIME_BUCKET;
import static org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpanRecord.ENDPOINT_ID;
import static org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpanRecord.ENDPOINT_NAME;
import static org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpanRecord.END_TIME;
import static org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpanRecord.IS_ERROR;
import static org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpanRecord.LATENCY;
import static org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpanRecord.SERVICE_ID;
import static org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpanRecord.SERVICE_INSTANCE_ID;
import static org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpanRecord.START_TIME;
import static org.apache.skywalking.oap.server.storage.plugin.jaeger.JaegerSpanRecord.TRACE_ID;

public class JaegerTraceQueryEsDAO extends EsDAO implements ITraceQueryDAO {

    public JaegerTraceQueryEsDAO(ElasticSearchClient client) {
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
            boolQueryBuilder.must().add(rangeQueryBuilder);
        }
        if (!Strings.isNullOrEmpty(endpointName)) {
            mustQueryList.add(QueryBuilders.matchPhraseQuery(ENDPOINT_NAME, endpointName));
        }
        if (StringUtil.isNotEmpty(serviceId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SERVICE_ID, serviceId));
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (!Strings.isNullOrEmpty(endpointId)) {
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

        TermsAggregationBuilder builder = AggregationBuilders.terms(TRACE_ID)
                                                             .field(TRACE_ID)
                                                             .size(limit)
                                                             .subAggregation(AggregationBuilders.max(LATENCY)
                                                                                                .field(LATENCY))
                                                             .subAggregation(AggregationBuilders.min(START_TIME)
                                                                                                .field(START_TIME));
        switch (queryOrder) {
            case BY_START_TIME:
                builder.order(BucketOrder.aggregation(START_TIME, false));
                break;
            case BY_DURATION:
                builder.order(BucketOrder.aggregation(LATENCY, false));
                break;
        }
        sourceBuilder.aggregation(builder);

        SearchResponse response = getClient().search(JaegerSpanRecord.INDEX_NAME, sourceBuilder);

        TraceBrief traceBrief = new TraceBrief();

        Terms terms = response.getAggregations().get(TRACE_ID);

        for (Terms.Bucket termsBucket : terms.getBuckets()) {
            BasicTrace basicTrace = new BasicTrace();

            basicTrace.setSegmentId(termsBucket.getKeyAsString());
            Min startTime = termsBucket.getAggregations().get(START_TIME);
            Max latency = termsBucket.getAggregations().get(LATENCY);
            basicTrace.setStart(String.valueOf((long) startTime.getValue()));
            basicTrace.getEndpointNames().add("");
            basicTrace.setDuration((int) latency.getValue());
            basicTrace.setError(false);
            basicTrace.getTraceIds().add(termsBucket.getKeyAsString());
            traceBrief.getTraces().add(basicTrace);
        }

        return traceBrief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.termQuery(TRACE_ID, traceId));
        sourceBuilder.sort(START_TIME, SortOrder.ASC);
        sourceBuilder.size(1000);

        SearchResponse response = getClient().search(JaegerSpanRecord.INDEX_NAME, sourceBuilder);

        List<Span> spanList = new ArrayList<>();

        for (SearchHit searchHit : response.getHits().getHits()) {
            String serviceId = (String) searchHit.getSourceAsMap().get(SERVICE_ID);
            long startTime = ((Number) searchHit.getSourceAsMap().get(START_TIME)).longValue();
            long endTime = ((Number) searchHit.getSourceAsMap().get(END_TIME)).longValue();
            String dataBinaryBase64 = (String) searchHit.getSourceAsMap().get(SegmentRecord.DATA_BINARY);

            Model.Span jaegerSpan = Model.Span.newBuilder()
                                              .mergeFrom(Base64.getDecoder().decode(dataBinaryBase64))
                                              .build();

            Span swSpan = new Span();

            swSpan.setTraceId(format(jaegerSpan.getTraceId()));
            swSpan.setEndpointName(jaegerSpan.getOperationName());
            swSpan.setStartTime(startTime);
            swSpan.setEndTime(endTime);
            jaegerSpan.getTagsList().forEach(keyValue -> {
                String key = keyValue.getKey();
                Model.ValueType valueVType = keyValue.getVType();
                switch (valueVType) {
                    case STRING:
                        swSpan.getTags().add(new KeyValue(key, keyValue.getVStr()));
                        break;
                    case INT64:
                        swSpan.getTags().add(new KeyValue(key, keyValue.getVInt64() + ""));
                        break;
                    case BOOL:
                        swSpan.getTags().add(new KeyValue(key, keyValue.getVBool() + ""));
                        break;
                    case FLOAT64:
                        swSpan.getTags().add(new KeyValue(key, keyValue.getVFloat64() + ""));
                        break;
                }
                swSpan.setType("Local");
                if ("span.kind".equals(key)) {
                    String kind = keyValue.getVStr();
                    if ("server".equals(kind) || "consumer".equals(kind)) {
                        swSpan.setType("Entry");
                    } else if ("client".equals(kind) || "producer".equals(kind)) {
                        swSpan.setType("Exit");
                    }
                }
            });
            jaegerSpan.getLogsList().forEach(log -> {
                LogEntity entity = new LogEntity();
                boolean hasTimestamp = log.hasTimestamp();
                if (hasTimestamp) {
                    long time = Instant.ofEpochSecond(log.getTimestamp().getSeconds(), log.getTimestamp().getNanos())
                                       .toEpochMilli();
                    entity.setTime(time);
                }
                log.getFieldsList().forEach(field -> {
                    String key = field.getKey();
                    Model.ValueType valueVType = field.getVType();
                    switch (valueVType) {
                        case STRING:
                            entity.getData().add(new KeyValue(key, field.getVStr()));
                            break;
                        case INT64:
                            entity.getData().add(new KeyValue(key, field.getVInt64() + ""));
                            break;
                        case BOOL:
                            entity.getData().add(new KeyValue(key, field.getVBool() + ""));
                            break;
                        case FLOAT64:
                            entity.getData().add(new KeyValue(key, field.getVFloat64() + ""));
                            break;
                    }
                });

                swSpan.getLogs().add(entity);
            });

            final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
                serviceId);

            swSpan.setServiceCode(serviceIDDefinition.getName());
            swSpan.setSpanId(0);
            swSpan.setParentSpanId(-1);
            String spanId = id(format(jaegerSpan.getTraceId()), format(jaegerSpan.getSpanId()));
            swSpan.setSegmentSpanId(spanId);
            swSpan.setSegmentId(spanId);

            List<Model.SpanRef> spanReferencesList = jaegerSpan.getReferencesList();
            if (spanReferencesList.size() > 0) {
                spanReferencesList.forEach(jaegerRef -> {
                    Ref ref = new Ref();
                    ref.setTraceId(format(jaegerRef.getTraceId()));
                    String parentId = id(format(jaegerRef.getTraceId()), format(jaegerRef.getSpanId()));
                    ref.setParentSegmentId(parentId);
                    ref.setType(RefType.CROSS_PROCESS);
                    ref.setParentSpanId(0);

                    swSpan.getRefs().add(ref);
                    swSpan.setSegmentParentSpanId(parentId);
                });
            } else {
                swSpan.setRoot(true);
                swSpan.setSegmentParentSpanId("");
            }
            spanList.add(swSpan);
        }
        return spanList;
    }

    private String id(String traceId, String spanId) {
        return traceId + "_" + spanId;
    }

    private String format(ByteString bytes) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(bytes.toByteArray());
    }
}
