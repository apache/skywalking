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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.codec.DecoderException;
import org.apache.skywalking.oap.query.traceql.TraceQLConfig;
import org.apache.skywalking.oap.query.traceql.converter.OTLPConverter;
import org.apache.skywalking.oap.query.traceql.converter.ZipkinOTLPConverter;
import org.apache.skywalking.oap.query.traceql.entity.OtlpTraceResponse;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;
import org.apache.skywalking.oap.query.traceql.entity.TagNamesResponse;
import org.apache.skywalking.oap.query.traceql.entity.TagNamesV2Response;
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
        this.allowedTags = Arrays.stream(config.getZipkinTracesListResultTags().trim().split(Const.COMMA))
                                 .map(String::trim)
                                 .collect(Collectors.toSet());
        // Add fixed tags
        this.allowedTags.add(SPAN_KIND);
        this.allowedTags.add(SERVICE_NAME);
    }

    @Override
    protected HttpResponse queryTraceImpl(String traceId,
                                          Optional<Long> start,
                                          Optional<Long> end,
                                          Optional<String> accept) throws IOException, DecoderException {
        List<Span> zipkinTrace = zipkinQueryService.getTraceById(traceId);

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
                                      Optional<Integer> spss) throws IOException {
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

                // Apply TraceQL parameters
                if (StringUtil.isNotBlank(traceQLParams.getServiceName())) {
                    queryRequestBuilder.serviceName(traceQLParams.getServiceName());
                }
                if (StringUtil.isNotBlank(traceQLParams.getSpanName())) {
                    queryRequestBuilder.spanName(traceQLParams.getSpanName());
                }

                // Use duration from TraceQL
                if (traceQLParams.getMinDuration() != null) {
                    queryRequestBuilder.minDuration(traceQLParams.getMinDuration());
                } else if (minDuration.isPresent()) {
                    queryRequestBuilder.minDuration(parseDuration(minDuration.get()));
                }

                if (traceQLParams.getMaxDuration() != null) {
                    queryRequestBuilder.maxDuration(traceQLParams.getMaxDuration());
                } else if (maxDuration.isPresent()) {
                    queryRequestBuilder.maxDuration(parseDuration(maxDuration.get()));
                }

                Map<String, String> annotationQuery = new HashMap<>();
                if (CollectionUtils.isNotEmpty(traceQLParams.getTags())) {
                    annotationQuery.putAll(traceQLParams.getTags());
                }

                if (StringUtil.isNotBlank(traceQLParams.getStatus())) {
                    if (ERROR.equalsIgnoreCase(traceQLParams.getStatus())) {
                        annotationQuery.put(ERROR, "");
                    }
                }
                if (CollectionUtils.isNotEmpty(annotationQuery)) {
                    queryRequestBuilder.annotationQuery(annotationQuery);
                }
            } else {
                parseTagsParameter(tags, queryRequestBuilder);

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
            SearchResponse response = ZipkinOTLPConverter.convertToSearchResponse(traces, allowedTags);
            return successResponse(response);
        } catch (IllegalExpressionException | IllegalArgumentException e) {
            return badRequestResponse(e.getMessage());
        }
    }

    @Override
    protected HttpResponse searchTagsImpl(Optional<String> scope,
                                          Optional<Integer> limit,
                                          Optional<Long> start,
                                          Optional<Long> end) throws IOException {
        TagNamesResponse response = new TagNamesResponse();
        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());

        Set<String> tagKeys = tagAutoCompleteQueryService.queryTagAutocompleteKeys(
            TagType.ZIPKIN,
            duration
        );
        response.getTagNames().addAll(tagKeys);

        return successResponse(response);
    }

    @Override
    protected HttpResponse searchTagsV2Impl(Optional<String> q,
                                            Optional<String> scope,
                                            Optional<Integer> limit,
                                            Optional<Long> start,
                                            Optional<Long> end) throws IOException {
        TagNamesV2Response response = new TagNamesV2Response();
        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());

        TagNamesV2Response.Scope spanScope = new TagNamesV2Response.Scope(SCOPE_SPAN);
        //for Grafana variables, tempo only supports label query in variables setting.
        TagNamesV2Response.Scope resourceScope = new TagNamesV2Response.Scope(SCOPE_RESOURCE);

        Set<String> tagKeys = tagAutoCompleteQueryService.queryTagAutocompleteKeys(
            TagType.ZIPKIN,
            duration
        );
        resourceScope.getTags().add(SERVICE);
        response.getScopes().add(resourceScope);
        spanScope.getTags().addAll(tagKeys);
        response.getScopes().add(spanScope);

        return successResponse(response);
    }

    @Override
    protected HttpResponse searchTagValuesImpl(String tagName,
                                               Optional<String> query,
                                               Optional<Integer> limit,
                                               Optional<Long> start,
                                               Optional<Long> end) throws IOException {
        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());

        if (tagName.equals(RESOURCE_SERVICE_NAME) || tagName.equals(RESOURCE_SERVICE)) {
            List<String> serviceNames = zipkinQueryService.getServiceNames();
            TagValuesResponse serviceNameRsp = new TagValuesResponse();
            for (String serviceName : serviceNames) {
                TagValuesResponse.TagValue tagValue = new TagValuesResponse.TagValue(TYPE_STRING, serviceName);
                serviceNameRsp.getTagValues().add(tagValue);
            }
            return successResponse(serviceNameRsp);
        } else if (tagName.equals(STATUS)) {
            TagValuesResponse serviceNameRsp = new TagValuesResponse();
            // zipkin doesn't have an ok status query
            TagValuesResponse.TagValue tagValue = new TagValuesResponse.TagValue(TYPE_STRING, ERROR);
            serviceNameRsp.getTagValues().add(tagValue);
            return successResponse(serviceNameRsp);
        } else if (tagName.startsWith(SPAN_PREFIX)) {
            String actualTagKey = tagName.substring(SPAN_PREFIX.length());
            TagValuesResponse response = new TagValuesResponse();

            Set<String> tagValues = tagAutoCompleteQueryService.queryTagAutocompleteValues(
                TagType.ZIPKIN,
                actualTagKey,
                duration
            );

            for (String value : tagValues) {
                TagValuesResponse.TagValue tagValue = new TagValuesResponse.TagValue(TYPE_STRING, value);
                response.getTagValues().add(tagValue);
            }
            return successResponse(response);
        }
        if (tagName.equals(NAME)) {
            if (query.isPresent() && !query.get().isEmpty()) {
                TraceQLParseResult parseResult = TraceQLQueryParser.extractParams(query.get());
                if (parseResult.hasError()) {
                    return badRequestResponse(parseResult.getErrorInfo());
                }
                TraceQLQueryParams traceQLParams = parseResult.getParams();
                TagValuesResponse serviceNameRsp = new TagValuesResponse();
                if (StringUtil.isNotBlank(traceQLParams.getServiceName())) {
                    List<String> spanNames = zipkinQueryService.getSpanNames(traceQLParams.getServiceName());
                    for (String spanName : spanNames) {
                        TagValuesResponse.TagValue tagValue = new TagValuesResponse.TagValue(TYPE_STRING, spanName);
                        serviceNameRsp.getTagValues().add(tagValue);
                    }
                }
                return successResponse(serviceNameRsp);
            } else {
                // Return empty list if no query provide, to avoid error as Grafana query this every time when user enter the query page.
                return successResponse(new TagValuesResponse());
            }
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

    /**
     * Parse tags parameter and apply to QueryRequest.Builder.
     */
    private void parseTagsParameter(Optional<String> tags, QueryRequest.Builder queryRequestBuilder) {
        if (tags.isPresent() && !tags.get().isEmpty()) {
            String[] tagPairs = tags.get().split(" ");
            for (String tagPair : tagPairs) {
                String[] kv = tagPair.split(Const.EQUAL);
                if (kv.length == 2) {
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    if (SERVICE_NAME.equals(key)) {
                        queryRequestBuilder.serviceName(value);
                    } else if (SPAN_NAME.equals(key)) {
                        queryRequestBuilder.spanName(value);
                    }
                }
            }
        }
    }
}
