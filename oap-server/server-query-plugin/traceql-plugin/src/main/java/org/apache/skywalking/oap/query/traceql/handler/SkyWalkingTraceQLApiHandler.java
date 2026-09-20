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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.codec.DecoderException;
import org.apache.skywalking.oap.query.traceql.TraceQLConfig;
import org.apache.skywalking.oap.query.traceql.converter.OTLPConverter;
import org.apache.skywalking.oap.query.traceql.converter.SkyWalkingOTLPConverter;
import org.apache.skywalking.oap.query.traceql.converter.SkyWalkingSpanMatcher;
import org.apache.skywalking.oap.query.traceql.entity.OtlpTraceResponse;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;
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
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.query.type.Trace;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TraceList;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * SkyWalking-native implementation of TraceQL API Handler.
 */
public class SkyWalkingTraceQLApiHandler extends TraceQLApiHandler {
    private static final List<String> INTRINSIC_TAG_NAMES = Arrays.asList(
        NAME, STATUS, DURATION, SPAN_INTRINSIC_PREFIX + NAME, SPAN_INTRINSIC_PREFIX + STATUS, SPAN_INTRINSIC_PREFIX + DURATION);
    private final TraceQueryService traceQueryService;
    private final TagAutoCompleteQueryService tagAutoCompleteQueryService;
    private final MetadataQueryService metadataQueryService;
    private final TraceQLConfig traceQLConfig;
    private final Set<String> allowedTags;
    private static final long START_OF_2020_SEC = new DateTime(2020, 1, 1, 0, 0, 0, DateTimeZone.UTC).getMillis() / 1000;

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
        final String swTagsConfig = config.getSkywalkingTracesListResultTags();
        final Set<String> allowedTagsInit;
        if (StringUtil.isNotBlank(swTagsConfig)) {
            allowedTagsInit = Arrays.stream(swTagsConfig.split(Const.COMMA))
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
        // If start/end are provided use them; otherwise cover the full historical range from 2020-01-01 00:00:00 UTC
        Duration duration = buildDuration(
            start.isPresent() ? start : Optional.of(START_OF_2020_SEC),
            end,
            0L
        );

        // Query SkyWalking trace by ID
        Trace swTrace = traceQueryService.queryTrace(SkyWalkingOTLPConverter.decodeTraceId(traceId), duration);

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
                                       Optional<Integer> spss,
                                       Optional<Boolean> coldStage) throws IOException {
        // Parse TraceQL query
        TraceQLParseResult parseResult = parseTraceQLQuery(query, tags);
        if (parseResult.hasError()) {
            return errorResponse(HttpStatus.BAD_REQUEST, parseResult.getErrorInfo());
        }

        TraceQLQueryParams queryParams = parseResult.getParams();

        Duration duration = buildDuration(start, end, traceQLConfig.getLookback());
        TraceQueryCondition condition;
        try {
            condition = toCondition(queryParams, duration);
        } catch (IllegalArgumentException e) {
            return badRequestResponse(e.getMessage());
        }

        // The plain minDuration/maxDuration parameters, when the TraceQL did not set a range
        try {
            if (queryParams.getMinDuration() == null && minDuration.isPresent()) {
                condition.setMinTraceDuration((int) (parseDuration(minDuration.get()) / 1000));
            }
        } catch (IllegalExpressionException e) {
            return badRequestResponse("Invalid minDuration format: " + e.getMessage());
        }
        try {
            if (queryParams.getMaxDuration() == null && maxDuration.isPresent()) {
                condition.setMaxTraceDuration((int) (parseDuration(maxDuration.get()) / 1000));
            }
        } catch (IllegalExpressionException e) {
            return badRequestResponse("Invalid maxDuration format: " + e.getMessage());
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
        SearchResponse response = SkyWalkingOTLPConverter.convertTraceListToSearchResponse(
            traceList, allowedTags, new SkyWalkingSpanMatcher(queryParams), spansPerSpanSet(spss));

        return successResponse(response);
    }

    /**
     * TraceQL onto the trace query condition: service, instance and endpoint by their ids, the duration range in
     * whole milliseconds, {@code status} as the trace state, and every attribute (including
     * {@code span.http.status_code}) as a tag.
     */
    private static TraceQueryCondition toCondition(TraceQLQueryParams queryParams, Duration duration) {
        if (StringUtil.isNotBlank(queryParams.getKind())) {
            throw new IllegalArgumentException(
                "kind is not supported on the SkyWalking datasource: the span type is not a trace query condition");
        }
        if ("unset".equalsIgnoreCase(queryParams.getStatus())) {
            throw new IllegalArgumentException(
                "status = unset is not supported on the SkyWalking datasource: a span is either ok or error");
        }
        if (StringUtil.isNotBlank(queryParams.getRemoteServiceName()) && !ALL.equals(queryParams.getRemoteServiceName())) {
            throw new IllegalArgumentException(
                "resource.remote.service is not supported on the SkyWalking datasource: the trace query has no peer condition");
        }
        boolean serviceNamed = StringUtil.isNotBlank(queryParams.getServiceName()) && !ALL.equals(queryParams.getServiceName());
        if (!serviceNamed && StringUtil.isNotBlank(queryParams.getServiceInstance()) && !ALL.equals(queryParams.getServiceInstance())) {
            throw new IllegalArgumentException(
                "resource.instance needs resource.service.name on the SkyWalking datasource: instance ids are scoped by service");
        }
        if (!serviceNamed && StringUtil.isNotBlank(queryParams.getSpanName()) && !ALL.equals(queryParams.getSpanName())) {
            throw new IllegalArgumentException(
                "name needs resource.service.name on the SkyWalking datasource: endpoint ids are scoped by service");
        }
        TraceQueryCondition condition = new TraceQueryCondition();
        condition.setQueryDuration(duration);

        if (StringUtil.isNotBlank(queryParams.getServiceName())
            && !queryParams.getServiceName().equals(ALL)) {
            String serviceId = IDManager.ServiceID.buildId(queryParams.getServiceName(), true);
            condition.setServiceId(serviceId);
        }
        if (StringUtil.isNotBlank(queryParams.getServiceInstance())
            && StringUtil.isNotBlank(condition.getServiceId())
            && !queryParams.getServiceInstance().equals(ALL)) {
            condition.setServiceInstanceId(
                IDManager.ServiceInstanceID.buildId(condition.getServiceId(), queryParams.getServiceInstance()));
        }
        if (StringUtil.isNotBlank(queryParams.getSpanName())
            && StringUtil.isNotBlank(condition.getServiceId())
            && !queryParams.getSpanName().equals(ALL)) {
            condition.setEndpointId(IDManager.EndpointID.buildId(condition.getServiceId(), queryParams.getSpanName()));
        }

        // TraceQL durations reach the handler in microseconds; the condition takes whole milliseconds
        if (queryParams.getMinDuration() != null) {
            condition.setMinTraceDuration((int) (queryParams.getMinDuration() / 1000));
        }
        if (queryParams.getMaxDuration() != null) {
            condition.setMaxTraceDuration((int) (queryParams.getMaxDuration() / 1000));
        }

        condition.setTraceState(TraceState.ALL);
        if (ERROR.equalsIgnoreCase(queryParams.getStatus())) {
            condition.setTraceState(TraceState.ERROR);
        } else if (OK.equalsIgnoreCase(queryParams.getStatus())) {
            condition.setTraceState(TraceState.SUCCESS);
        }

        // The visitor keeps span.http.status_code apart; the agents record it as an ordinary tag
        List<Tag> tagList = new ArrayList<>();
        if (queryParams.getTags() != null) {
            for (Map.Entry<String, String> entry : queryParams.getTags().entrySet()) {
                Tag tag = new Tag();
                tag.setKey(entry.getKey());
                tag.setValue(entry.getValue());
                tagList.add(tag);
            }
        }
        if (StringUtil.isNotBlank(queryParams.getHttpStatusCode())) {
            Tag tag = new Tag();
            tag.setKey(HTTP_STATUS_CODE);
            tag.setValue(queryParams.getHttpStatusCode());
            tagList.add(tag);
        }
        if (!tagList.isEmpty()) {
            condition.setTags(tagList);
        }
        return condition;
    }

    /**
     * Tempo's {@code q} on a tag lookup: the newest {@link #TAG_FILTER_SAMPLE_TRACES} traces matching the filter,
     * with the matcher that picks their matching spans.
     */
    private Sample sample(TraceQLQueryParams params, Duration duration) throws IOException {
        TraceQueryCondition condition = toCondition(params, duration);
        Pagination pagination = new Pagination();
        pagination.setPageNum(1);
        pagination.setPageSize(TAG_FILTER_SAMPLE_TRACES);
        condition.setPaging(pagination);
        condition.setQueryOrder(QueryOrder.BY_START_TIME);
        return new Sample(traceQueryService.queryTraces(condition), new SkyWalkingSpanMatcher(params));
    }

    private static final class Sample {
        private final TraceList traces;
        private final SkyWalkingSpanMatcher matcher;

        private Sample(TraceList traces, SkyWalkingSpanMatcher matcher) {
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
            Sample sample = sample(filter.getParams(), duration);
            Set<String> searchableKeys = tagAutoCompleteQueryService.queryTagAutocompleteKeys(TagType.TRACE, duration);
            return tagNamesV2(scope, limit, requested -> SCOPE_INTRINSIC.equals(requested)
                ? INTRINSIC_TAG_NAMES : SkyWalkingOTLPConverter.tagNames(sample.traces, sample.matcher, requested, searchableKeys));
        } catch (IllegalArgumentException e) {
            return badRequestResponse(e.getMessage());
        }
    }

    /**
     * SkyWalking spans carry no kind the trace query can filter on, so the intrinsic scope lists name, status and
     * duration.
     */
    private List<String> namesOf(String scope, Duration duration) throws IOException {
        switch (scope) {
            case SCOPE_RESOURCE:
                //for Grafana variables, tempo only supports label query in variables setting.
                return Arrays.asList(SERVICE, INSTANCE);
            case SCOPE_SPAN:
                return new ArrayList<>(tagAutoCompleteQueryService.queryTagAutocompleteKeys(TagType.TRACE, duration));
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
            && !(onlyServiceName(filter.getParams()) && (NAME.equals(tag) || RESOURCE_INSTANCE.equals(tag)))) {
            try {
                Sample sample = sample(filter.getParams(), duration);
                return successResponse(stringValues(SkyWalkingOTLPConverter.tagValues(sample.traces, sample.matcher, tag), limit));
            } catch (IllegalArgumentException e) {
                return badRequestResponse(e.getMessage());
            }
        }

        if (tag.equals(RESOURCE_SERVICE_NAME) || tag.equals(RESOURCE_SERVICE)) {
            // Only GENERAL layer services use SkyWalking native protocol
            List<Service> services = metadataQueryService.listServices(Layer.GENERAL.name(), null);
            return successResponse(stringValues(
                services.stream().map(Service::getName).collect(Collectors.toList()), limit));
        }
        if (tag.equals(STATUS)) {
            return successResponse(stringValues(Arrays.asList(OK, ERROR), limit));
        }
        if (tag.equals(DURATION)) {
            // A range intrinsic has no value list, Tempo answers an empty one too.
            return successResponse(new TagValuesResponse());
        }
        if (tag.equals(KIND)) {
            return badRequestResponse("kind is not supported on the SkyWalking datasource: the span type is not a trace query condition");
        }
        if (tag.equals(RESOURCE_REMOTE_SERVICE)) {
            return badRequestResponse("resource.remote.service is not supported on the SkyWalking datasource: the trace query has no peer condition");
        }
        if (tag.startsWith(SPAN_PREFIX)) {
            return successResponse(stringValues(tagAutoCompleteQueryService.queryTagAutocompleteValues(
                TagType.TRACE, tag.substring(SPAN_PREFIX.length()), duration), limit));
        }
        if (tag.equals(NAME) || tag.equals(RESOURCE_INSTANCE)) {
            if (query.isPresent() && !query.get().isEmpty()) {
                TraceQLParseResult parseResult = TraceQLQueryParser.extractParams(query.get());
                if (parseResult.hasError()) {
                    return badRequestResponse(parseResult.getErrorInfo());
                }
                TraceQLQueryParams traceQLParams = parseResult.getParams();
                if (StringUtil.isNotBlank(traceQLParams.getServiceName()) && !traceQLParams.getServiceName().equals(ALL)) {
                    String serviceId = IDManager.ServiceID.buildId(traceQLParams.getServiceName(), true);
                    if (tag.equals(NAME)) {
                        List<Endpoint> endpoints = metadataQueryService.findEndpoint("", serviceId, limit.orElse(100), duration);
                        return successResponse(stringValues(
                            endpoints.stream().map(Endpoint::getName).collect(Collectors.toList()), limit));
                    }
                    List<ServiceInstance> instances = metadataQueryService.listInstances(duration, serviceId);
                    return successResponse(stringValues(
                        instances.stream().map(ServiceInstance::getName).collect(Collectors.toList()), limit));
                }
            }
            // Empty when no service is named: Grafana asks for these on every visit to the query page.
            return successResponse(new TagValuesResponse());
        }
        return badRequestResponse("Unsupported tag value query.");
    }

    /**
     * Parse TraceQL query string.
     */
    private TraceQLParseResult parseTraceQLQuery(Optional<String> query, Optional<String> tags) {
        // Priority: q parameter > tags parameter; the latter is Tempo's deprecated logfmt form, not TraceQL
        if (query.isPresent() && StringUtil.isNotEmpty(query.get())) {
            return TraceQLQueryParser.extractParams(query.get());
        }
        if (tags.isPresent() && StringUtil.isNotEmpty(tags.get())) {
            try {
                return TraceQLParseResult.of(parseTagsParameter(tags.get()));
            } catch (IllegalArgumentException e) {
                return TraceQLParseResult.error(e.getMessage());
            }
        }
        return TraceQLParseResult.of(new TraceQLQueryParams());
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

