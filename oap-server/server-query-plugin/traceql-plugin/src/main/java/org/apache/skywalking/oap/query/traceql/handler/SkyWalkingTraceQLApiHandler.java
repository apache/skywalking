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
import com.linecorp.armeria.common.HttpStatus;
import io.grafana.tempo.tempopb.TraceByIDResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.codec.DecoderException;
import org.apache.skywalking.oap.query.traceql.TraceQLConfig;
import org.apache.skywalking.oap.query.traceql.converter.OTLPConverter;
import org.apache.skywalking.oap.query.traceql.converter.SkyWalkingOTLPConverter;
import org.apache.skywalking.oap.query.traceql.entity.OtlpTraceResponse;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;
import org.apache.skywalking.oap.query.traceql.entity.TagNamesResponse;
import org.apache.skywalking.oap.query.traceql.entity.TagNamesV2Response;
import org.apache.skywalking.oap.query.traceql.entity.TagValuesResponse;
import org.apache.skywalking.oap.query.traceql.exception.IllegalExpressionException;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLParseResult;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParams;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParser;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Trace;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TraceList;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * SkyWalking-native implementation of TraceQL API Handler.
 */
public class SkyWalkingTraceQLApiHandler extends TraceQLApiHandler {
    private final TraceQueryService traceQueryService;
    private final TagAutoCompleteQueryService tagAutoCompleteQueryService;
    private final MetadataQueryService metadataQueryService;
    private final TraceQLConfig traceQLConfig;
    private final Set<String> allowedTags;

    public SkyWalkingTraceQLApiHandler(ModuleManager moduleManager, TraceQLConfig config) {
        super();
        this.traceQueryService = moduleManager.find(CoreModule.NAME)
                                              .provider()
                                              .getService(TraceQueryService.class);
        this.tagAutoCompleteQueryService = moduleManager.find(CoreModule.NAME)
                                                        .provider()
                                                        .getService(TagAutoCompleteQueryService.class);
        this.metadataQueryService = moduleManager.find(CoreModule.NAME)
                                                 .provider()
                                                 .getService(MetadataQueryService.class);
        this.traceQLConfig = config;
        this.allowedTags = Arrays.stream(config.getSkywalkingTracesListResultTags().trim().split(Const.COMMA))
                                 .map(String::trim)
                                 .collect(Collectors.toSet());
        // Add fixed tags
        this.allowedTags.add(SPAN_KIND);
        this.allowedTags.add(SERVICE_NAME);
    }

    @Override
    protected HttpResponse queryTraceImpl(String traceId,
                                          Optional<String> accept) throws IOException, DecoderException {
        // Query SkyWalking trace by ID
        Trace swTrace = traceQueryService.queryTrace(SkyWalkingOTLPConverter.decodeTraceId(traceId), null);

        if (swTrace == null || swTrace.getSpans().isEmpty()) {
            return HttpResponse.of(HttpStatus.NOT_FOUND);
        }

        // Convert to Protobuf format first
        TraceByIDResponse protoResponse = SkyWalkingOTLPConverter.convertToProtobuf(traceId, swTrace);

        // Return based on Accept header
        if (accept.isPresent() && accept.get().contains("application/protobuf")) {
            return buildProtobufHttpResponse(protoResponse);
        } else {
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
        // Parse TraceQL query
        TraceQLParseResult parseResult = parseTraceQLQuery(query, tags);
        if (parseResult.hasError()) {
            return errorResponse(HttpStatus.BAD_REQUEST, parseResult.getErrorInfo());
        }

        TraceQLQueryParams queryParams = parseResult.getParams();

        // Build TraceQueryCondition
        TraceQueryCondition condition = new TraceQueryCondition();

        // Set time range
        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());
        condition.setQueryDuration(duration);

        // Set service ID if service name is provided
        if (StringUtil.isNotBlank(queryParams.getServiceName())) {
            String serviceId = IDManager.ServiceID.buildId(queryParams.getServiceName(), true);
            condition.setServiceId(serviceId);
        }

        // Set endpoint ID if span name is provided
        if (StringUtil.isNotBlank(queryParams.getSpanName()) && StringUtil.isNotBlank(condition.getServiceId())) {
            // Use IDManager to build endpoint ID
            condition.setEndpointId(
                IDManager.EndpointID.buildId(condition.getServiceId(), queryParams.getSpanName())
            );
        }

        // Set duration filters (convert from microseconds to milliseconds)
        if (queryParams.getMinDuration() != null) {
            condition.setMinTraceDuration((int) (queryParams.getMinDuration() / 1000));
        } else if (minDuration.isPresent()) {
            try {
                long durationMicros = parseDuration(minDuration.get());
                condition.setMinTraceDuration((int) (durationMicros / 1000));
            } catch (IllegalExpressionException e) {
                return badRequestResponse("Invalid minDuration format: " + e.getMessage());
            }
        }

        if (queryParams.getMaxDuration() != null) {
            condition.setMaxTraceDuration((int) (queryParams.getMaxDuration() / 1000));
        } else if (maxDuration.isPresent()) {
            try {
                long durationMicros = parseDuration(maxDuration.get());
                condition.setMaxTraceDuration((int) (durationMicros / 1000));
            } catch (IllegalExpressionException e) {
                return badRequestResponse("Invalid maxDuration format: " + e.getMessage());
            }
        }

        // Set trace state based on status, default ALL.
        condition.setTraceState(TraceState.ALL);
        if (StringUtil.isNotBlank(queryParams.getStatus())) {
            if (ERROR.equalsIgnoreCase(queryParams.getStatus())) {
                condition.setTraceState(TraceState.ERROR);
            } else if (OK.equalsIgnoreCase(queryParams.getStatus())) {
                condition.setTraceState(TraceState.SUCCESS);
            }
        }

        // Set tags
        if (queryParams.getTags() != null && !queryParams.getTags().isEmpty()) {
            List<Tag> tagList = new ArrayList<>();
            for (Map.Entry<String, String> entry : queryParams.getTags().entrySet()) {
                Tag tag = new Tag();
                tag.setKey(entry.getKey());
                tag.setValue(entry.getValue());
                tagList.add(tag);
            }
            condition.setTags(tagList);
        }

        // Set pagination
        Pagination pagination = new Pagination();
        pagination.setPageNum(1);
        pagination.setPageSize(limit.orElse(20));
        condition.setPaging(pagination);

        // Set query order (default: by start time descending)
        condition.setQueryOrder(QueryOrder.BY_START_TIME);

        // Query traces using TraceQueryService
        TraceList traceList = traceQueryService.queryTraces(condition);

        // Convert TraceList to SearchResponse
        SearchResponse response = SkyWalkingOTLPConverter.convertTraceListToSearchResponse(traceList, allowedTags);

        return successResponse(response);
    }

    @Override
    protected HttpResponse searchTagsImpl(Optional<String> scope,
                                          Optional<Integer> limit,
                                          Optional<Long> start,
                                          Optional<Long> end) throws IOException {
        // Get all tag keys for TRACE type (SkyWalking native traces)
        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());
        Set<String> tagKeys = tagAutoCompleteQueryService.queryTagAutocompleteKeys(TagType.TRACE, duration);

        TagNamesResponse response = new TagNamesResponse();
        response.setTagNames(new ArrayList<>(tagKeys));

        return successResponse(response);
    }

    @Override
    protected HttpResponse searchTagsV2Impl(Optional<String> q,
                                            Optional<String> scope,
                                            Optional<Integer> limit,
                                            Optional<Long> start,
                                            Optional<Long> end) throws IOException {
        // Get all tag keys for TRACE type (SkyWalking native traces)
        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());
        Set<String> tagKeys = tagAutoCompleteQueryService.queryTagAutocompleteKeys(TagType.TRACE, duration);

        TagNamesV2Response response = new TagNamesV2Response();
        TagNamesV2Response.Scope spanScope = new TagNamesV2Response.Scope();
        spanScope.setName(SCOPE_SPAN);
        spanScope.setTags(new ArrayList<>(tagKeys));
        //for Grafana variables, tempo only supports label query in variables setting.
        TagNamesV2Response.Scope resourceScope = new TagNamesV2Response.Scope(SCOPE_RESOURCE);
        resourceScope.getTags().add(SERVICE);
        List<TagNamesV2Response.Scope> scopes = new ArrayList<>();
        scopes.add(spanScope);
        response.setScopes(scopes);
        response.getScopes().add(resourceScope);
        return successResponse(response);
    }

    @Override
    protected HttpResponse searchTagValuesImpl(String tagName,
                                               Optional<String> query,
                                               Optional<Integer> limit,
                                               Optional<Long> start,
                                               Optional<Long> end) throws IOException {
        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());

        // Handle special tags: resource.service.name or resource.service
        if (tagName.equals(RESOURCE_SERVICE_NAME) || tagName.equals(RESOURCE_SERVICE)) {
            // Query service names from MetadataQueryService with Layer.GENERAL filter
            // Only GENERAL layer services use SkyWalking native protocol
            List<org.apache.skywalking.oap.server.core.query.type.Service> services =
                metadataQueryService.listServices(Layer.GENERAL.name(), null);

            TagValuesResponse response = new TagValuesResponse();
            for (org.apache.skywalking.oap.server.core.query.type.Service service : services) {
                response.getTagValues().add(new TagValuesResponse.TagValue(TYPE_STRING, service.getName()));
            }
            return successResponse(response);
        }

        // Handle status tag
        if (tagName.equals(STATUS)) {
            TagValuesResponse response = new TagValuesResponse();
            response.getTagValues().add(new TagValuesResponse.TagValue(TYPE_STRING, OK));
            response.getTagValues().add(new TagValuesResponse.TagValue(TYPE_STRING, ERROR));
            return successResponse(response);
        }

        // Handle span.* prefix tags
        if (tagName.startsWith(SPAN_PREFIX)) {
            String actualTagName = tagName.substring(SPAN_PREFIX.length());
            TagValuesResponse response = new TagValuesResponse();

            Set<String> tagValues = tagAutoCompleteQueryService.queryTagAutocompleteValues(
                TagType.TRACE,
                actualTagName,
                duration
            );

            for (String value : tagValues) {
                response.getTagValues().add(new TagValuesResponse.TagValue(TYPE_STRING, value));
            }
            return successResponse(response);
        }

        // Handle 'name' tag with TraceQL query filter
        if (tagName.equals(NAME)) {
            if (query.isPresent() && !query.get().isEmpty()) {
                TraceQLParseResult parseResult = TraceQLQueryParser.extractParams(query.get());
                if (parseResult.hasError()) {
                    return badRequestResponse(parseResult.getErrorInfo());
                }
                TraceQLQueryParams traceQLParams = parseResult.getParams();
                TagValuesResponse response = new TagValuesResponse();
                if (StringUtil.isNotBlank(traceQLParams.getServiceName())) {
                    String serviceId = IDManager.ServiceID.buildId(traceQLParams.getServiceName(), true);
                    List<Endpoint> endpoints = metadataQueryService.findEndpoint(
                        "",
                        serviceId,
                        limit.orElse(100),
                        duration
                    );

                    for (Endpoint endpoint : endpoints) {
                        response.getTagValues().add(new TagValuesResponse.TagValue(TYPE_STRING, endpoint.getName()));
                    }
                }
                return successResponse(response);
            } else {
                // Return empty list if no query provided, to avoid error as Grafana queries this every time when user enters the query page.
                return successResponse(new TagValuesResponse());
            }
        }
        return badRequestResponse("Unsupported tag value query.");
    }

    /**
     * Parse TraceQL query string.
     */
    private TraceQLParseResult parseTraceQLQuery(Optional<String> query, Optional<String> tags) {
        String traceQLQuery = null;

        // Priority: q parameter > tags parameter
        if (query.isPresent() && StringUtil.isNotEmpty(query.get())) {
            traceQLQuery = query.get();
        } else if (tags.isPresent() && StringUtil.isNotEmpty(tags.get())) {
            traceQLQuery = tags.get();
        }

        if (StringUtil.isEmpty(traceQLQuery)) {
            return TraceQLParseResult.of(new TraceQLQueryParams());
        }

        return TraceQLQueryParser.extractParams(traceQLQuery);
    }

    /**
     * Convert Protobuf TraceByIDResponse to JSON and build HTTP response.
     */
    private HttpResponse buildJsonHttpResponseFromProtobuf(TraceByIDResponse protoResponse) throws JsonProcessingException {
        OtlpTraceResponse jsonResponse = OTLPConverter.convertProtobufToJson(protoResponse, OTLPConverter.TraceType.SKYWALKING);
        return successResponse(jsonResponse);
    }

    /**
     * Parse duration string to microseconds.
     */
    private long parseDuration(String duration) throws IllegalExpressionException {
        return org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryVisitor.parseDuration(duration);
    }
}

