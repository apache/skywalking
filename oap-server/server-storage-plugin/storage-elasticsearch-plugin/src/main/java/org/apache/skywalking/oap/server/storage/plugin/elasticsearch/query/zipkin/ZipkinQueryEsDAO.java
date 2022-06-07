package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.zipkin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.BucketOrder;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.TermsAggregationBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

public class ZipkinQueryEsDAO extends EsDAO implements IZipkinQueryDAO {
    private final int nameQueryMaxSize = Integer.MAX_VALUE;

    public ZipkinQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<String> getServiceNames(final long startTimeMillis, final long endTimeMillis) {
        return queryNames(ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME, startTimeMillis, endTimeMillis, null);
    }

    @Override
    public List<String> getRemoteServiceNames(final long startTimeMillis,
                                              final long endTimeMillis,
                                              final String serviceName) {
        return queryNames(ZipkinSpanRecord.REMOTE_ENDPOINT_SERVICE_NAME, startTimeMillis, endTimeMillis, serviceName);
    }

    @Override
    public List<String> getSpanNames(final long startTimeMillis, final long endTimeMillis, final String serviceName) {
        return queryNames(ZipkinSpanRecord.NAME, startTimeMillis, endTimeMillis, serviceName);
    }

    @Override
    public List<Span> getTrace(final String traceId) {
        String index = IndexController.LogicIndicesRegister.getPhysicalTableName(ZipkinSpanRecord.INDEX_NAME);
        BoolQueryBuilder query = Query.bool().must(Query.term(ZipkinSpanRecord.TRACE_ID, traceId));
        SearchBuilder search = Search.builder().query(query);
        SearchResponse response = getClient().search(index, search.build());
        return buildSingleTrace(response);
    }

    @Override
    public List<List<Span>> getTraces(final QueryRequest request) {
        final long startTimeMillis = request.endTs() - request.lookback();
        final long endTimeMillis = request.endTs();
        String index = IndexController.LogicIndicesRegister.getPhysicalTableName(ZipkinSpanRecord.INDEX_NAME);
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
        SearchResponse traceIdResponse = getClient().search(index, search.build());
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
        SearchBuilder search = Search.builder().query(query).size(5000); //max span size for 1 scroll
        final SearchParams params = new SearchParams().scroll(SCROLL_CONTEXT_RETENTION);
        SearchResponse response = getClient().search(index, search.build(), params);
        String scrollId = response.getScrollId();

        Map<String, List<Span>> groupedByTraceId = new LinkedHashMap<String, List<Span>>();
        try {
            while (response.getHits().getHits().size() != 0) {
                buildTraces(response, groupedByTraceId);
                response = getClient().scroll(SCROLL_CONTEXT_RETENTION, scrollId);
            }
        } finally {
            getClient().deleteScrollContextQuietly(scrollId);
        }
        return new ArrayList<>(groupedByTraceId.values());
    }

    private List<String> queryNames(String aggregationFiled,
                                    final long startTimeMillis,
                                    final long endTimeMillis,
                                    String serviceName) {
        List<String> names = new ArrayList<>();

        String index = IndexController.LogicIndicesRegister.getPhysicalTableName(ZipkinSpanRecord.INDEX_NAME);
        BoolQueryBuilder query = Query.bool().must(Query.range(ZipkinSpanRecord.TIMESTAMP_MILLIS)
                                                        .gte(startTimeMillis)
                                                        .lte(endTimeMillis));
        if (!StringUtil.isEmpty(serviceName)) {
            query.must(Query.term(ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME, serviceName));
        }
        SearchBuilder search = Search.builder().query(query);
        search.aggregation(Aggregation.terms(aggregationFiled)
                                      .field(aggregationFiled).size(nameQueryMaxSize));
        SearchResponse response = getClient().search(index, search.build());
        Map<String, Object> terms =
            (Map<String, Object>) response.getAggregations().get(aggregationFiled);
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) terms.get("buckets");
        for (Map<String, Object> bucket : buckets) {
            String name = (String) bucket.get("key");
            if (bucket.get("key") == null) {
                continue;
            }
            names.add(name);
        }
        return names;
    }

    private List<Span> buildSingleTrace(SearchResponse response) {
        List<Span> trace = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSource();
            ZipkinSpanRecord record = new ZipkinSpanRecord.Builder().storage2Entity(
                new HashMapConverter.ToEntity(sourceAsMap));
            trace.add(buildSpanFromRecord(record));
        }
        return trace;
    }

    private void buildTraces(SearchResponse response, Map<String, List<Span>> groupedByTraceId) {
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSource();
            ZipkinSpanRecord record = new ZipkinSpanRecord.Builder().storage2Entity(
                new HashMapConverter.ToEntity(sourceAsMap));
            Span span = buildSpanFromRecord(record);
            String traceId = span.traceId();
            groupedByTraceId.putIfAbsent(traceId, new ArrayList<>());
            groupedByTraceId.get(traceId).add(span);
        }
    }

    private Span buildSpanFromRecord(ZipkinSpanRecord record) {
        Span.Builder span = Span.newBuilder();
        span.traceId(record.getTraceId());
        span.id(record.getSpanId());
        span.parentId(record.getParentId());
        span.kind(Span.Kind.valueOf(record.getKind()));
        span.timestamp(record.getTimestamp());
        span.duration(record.getDuration());
        span.name(record.getName());
        //Build localEndpoint
        Endpoint.Builder localEndpoint = Endpoint.newBuilder();
        localEndpoint.serviceName(record.getLocalEndpointServiceName());
        if (!StringUtil.isEmpty(record.getLocalEndpointIPV4())) {
            localEndpoint.parseIp(record.getLocalEndpointIPV4());
        } else {
            localEndpoint.parseIp(record.getLocalEndpointIPV6());
        }
        localEndpoint.port(record.getLocalEndpointPort());
        span.localEndpoint(localEndpoint.build());
        //Build remoteEndpoint
        Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();
        remoteEndpoint.serviceName(record.getRemoteEndpointServiceName());
        if (!StringUtil.isEmpty(record.getLocalEndpointIPV4())) {
            remoteEndpoint.parseIp(record.getRemoteEndpointIPV4());
        } else {
            remoteEndpoint.parseIp(record.getRemoteEndpointIPV6());
        }
        remoteEndpoint.port(record.getRemoteEndpointPort());
        span.remoteEndpoint(remoteEndpoint.build());

        //Build tags
        JsonObject tagsJson = record.getTags();
        if (tagsJson != null) {
            for (Map.Entry<String, JsonElement> tag : tagsJson.entrySet()) {
                span.putTag(tag.getKey(), tag.getValue().getAsString());
            }
        }
        //Build annotation
        JsonObject annotationJson = record.getAnnotations();
        if (annotationJson != null) {
            for (Map.Entry<String, JsonElement> annotation : annotationJson.entrySet()) {
                span.addAnnotation(Long.parseLong(annotation.getKey()), annotation.getValue().getAsString());
            }
        }
        return span.build();
    }
}
