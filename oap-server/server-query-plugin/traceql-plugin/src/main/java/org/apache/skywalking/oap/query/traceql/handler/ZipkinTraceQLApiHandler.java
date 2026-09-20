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

package org.apache.skywalking.oap.query.traceql.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.linecorp.armeria.common.HttpResponse;
import io.grafana.tempo.tempopb.TraceByIDResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.codec.DecoderException;
import org.apache.skywalking.oap.query.traceql.TraceQLConfig;
import org.apache.skywalking.oap.query.traceql.converter.OTLPConverter;
import org.apache.skywalking.oap.query.traceql.converter.ZipkinOTLPConverter;
import org.apache.skywalking.oap.query.traceql.converter.ZipkinSpanMatcher;
import org.apache.skywalking.oap.query.traceql.entity.OtlpTraceResponse;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;
import org.apache.skywalking.oap.query.traceql.entity.TagValuesResponse;
import org.apache.skywalking.oap.query.traceql.exception.IllegalExpressionException;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLParseResult;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParams;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParser;
import org.apache.skywalking.oap.query.zipkin.ZipkinQueryService;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.joda.time.DateTime;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

import static org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryVisitor.parseDuration;

public class ZipkinTraceQLApiHandler extends TraceQLApiHandler {
    private static final List<String> INTRINSIC_TAG_NAMES = Arrays.asList(
        NAME, STATUS, DURATION, SPAN_INTRINSIC_PREFIX + NAME, SPAN_INTRINSIC_PREFIX + STATUS, SPAN_INTRINSIC_PREFIX + DURATION);
    private final ZipkinQueryService zipkinQueryService;
    private final TagAutoCompleteQueryService tagAutoCompleteQueryService;
    private final TraceQLConfig traceQLConfig;
    private final Set<String> allowedTags;

    public ZipkinTraceQLApiHandler(ModuleManager moduleManager, TraceQLConfig config) {
        super();
        this.tagAutoCompleteQueryService = moduleManager.find(CoreModule.NAME)
                                                        .provider()
                                                        .getService(TagAutoCompleteQueryService.class);
        this.zipkinQueryService = new ZipkinQueryService(moduleManager);
        this.traceQLConfig = config;
        final String zipkinTagsConfig = config.getZipkinTracesListResultTags();
        final Set<String> allowedTagsInit;
        if (StringUtil.isNotBlank(zipkinTagsConfig)) {
            allowedTagsInit = Arrays.stream(zipkinTagsConfig.split(Const.COMMA))
                                    .map(String::trim)
                                    .filter(tag -> !tag.isEmpty())
                                    .collect(Collectors.toCollection(HashSet::new));
        } else {
            allowedTagsInit = new HashSet<>();
        }
        this.allowedTags = allowedTagsInit;
        // Add fixed tags
        this.allowedTags.add(SPAN_KIND);
        this.allowedTags.add(SERVICE_NAME);
    }

    @Override
    protected HttpResponse queryTraceImpl(String traceId,
                                          Optional<Long> start,
                                          Optional<Long> end,
                                          Optional<Boolean> coldStage,
                                          Optional<String> accept) throws IOException, DecoderException {
        List<Span> zipkinTrace = zipkinQueryService.getTraceById(traceId, null);

        if (zipkinTrace == null || zipkinTrace.isEmpty()) {
            return HttpResponse.of(com.linecorp.armeria.common.HttpStatus.NOT_FOUND);
        }

        // Convert Zipkin spans to Protobuf format
        TraceByIDResponse protoResponse = ZipkinOTLPConverter.convertToProtobuf(zipkinTrace);

        // Return based on Accept header
        if (accept.isPresent() && accept.get().contains("application/protobuf")) {
            return buildProtobufHttpResponse(protoResponse);
        } else {
            // Convert protobuf to JSON for default response
            return buildJsonHttpResponseFromProtobuf(protoResponse);
        }
    }

    @Override
    protected HttpResponse searchImpl(Optional<String> query,
                                      Optional<String> tags,
                                      Optional<String> minDuration,
                                      Optional<String> maxDuration,
                                      Optional<Integer> limit,
                                      Optional<Long> start,
                                      Optional<Long> end,
                                      Optional<Integer> spss,
                                      Optional<Boolean> coldStage) throws IOException {
        try {
            QueryRequest.Builder queryRequestBuilder = QueryRequest.newBuilder();

            // Set end timestamp (convert from seconds to milliseconds)
            long endTsMillis = end.isPresent() ? end.get() * 1000 : System.currentTimeMillis();
            queryRequestBuilder.endTs(endTsMillis);

            // Calculate lookback
            long lookbackMillis;
            if (start.isPresent()) {
                long startTsMillis = start.get() * 1000;
                lookbackMillis = endTsMillis - startTsMillis;
            } else {
                lookbackMillis = traceQLConfig.getLookback();
            }
            queryRequestBuilder.lookback(lookbackMillis);

            Duration duration = new Duration();
            duration.setStep(Step.SECOND);
            DateTime endTime = new DateTime(endTsMillis);
            DateTime startTime = endTime.minus(org.joda.time.Duration.millis(lookbackMillis));
            duration.setStart(startTime.toString("yyyy-MM-dd HHmmss"));
            duration.setEnd(endTime.toString("yyyy-MM-dd HHmmss"));
            if (query.isPresent() && !query.get().isEmpty()) {
                TraceQLParseResult parseResult = TraceQLQueryParser.extractParams(query.get());

                if (parseResult.hasError()) {
                    return badRequestResponse(parseResult.getErrorInfo());
                }

                TraceQLQueryParams traceQLParams = parseResult.getParams();
                applyTraceQL(traceQLParams, queryRequestBuilder);
                if (traceQLParams.getMinDuration() == null && minDuration.isPresent()) {
                    queryRequestBuilder.minDuration(parseDuration(minDuration.get()));
                }
                if (traceQLParams.getMaxDuration() == null && maxDuration.isPresent()) {
                    queryRequestBuilder.maxDuration(parseDuration(maxDuration.get()));
                }
            } else {
                if (tags.isPresent() && !tags.get().isEmpty()) {
                    applyTraceQL(parseTagsParameter(tags.get()), queryRequestBuilder);
                }

                if (minDuration.isPresent()) {
                    queryRequestBuilder.minDuration(parseDuration(minDuration.get()));
                }

                if (maxDuration.isPresent()) {
                    queryRequestBuilder.maxDuration(parseDuration(maxDuration.get()));
                }
            }

            queryRequestBuilder.limit(limit.orElse(20));
            QueryRequest queryRequest = queryRequestBuilder.build();
            List<List<zipkin2.Span>> traces = zipkinQueryService.getTraces(queryRequest, duration);
            SearchResponse response = ZipkinOTLPConverter.convertToSearchResponse(
                traces, allowedTags, new ZipkinSpanMatcher(queryRequest), spansPerSpanSet(spss));
            return successResponse(response);
        } catch (IllegalExpressionException | IllegalArgumentException e) {
            return badRequestResponse(e.getMessage());
        }
    }

    /**
     * TraceQL onto the Zipkin query: local and remote service, span name, the duration range, and the annotation
     * query over tags, where {@code status = error} is Zipkin's {@code error} tag.
     */
    private static void applyTraceQL(TraceQLQueryParams traceQLParams, QueryRequest.Builder queryRequestBuilder) {
        if (StringUtil.isNotBlank(traceQLParams.getKind())) {
            throw new IllegalArgumentException("kind is not supported on the Zipkin datasource: Zipkin spans carry no filterable kind");
        }
        if (StringUtil.isNotBlank(traceQLParams.getServiceInstance()) && !ALL.equals(traceQLParams.getServiceInstance())) {
            throw new IllegalArgumentException("resource.instance is not supported on the Zipkin datasource: Zipkin spans carry no instance");
        }
        if (StringUtil.isNotBlank(traceQLParams.getStatus()) && !ERROR.equalsIgnoreCase(traceQLParams.getStatus())) {
            throw new IllegalArgumentException(
                "status = " + traceQLParams.getStatus() + " is not supported on the Zipkin datasource: only status = error, Zipkin's error tag");
        }
        if (StringUtil.isNotBlank(traceQLParams.getServiceName())
            && !traceQLParams.getServiceName().equals(ALL)) {
            queryRequestBuilder.serviceName(traceQLParams.getServiceName());
        }
        if (StringUtil.isNotBlank(traceQLParams.getRemoteServiceName())
            && !traceQLParams.getRemoteServiceName().equals(ALL)) {
            queryRequestBuilder.remoteServiceName(traceQLParams.getRemoteServiceName());
        }
        if (StringUtil.isNotBlank(traceQLParams.getSpanName())
            && !traceQLParams.getSpanName().equals(ALL)) {
            queryRequestBuilder.spanName(traceQLParams.getSpanName());
        }
        if (traceQLParams.getMinDuration() != null) {
            queryRequestBuilder.minDuration(traceQLParams.getMinDuration());
        }
        if (traceQLParams.getMaxDuration() != null) {
            queryRequestBuilder.maxDuration(traceQLParams.getMaxDuration());
        }
        Map<String, String> annotationQuery = new HashMap<>();
        if (CollectionUtils.isNotEmpty(traceQLParams.getTags())) {
            annotationQuery.putAll(traceQLParams.flatTags());
        }
        if (StringUtil.isNotBlank(traceQLParams.getHttpStatusCode())) {
            annotationQuery.put(HTTP_STATUS_CODE, traceQLParams.getHttpStatusCode());
        }
        if (ERROR.equalsIgnoreCase(traceQLParams.getStatus())) {
            annotationQuery.put(ERROR, "");
        }
        if (CollectionUtils.isNotEmpty(annotationQuery)) {
            queryRequestBuilder.annotationQuery(annotationQuery);
        }
    }

    /**
     * Tempo's {@code q} on a tag lookup: the newest {@link #TAG_FILTER_SAMPLE_TRACES} traces matching the filter,
     * with the matcher that picks their matching spans.
     */
    private Sample sample(TraceQLQueryParams params, Optional<Long> start, Optional<Long> end) throws IOException {
        long endTsMillis = end.isPresent() ? end.get() * 1000 : System.currentTimeMillis();
        long lookbackMillis = start.isPresent() ? endTsMillis - start.get() * 1000 : traceQLConfig.getLookback();
        QueryRequest.Builder builder = QueryRequest.newBuilder().endTs(endTsMillis).lookback(lookbackMillis)
                                                   .limit(TAG_FILTER_SAMPLE_TRACES);
        applyTraceQL(params, builder);
        QueryRequest request = builder.build();
        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());
        return new Sample(zipkinQueryService.getTraces(request, duration), new ZipkinSpanMatcher(request));
    }

    private static final class Sample {
        private final List<List<Span>> traces;
        private final ZipkinSpanMatcher matcher;

        private Sample(List<List<Span>> traces, ZipkinSpanMatcher matcher) {
            this.traces = traces;
            this.matcher = matcher;
        }
    }

    @Override
    protected HttpResponse searchTagsImpl(Optional<String> scope,
                                          Optional<Integer> limit,
                                          Optional<Long> start,
                                          Optional<Long> end) throws IOException {
        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());
        return tagNames(scope, limit, requested -> namesOf(requested, duration));
    }

    @Override
    protected HttpResponse searchTagsV2Impl(Optional<String> q,
                                            Optional<String> scope,
                                            Optional<Integer> limit,
                                            Optional<Long> start,
                                            Optional<Long> end) throws IOException {
        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());
        TraceQLParseResult filter = parseFilter(q);
        if (filter != null && filter.hasError()) {
            return badRequestResponse(filter.getErrorInfo());
        }
        if (filter == null || !hasFilter(filter.getParams())) {
            return tagNamesV2(scope, limit, requested -> namesOf(requested, duration));
        }
        try {
            Sample sample = sample(filter.getParams(), start, end);
            return tagNamesV2(scope, limit, requested -> SCOPE_INTRINSIC.equals(requested)
                ? INTRINSIC_TAG_NAMES : ZipkinOTLPConverter.tagNames(sample.traces, sample.matcher, requested));
        } catch (IllegalArgumentException e) {
            return badRequestResponse(e.getMessage());
        }
    }

    /**
     * Zipkin has no span kind and no ok status to filter on, so the intrinsic scope lists name, status and duration.
     */
    private List<String> namesOf(String scope, Duration duration) throws IOException {
        switch (scope) {
            case SCOPE_RESOURCE:
                //for Grafana variables, tempo only supports label query in variables setting.
                return Arrays.asList(SERVICE, REMOTE_SERVICE);
            case SCOPE_SPAN:
                return new ArrayList<>(tagAutoCompleteQueryService.queryTagAutocompleteKeys(TagType.ZIPKIN, duration));
            default:
                return INTRINSIC_TAG_NAMES;
        }
    }

    @Override
    protected HttpResponse searchTagValuesImpl(String tagName,
                                               Optional<String> query,
                                               Optional<Integer> limit,
                                               Optional<Long> start,
                                               Optional<Long> end) throws IOException {
        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());
        String tag = normalizeTagName(tagName);
        TraceQLParseResult filter = parseFilter(query);
        if (filter != null && filter.hasError()) {
            return badRequestResponse(filter.getErrorInfo());
        }
        // Anything narrower than a service name is answered from a sample of matching traces; the service-scoped
        // catalogs below are exact and complete, so they keep answering the common service-only case.
        if (filter != null && hasFilter(filter.getParams()) && !isEnumIntrinsic(tag)
            && !(onlyServiceName(filter.getParams()) && (NAME.equals(tag) || RESOURCE_REMOTE_SERVICE.equals(tag)))) {
            try {
                Sample sample = sample(filter.getParams(), start, end);
                return successResponse(stringValues(ZipkinOTLPConverter.tagValues(sample.traces, sample.matcher, tag), limit));
            } catch (IllegalArgumentException e) {
                return badRequestResponse(e.getMessage());
            }
        }

        if (tag.equals(RESOURCE_SERVICE_NAME) || tag.equals(RESOURCE_SERVICE)) {
            return successResponse(stringValues(zipkinQueryService.getServiceNames(), limit));
        } else if (tag.equals(STATUS)) {
            // zipkin doesn't have an ok status query
            return successResponse(stringValues(Collections.singletonList(ERROR), limit));
        } else if (tag.equals(DURATION)) {
            // A range intrinsic has no value list, Tempo answers an empty one too.
            return successResponse(new TagValuesResponse());
        } else if (tag.equals(KIND)) {
            return badRequestResponse("kind is not supported on the Zipkin datasource: Zipkin spans carry no filterable kind");
        } else if (tag.equals(RESOURCE_INSTANCE) || tag.equals(SCOPE_RESOURCE + "." + SERVICE_INSTANCE_ID)) {
            return badRequestResponse("resource.instance is not supported on the Zipkin datasource: Zipkin spans carry no instance");
        } else if (tag.startsWith(SPAN_PREFIX)) {
            return successResponse(stringValues(tagAutoCompleteQueryService.queryTagAutocompleteValues(
                TagType.ZIPKIN, tag.substring(SPAN_PREFIX.length()), duration), limit));
        }
        if (tag.equals(NAME) || tag.equals(RESOURCE_REMOTE_SERVICE)) {
            if (query.isPresent() && !query.get().isEmpty()) {
                TraceQLParseResult parseResult = TraceQLQueryParser.extractParams(query.get());
                if (parseResult.hasError()) {
                    return badRequestResponse(parseResult.getErrorInfo());
                }
                TraceQLQueryParams traceQLParams = parseResult.getParams();
                if (StringUtil.isNotBlank(traceQLParams.getServiceName()) && !traceQLParams.getServiceName().equals(ALL)) {
                    if (tag.equals(NAME)) {
                        return successResponse(stringValues(zipkinQueryService.getSpanNames(traceQLParams.getServiceName()), limit));
                    }
                    return successResponse(stringValues(
                        zipkinQueryService.getRemoteServiceNames(traceQLParams.getServiceName()), limit));
                }
            }
            // Empty when no service is named: Grafana asks for these on every visit to the query page.
            return successResponse(new TagValuesResponse());
        }
        return badRequestResponse("Unsupported tag value query.");
    }

    /**
     * Convert Protobuf TraceByIDResponse to JSON and build HTTP response.
     */
    private HttpResponse buildJsonHttpResponseFromProtobuf(TraceByIDResponse protoResponse) throws
        JsonProcessingException {
        OtlpTraceResponse jsonResponse = OTLPConverter.convertProtobufToJson(protoResponse, OTLPConverter.TraceType.ZIPKIN);
        return successResponse(jsonResponse);
    }
}
