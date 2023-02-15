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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.zipkin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.BucketOrder;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.TermsAggregationBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceRelationTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceSpanTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.RoutingUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeRangeIndexNameGenerator;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

public class ZipkinQueryEsDAO extends EsDAO implements IZipkinQueryDAO {
    private final static int NAME_QUERY_MAX_SIZE = 10000;
    private final static int SCROLLING_BATCH_SIZE = 5000;

    public ZipkinQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<String> getServiceNames() {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ZipkinServiceTraffic.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(ZipkinServiceTraffic.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, ZipkinServiceTraffic.INDEX_NAME));
        }
        final SearchBuilder search = Search.builder().query(query).size(SCROLLING_BATCH_SIZE);
        final SearchParams params = new SearchParams().scroll(SCROLL_CONTEXT_RETENTION);
        final List<String> services = new ArrayList<>();

        SearchResponse response = getClient().search(index, search.build(), params);
        final Set<String> scrollIds = new HashSet<>();
        try {
            while (response.getHits().getHits().size() != 0) {
                String scrollId = response.getScrollId();
                scrollIds.add(scrollId);
                for (SearchHit searchHit : response.getHits()) {
                    Map<String, Object> sourceAsMap = searchHit.getSource();
                    ZipkinServiceTraffic record = new ZipkinServiceTraffic.Builder().storage2Entity(
                        new ElasticSearchConverter.ToEntity(ZipkinServiceTraffic.INDEX_NAME, sourceAsMap));
                    services.add(record.getServiceName());
                }
                if (services.size() < SCROLLING_BATCH_SIZE) {
                    break;
                }
                response = getClient().scroll(SCROLL_CONTEXT_RETENTION, scrollId);
            }
        } finally {
            scrollIds.forEach(getClient()::deleteScrollContextQuietly);
        }
        return services;
    }

    @Override
    public List<String> getRemoteServiceNames(final String serviceName) {
        String index = IndexController.LogicIndicesRegister.getPhysicalTableName(
            ZipkinServiceRelationTraffic.INDEX_NAME);
        BoolQueryBuilder query = Query.bool().must(Query.term(ZipkinServiceRelationTraffic.SERVICE_NAME, serviceName));
        if (IndexController.LogicIndicesRegister.isMergedTable(ZipkinServiceRelationTraffic.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, ZipkinServiceRelationTraffic.INDEX_NAME));
        }
        SearchBuilder search = Search.builder().query(query).size(NAME_QUERY_MAX_SIZE);
        SearchResponse response = getClient().search(index, search.build());
        List<String> remoteServices = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSource();
            ZipkinServiceRelationTraffic record = new ZipkinServiceRelationTraffic.Builder().storage2Entity(
                new ElasticSearchConverter.ToEntity(ZipkinServiceRelationTraffic.INDEX_NAME, sourceAsMap));
            remoteServices.add(record.getRemoteServiceName());
        }
        return remoteServices;
    }

    @Override
    public List<String> getSpanNames(final String serviceName) {
        String index = IndexController.LogicIndicesRegister.getPhysicalTableName(ZipkinServiceSpanTraffic.INDEX_NAME);
        BoolQueryBuilder query = Query.bool().must(Query.term(ZipkinServiceSpanTraffic.SERVICE_NAME, serviceName));
        if (IndexController.LogicIndicesRegister.isMergedTable(ZipkinServiceSpanTraffic.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, ZipkinServiceSpanTraffic.INDEX_NAME));
        }
        SearchBuilder search = Search.builder().query(query).size(NAME_QUERY_MAX_SIZE);
        SearchResponse response = getClient().search(index, search.build());
        List<String> spanNames = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSource();
            ZipkinServiceSpanTraffic record = new ZipkinServiceSpanTraffic.Builder().storage2Entity(
                new ElasticSearchConverter.ToEntity(ZipkinServiceSpanTraffic.INDEX_NAME, sourceAsMap));
            spanNames.add(record.getSpanName());
        }
        return spanNames;
    }

    @Override
    public List<Span> getTrace(final String traceId) {
        String index = IndexController.LogicIndicesRegister.getPhysicalTableName(ZipkinSpanRecord.INDEX_NAME);
        BoolQueryBuilder query = Query.bool().must(Query.term(ZipkinSpanRecord.TRACE_ID, traceId));
        SearchBuilder search = Search.builder().query(query).size(SCROLLING_BATCH_SIZE);
        final SearchParams params = new SearchParams().scroll(SCROLL_CONTEXT_RETENTION);
        RoutingUtils.addRoutingValueToSearchParam(params, traceId);
        SearchResponse response = getClient().search(index, search.build(), params);
        final Set<String> scrollIds = new HashSet<>();
        List<Span> trace = new ArrayList<>();
        try {
            while (response.getHits().getHits().size() != 0) {
                String scrollId = response.getScrollId();
                scrollIds.add(scrollId);
                for (SearchHit searchHit : response.getHits()) {
                    Map<String, Object> sourceAsMap = searchHit.getSource();
                    ZipkinSpanRecord record = new ZipkinSpanRecord.Builder().storage2Entity(
                        new ElasticSearchConverter.ToEntity(ZipkinSpanRecord.INDEX_NAME, sourceAsMap));
                    trace.add(ZipkinSpanRecord.buildSpanFromRecord(record));
                }
                if (response.getHits().getHits().size() < SCROLLING_BATCH_SIZE) {
                    break;
                }
                response = getClient().scroll(SCROLL_CONTEXT_RETENTION, scrollId);
            }
        } finally {
            scrollIds.forEach(getClient()::deleteScrollContextQuietly);
        }
        return trace;
    }

    @Override
    public List<List<Span>> getTraces(final QueryRequest request, Duration duration) {
        final long startTimeMillis = duration.getStartTimestamp();
        final long endTimeMillis = duration.getEndTimestamp();
        BoolQueryBuilder query = Query.bool();
        if (startTimeMillis > 0 && endTimeMillis > 0) {
            query.must(Query.range(ZipkinSpanRecord.TIMESTAMP_MILLIS)
                            .gte(startTimeMillis)
                            .lte(endTimeMillis));
        }
        if (!StringUtil.isEmpty(request.serviceName())) {
            query.must(Query.term(ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME, request.serviceName()));
        }

        if (!StringUtil.isEmpty(request.remoteServiceName())) {
            query.must(Query.term(ZipkinSpanRecord.REMOTE_ENDPOINT_SERVICE_NAME, request.remoteServiceName()));
        }

        if (!StringUtil.isEmpty(request.spanName())) {
            query.must(Query.term(ZipkinSpanRecord.NAME, request.spanName()));
        }

        if (!CollectionUtils.isEmpty(request.annotationQuery())) {
            for (Map.Entry<String, String> annotation : request.annotationQuery().entrySet()) {
                if (annotation.getValue().isEmpty()) {
                    query.must(Query.term(ZipkinSpanRecord.QUERY, annotation.getKey()));
                } else {
                    query.must(Query.term(ZipkinSpanRecord.QUERY, annotation.getKey() + "=" + annotation.getValue()));
                }
            }
        }

        if (request.minDuration() != null) {
            query.must(Query.range(ZipkinSpanRecord.DURATION).gte(request.minDuration()));
        }
        if (request.maxDuration() != null) {
            query.must(Query.range(ZipkinSpanRecord.DURATION).lte(request.maxDuration()));
        }
        final TermsAggregationBuilder traceIdAggregation =
            Aggregation.terms(ZipkinSpanRecord.TRACE_ID)
                       .field(ZipkinSpanRecord.TRACE_ID)
                       .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                       .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST)
                       .size(request.limit()).subAggregation(Aggregation.min(ZipkinSpanRecord.TIMESTAMP_MILLIS)
                                                                        .field(ZipkinSpanRecord.TIMESTAMP_MILLIS))
                       .order(BucketOrder.aggregation(ZipkinSpanRecord.TIMESTAMP_MILLIS, false));

        SearchBuilder search = Search.builder().query(query).aggregation(traceIdAggregation);
        SearchResponse traceIdResponse = getClient().search(new TimeRangeIndexNameGenerator(
            IndexController.LogicIndicesRegister.getPhysicalTableName(ZipkinSpanRecord.INDEX_NAME),
            TimeBucket.getRecordTimeBucket(startTimeMillis),
            TimeBucket.getRecordTimeBucket(endTimeMillis)
        ), search.build());
        final Map<String, Object> idTerms =
            (Map<String, Object>) traceIdResponse.getAggregations().get(ZipkinSpanRecord.TRACE_ID);
        final List<Map<String, Object>> buckets =
            (List<Map<String, Object>>) idTerms.get("buckets");

        Set<String> traceIds = new HashSet<>();
        for (Map<String, Object> idBucket : buckets) {
            traceIds.add((String) idBucket.get("key"));
        }

        return getTraces(traceIds);
    }

    @Override
    public List<List<Span>> getTraces(final Set<String> traceIds) {
        String index = IndexController.LogicIndicesRegister.getPhysicalTableName(ZipkinSpanRecord.INDEX_NAME);
        BoolQueryBuilder query = Query.bool().must(Query.terms(ZipkinSpanRecord.TRACE_ID, new ArrayList<>(traceIds)));
        SearchBuilder search = Search.builder().query(query).sort(ZipkinSpanRecord.TIMESTAMP_MILLIS, Sort.Order.DESC)
                                     .size(SCROLLING_BATCH_SIZE); //max span size for 1 scroll
        final SearchParams params = new SearchParams().scroll(SCROLL_CONTEXT_RETENTION);
        RoutingUtils.addRoutingValuesToSearchParam(params, traceIds);

        SearchResponse response = getClient().search(index, search.build(), params);
        final Set<String> scrollIds = new HashSet<>();
        Map<String, List<Span>> groupedByTraceId = new LinkedHashMap<String, List<Span>>();
        try {
            while (response.getHits().getHits().size() != 0) {
                String scrollId = response.getScrollId();
                scrollIds.add(scrollId);
                buildTraces(response, groupedByTraceId);
                if (response.getHits().getHits().size() < SCROLLING_BATCH_SIZE) {
                    break;
                }
                response = getClient().scroll(SCROLL_CONTEXT_RETENTION, scrollId);
            }
        } finally {
            scrollIds.forEach(getClient()::deleteScrollContextQuietly);
        }
        return new ArrayList<>(groupedByTraceId.values());
    }

    private void buildTraces(SearchResponse response, Map<String, List<Span>> groupedByTraceId) {
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSource();
            ZipkinSpanRecord record = new ZipkinSpanRecord.Builder().storage2Entity(
                new ElasticSearchConverter.ToEntity(ZipkinSpanRecord.INDEX_NAME, sourceAsMap));
            Span span = ZipkinSpanRecord.buildSpanFromRecord(record);
            String traceId = span.traceId();
            groupedByTraceId.putIfAbsent(traceId, new ArrayList<>());
            groupedByTraceId.get(traceId).add(span);
        }
    }
}
