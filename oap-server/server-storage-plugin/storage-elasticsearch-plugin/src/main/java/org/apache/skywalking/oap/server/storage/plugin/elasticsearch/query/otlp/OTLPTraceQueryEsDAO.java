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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.otlp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
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
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchScroller;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.RoutingUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeRangeIndexNameGenerator;

/**
 * Reads {@code otlp_span} on Elasticsearch: a terms aggregation on {@code trace_id} selects the traces, then the
 * spans of the selected traces are fetched and grouped by trace, the shape {@code ZipkinQueryEsDAO} uses. The
 * catalogs ignore the time range, as the Zipkin catalogs do on this storage.
 */
public class OTLPTraceQueryEsDAO extends EsDAO implements IOTLPTraceQueryDAO {
    private static final int NAME_QUERY_MAX_SIZE = 10000;
    private static final int SCROLLING_BATCH_SIZE = 5000;

    public OTLPTraceQueryEsDAO(final ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<String> getServiceNames(@Nullable final Duration duration) {
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(OTLPServiceTraffic.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(OTLPServiceTraffic.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, OTLPServiceTraffic.INDEX_NAME));
        }
        final SearchBuilder search = Search.builder().query(query).size(SCROLLING_BATCH_SIZE);
        final var scroller = ElasticSearchScroller
            .<String>builder()
            .client(getClient())
            .search(search.build())
            .index(index)
            .resultConverter(hit -> new OTLPServiceTraffic.Builder().storage2Entity(
                new ElasticSearchConverter.ToEntity(OTLPServiceTraffic.INDEX_NAME, hit.getSource())).getServiceName())
            .build();
        return scroller.scroll();
    }

    @Override
    public List<String> getSpanNames(final String serviceName, @Nullable final Duration duration) {
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(OTLPServiceSpanTraffic.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool().must(Query.term(OTLPServiceSpanTraffic.SERVICE_NAME, serviceName));
        if (IndexController.LogicIndicesRegister.isMergedTable(OTLPServiceSpanTraffic.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, OTLPServiceSpanTraffic.INDEX_NAME));
        }
        final SearchBuilder search = Search.builder().query(query).size(NAME_QUERY_MAX_SIZE);
        final SearchResponse response = getClient().search(index, search.build());
        final List<String> spanNames = new ArrayList<>();
        for (final SearchHit searchHit : response.getHits()) {
            spanNames.add(new OTLPServiceSpanTraffic.Builder().storage2Entity(
                new ElasticSearchConverter.ToEntity(OTLPServiceSpanTraffic.INDEX_NAME, searchHit.getSource())).getSpanName());
        }
        return spanNames;
    }

    @Override
    public List<String> getPeerServiceNames(final String serviceName, @Nullable final Duration duration) {
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(OTLPServiceRelationTraffic.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool().must(Query.term(OTLPServiceRelationTraffic.SERVICE_NAME, serviceName));
        if (IndexController.LogicIndicesRegister.isMergedTable(OTLPServiceRelationTraffic.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, OTLPServiceRelationTraffic.INDEX_NAME));
        }
        final SearchBuilder search = Search.builder().query(query).size(NAME_QUERY_MAX_SIZE);
        final SearchResponse response = getClient().search(index, search.build());
        final List<String> peerServices = new ArrayList<>();
        for (final SearchHit searchHit : response.getHits()) {
            peerServices.add(new OTLPServiceRelationTraffic.Builder().storage2Entity(
                new ElasticSearchConverter.ToEntity(OTLPServiceRelationTraffic.INDEX_NAME, searchHit.getSource())).getPeerService());
        }
        return peerServices;
    }

    @Override
    public List<SpanWrapper> queryTraceById(final String traceId, @Nullable final Duration duration) {
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(OTLPSpanRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool().must(Query.term(OTLPSpanRecord.TRACE_ID, traceId));
        final SearchBuilder search = Search.builder().query(query).size(SCROLLING_BATCH_SIZE);
        final SearchParams params = new SearchParams();
        RoutingUtils.addRoutingValueToSearchParam(params, traceId);
        final var scroller = ElasticSearchScroller
            .<SpanWrapper>builder()
            .client(getClient())
            .search(search.build())
            .index(index)
            .params(params)
            .resultConverter(searchHit -> toSpanWrapper(searchHit.getSource()))
            .build();
        return scrollDebuggable(scroller, index, params);
    }

    @Override
    public List<List<SpanWrapper>> queryTraces(final OTLPTraceQueryCondition condition) {
        final Duration duration = condition.getQueryDuration();
        final BoolQueryBuilder query = Query.bool();
        if (StringUtil.isNotEmpty(condition.getTraceId())) {
            query.must(Query.term(OTLPSpanRecord.TRACE_ID, condition.getTraceId()));
        }
        if (duration != null && duration.getStartTimestamp() > 0 && duration.getEndTimestamp() > 0) {
            query.must(Query.range(OTLPSpanRecord.START_TIME)
                            .gte(duration.getStartTimestamp())
                            .lte(duration.getEndTimestamp()));
        }
        if (StringUtil.isNotEmpty(condition.getServiceName())) {
            query.must(Query.term(OTLPSpanRecord.SERVICE_NAME, condition.getServiceName()));
        }
        if (StringUtil.isNotEmpty(condition.getServiceInstance())) {
            query.must(Query.term(OTLPSpanRecord.SERVICE_INSTANCE, condition.getServiceInstance()));
        }
        if (StringUtil.isNotEmpty(condition.getScopeName())) {
            query.must(Query.term(OTLPSpanRecord.SCOPE_NAME, condition.getScopeName()));
        }
        if (StringUtil.isNotEmpty(condition.getSpanName())) {
            query.must(Query.term(OTLPSpanRecord.NAME, condition.getSpanName()));
        }
        if (StringUtil.isNotEmpty(condition.getPeerService())) {
            query.must(Query.term(OTLPSpanRecord.PEER_SERVICE, condition.getPeerService()));
        }
        if (condition.getKind() != null) {
            query.must(Query.term(OTLPSpanRecord.KIND, condition.getKind()));
        }
        if (condition.getStatusCode() != null) {
            query.must(Query.term(OTLPSpanRecord.STATUS_CODE, condition.getStatusCode()));
        }
        if (condition.getMinDurationNanos() > 0) {
            query.must(Query.range(OTLPSpanRecord.DURATION).gte(condition.getMinDurationNanos()));
        }
        if (condition.getMaxDurationNanos() > 0) {
            query.must(Query.range(OTLPSpanRecord.DURATION).lte(condition.getMaxDurationNanos()));
        }
        if (CollectionUtils.isNotEmpty(condition.getTags())) {
            for (final Tag tag : condition.getTags()) {
                query.must(Query.term(OTLPSpanRecord.TAGS, tag.getKey() + "=" + tag.getValue()));
            }
        }

        // One bucket per trace, ordered by the trace's earliest start or longest span, sized to the page.
        final boolean byDuration = condition.getQueryOrder() == QueryOrder.BY_DURATION;
        final String orderAggregation = byDuration ? OTLPSpanRecord.DURATION : OTLPSpanRecord.START_TIME;
        final TermsAggregationBuilder traceIdAggregation =
            Aggregation.terms(OTLPSpanRecord.TRACE_ID)
                       .field(OTLPSpanRecord.TRACE_ID)
                       .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                       .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST)
                       .size(condition.getLimit())
                       .subAggregation(byDuration
                                           ? Aggregation.max(orderAggregation).field(OTLPSpanRecord.DURATION)
                                           : Aggregation.min(orderAggregation).field(OTLPSpanRecord.START_TIME))
                       .order(BucketOrder.aggregation(orderAggregation, false));
        final SearchBuilder search = Search.builder().query(query).aggregation(traceIdAggregation);
        final SearchResponse traceIdResponse;
        if (duration != null && duration.getStartTimestamp() > 0 && duration.getEndTimestamp() > 0) {
            traceIdResponse = searchDebuggable(new TimeRangeIndexNameGenerator(
                IndexController.LogicIndicesRegister.getPhysicalTableName(OTLPSpanRecord.INDEX_NAME),
                TimeBucket.getRecordTimeBucket(duration.getStartTimestamp()),
                TimeBucket.getRecordTimeBucket(duration.getEndTimestamp())
            ), search.build());
        } else {
            traceIdResponse = searchDebuggable(
                IndexController.LogicIndicesRegister.getPhysicalTableName(OTLPSpanRecord.INDEX_NAME), search.build());
        }
        final List<String> traceIds = new ArrayList<>();
        if (Objects.nonNull(traceIdResponse.getAggregations())) {
            final Map<String, Object> idTerms =
                (Map<String, Object>) traceIdResponse.getAggregations().get(OTLPSpanRecord.TRACE_ID);
            final List<Map<String, Object>> buckets =
                (List<Map<String, Object>>) idTerms.get("buckets");
            for (final Map<String, Object> idBucket : buckets) {
                traceIds.add((String) idBucket.get("key"));
            }
        }
        return fetchTraces(traceIds);
    }

    /**
     * The spans of the given traces, grouped in the order the ids arrive in, which is the order the aggregation
     * ranked them.
     */
    private List<List<SpanWrapper>> fetchTraces(final List<String> traceIds) {
        if (traceIds.isEmpty()) {
            return new ArrayList<>();
        }
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(OTLPSpanRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool().must(Query.terms(OTLPSpanRecord.TRACE_ID, traceIds));
        final SearchBuilder search = Search.builder().query(query).sort(OTLPSpanRecord.START_TIME, Sort.Order.DESC)
                                           .size(SCROLLING_BATCH_SIZE);
        final SearchParams params = new SearchParams().scroll(SCROLL_CONTEXT_RETENTION);
        RoutingUtils.addRoutingValuesToSearchParam(params, new HashSet<>(traceIds));
        SearchResponse response = getClient().search(index, search.build(), params);
        final Set<String> scrollIds = new HashSet<>();
        final Map<String, List<SpanWrapper>> groupedByTraceId = new LinkedHashMap<>();
        for (final String traceId : traceIds) {
            groupedByTraceId.put(traceId, new ArrayList<>());
        }
        try {
            while (response.getHits().getHits().size() != 0) {
                final String scrollId = response.getScrollId();
                scrollIds.add(scrollId);
                for (final SearchHit searchHit : response.getHits()) {
                    final Map<String, Object> source = searchHit.getSource();
                    final String traceId = (String) source.get(OTLPSpanRecord.TRACE_ID);
                    groupedByTraceId.computeIfAbsent(traceId, k -> new ArrayList<>()).add(toSpanWrapper(source));
                }
                if (response.getHits().getHits().size() < SCROLLING_BATCH_SIZE) {
                    break;
                }
                response = getClient().scroll(SCROLL_CONTEXT_RETENTION, scrollId);
            }
        } finally {
            scrollIds.forEach(getClient()::deleteScrollContextQuietly);
        }
        final List<List<SpanWrapper>> traces = new ArrayList<>(groupedByTraceId.size());
        for (final List<SpanWrapper> trace : groupedByTraceId.values()) {
            if (!trace.isEmpty()) {
                traces.add(trace);
            }
        }
        return traces;
    }

    private static SpanWrapper toSpanWrapper(final Map<String, Object> source) {
        return new OTLPSpanRecord.Builder()
            .storage2Entity(new ElasticSearchConverter.ToEntity(OTLPSpanRecord.INDEX_NAME, source))
            .getSpanWrapper();
    }
}
