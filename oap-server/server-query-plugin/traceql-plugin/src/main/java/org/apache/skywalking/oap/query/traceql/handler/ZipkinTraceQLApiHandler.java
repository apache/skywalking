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
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import io.grafana.tempo.tempopb.TraceByIDResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
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
import org.apache.skywalking.oap.query.zipkin.ZipkinQueryConfig;
import org.apache.skywalking.oap.query.zipkin.ZipkinQueryModule;
import org.apache.skywalking.oap.query.zipkin.handler.ZipkinQueryHandler;
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

@Slf4j
public class ZipkinTraceQLApiHandler extends TraceQLApiHandler {
    private final ZipkinQueryHandler zipkinQueryHandler;
    private final ZipkinQueryConfig zipkinQueryConfig;
    private final TagAutoCompleteQueryService tagAutoCompleteQueryService;

    public ZipkinTraceQLApiHandler(ModuleManager moduleManager) {
        super();
        this.tagAutoCompleteQueryService = moduleManager.find(CoreModule.NAME)
                                                        .provider()
                                                        .getService(TagAutoCompleteQueryService.class);
        // Get ZipkinQueryHandler from ZipkinQueryModule service
        this.zipkinQueryHandler = moduleManager.find(ZipkinQueryModule.NAME)
                                               .provider()
                                               .getService(ZipkinQueryHandler.class);
        this.zipkinQueryConfig = zipkinQueryHandler.getConfig();
    }

    @Override
    protected HttpResponse queryTraceImpl(String traceId,
                                          Optional<String> accept) throws IOException, DecoderException {
        List<Span> zipkinTrace = zipkinQueryHandler.getTraceById(traceId);

        // Step 1: Convert Zipkin spans to Protobuf (primary format) using converter
        TraceByIDResponse protoResponse = ZipkinOTLPConverter.convertToProtobuf(zipkinTrace);

        // Step 2: Return based on Accept header
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
                lookbackMillis = zipkinQueryConfig.getLookback();
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
                    Set<String> tagKeys = tagAutoCompleteQueryService.queryTagAutocompleteKeys(
                        TagType.ZIPKIN,
                        duration
                    );
                    if (tagKeys.contains("error")) {
                        annotationQuery.put("error", "");
                    } else if (tagKeys.contains("otel.status_code")) {
                        annotationQuery.put("otel.status_code", traceQLParams.getStatus());
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
            log.info(query.get());
            log.info(queryRequest.toString());
            List<List<zipkin2.Span>> traces = zipkinQueryHandler.getTraces(queryRequest, duration);
            SearchResponse response = ZipkinOTLPConverter.convertToSearchResponse(traces);
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
        Duration duration = buildDuration(start, end);

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
        Duration duration = buildDuration(start, end);

        TagNamesV2Response.Scope spanScope = new TagNamesV2Response.Scope("span");
        //for Grafana variables, tempo only supports label query in variables setting.
        TagNamesV2Response.Scope resourceScope = new TagNamesV2Response.Scope("resource");

        Set<String> tagKeys = tagAutoCompleteQueryService.queryTagAutocompleteKeys(
            TagType.ZIPKIN,
            duration
        );
        resourceScope.getTags().add("service");
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
        Duration duration = buildDuration(start, end);

        if (tagName.equals("resource.service.name") || tagName.equals("resource.service")) {
            List<String> serviceNames = zipkinQueryHandler.getServiceNames();
            TagValuesResponse serviceNameRsp = new TagValuesResponse();
            for (String serviceName : serviceNames) {
                TagValuesResponse.TagValue tagValue = new TagValuesResponse.TagValue("string", serviceName);
                serviceNameRsp.getTagValues().add(tagValue);
            }
            return successResponse(serviceNameRsp);
        } else if (tagName.equals("status")) {
            TagValuesResponse serviceNameRsp = new TagValuesResponse();
            TagValuesResponse.TagValue tagValue1 = new TagValuesResponse.TagValue("string", "STATUS_CODE_OK");
            TagValuesResponse.TagValue tagValue2 = new TagValuesResponse.TagValue("string", "STATUS_CODE_ERROR");
            serviceNameRsp.getTagValues().add(tagValue1);
            serviceNameRsp.getTagValues().add(tagValue2);
            return successResponse(serviceNameRsp);
        } else if (tagName.startsWith("span.")) {
            String actualTagKey = tagName.substring(5);
            TagValuesResponse response = new TagValuesResponse();

            Set<String> tagValues = tagAutoCompleteQueryService.queryTagAutocompleteValues(
                TagType.ZIPKIN,
                actualTagKey,
                duration
            );

            for (String value : tagValues) {
                TagValuesResponse.TagValue tagValue = new TagValuesResponse.TagValue("string", value);
                response.getTagValues().add(tagValue);
            }
            return successResponse(response);
        }
        if (query.isPresent() && !query.get().isEmpty()) {
            TraceQLParseResult parseResult = TraceQLQueryParser.extractParams(query.get());
            if (parseResult.hasError()) {
                return badRequestResponse(parseResult.getErrorInfo());
            }
            TraceQLQueryParams traceQLParams = parseResult.getParams();
            if (tagName.equals("name")) {
                TagValuesResponse serviceNameRsp = new TagValuesResponse();
                if (StringUtil.isNotBlank(traceQLParams.getServiceName())) {
                    List<String> spanNames = zipkinQueryHandler.getSpanNames(traceQLParams.getServiceName());
                    for (String spanName : spanNames) {
                        TagValuesResponse.TagValue tagValue = new TagValuesResponse.TagValue("string", spanName);
                        serviceNameRsp.getTagValues().add(tagValue);
                    }
                }
                return successResponse(serviceNameRsp);
            }
        }
        return badRequestResponse("Unsupported tag value query.");
    }

    /**
     * Build HTTP response with Protobuf content.
     */
    private HttpResponse buildProtobufHttpResponse(TraceByIDResponse protoResponse) {
        byte[] protoBytes = protoResponse.toByteArray();
        return HttpResponse.of(
            ResponseHeaders.builder(HttpStatus.OK)
                           .contentType(MediaType.parse("application/protobuf"))
                           .build(),
            HttpData.wrap(protoBytes)
        );
    }

    /**
     * Convert Protobuf TraceByIDResponse to JSON and build HTTP response.
     */
    private HttpResponse buildJsonHttpResponseFromProtobuf(TraceByIDResponse protoResponse) throws
        JsonProcessingException {
        OtlpTraceResponse jsonResponse = ZipkinOTLPConverter.convertProtobufToJson(protoResponse);
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
                    if ("service.name".equals(key)) {
                        queryRequestBuilder.serviceName(value);
                    } else if ("span.name".equals(key)) {
                        queryRequestBuilder.spanName(value);
                    }
                }
            }
        }
    }

    /**
     * Build Duration object from start and end timestamps.
     */
    private Duration buildDuration(Optional<Long> start, Optional<Long> end) {
        Duration duration = new Duration();

        long endTime;
        long startTime;

        if (end.isPresent()) {
            endTime = end.get() * 1000;
        } else {
            endTime = System.currentTimeMillis();
        }

        if (start.isPresent()) {
            startTime = start.get() * 1000;
        } else {
            startTime = endTime - zipkinQueryConfig.getLookback();
        }

        duration.setStart(new DateTime(startTime).toString("yyyy-MM-dd HHmmss"));
        duration.setEnd(new DateTime(endTime).toString("yyyy-MM-dd HHmmss"));
        duration.setStep(Step.SECOND);

        return duration;
    }
}
