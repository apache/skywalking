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

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import io.grafana.tempo.tempopb.TraceByIDResponse;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.query.traceql.TraceQLConfig;
import org.apache.skywalking.oap.query.traceql.converter.OTLPSpanMatcher;
import org.apache.skywalking.oap.query.traceql.converter.OTLPTraceAssembler;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;
import org.apache.skywalking.oap.query.traceql.entity.TagValuesResponse;
import org.apache.skywalking.oap.query.traceql.exception.IllegalExpressionException;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLParseResult;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParams;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParser;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.OTLPTraceQueryService;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryVisitor.parseDuration;

/**
 * The Tempo API over natively stored OTLP spans. Nothing is converted between formats: the search maps TraceQL
 * onto the index columns of {@code otlp_span}, and a trace is returned as the {@code ResourceSpans} the exporters
 * sent, regrouped into one message per resource and scope.
 */
public class OTLPTraceQLApiHandler extends TraceQLApiHandler {
    /**
     * TraceQL spellings of the OTLP span kinds, in enum-number order.
     */
    private static final List<String> KINDS = Arrays.asList(
        "unspecified", "internal", "server", "client", "producer", "consumer");
    private static final String UNSET = "unset";
    private static final List<String> INTRINSIC_TAG_NAMES = Arrays.asList(
        NAME, STATUS, KIND, DURATION,
        SPAN_INTRINSIC_PREFIX + NAME, SPAN_INTRINSIC_PREFIX + STATUS, SPAN_INTRINSIC_PREFIX + KIND, SPAN_INTRINSIC_PREFIX + DURATION);
    private static final String OTEL_SCOPE_NAME = OTLPTraceAssembler.OTEL_SCOPE_NAME;
    private static final String SPAN_OTEL_SCOPE_NAME = SPAN_PREFIX + OTEL_SCOPE_NAME;
    private static final String RESOURCE_SERVICE_INSTANCE_ID = SCOPE_RESOURCE + Const.POINT + SERVICE_INSTANCE_ID;
    private static final String PEER_SERVICE = "peer.service";
    private static final int DEFAULT_LIMIT = 20;

    private final OTLPTraceQueryService queryService;
    private final TagAutoCompleteQueryService tagAutoCompleteQueryService;
    private final TraceQLConfig config;
    private final Set<String> allowedTags;

    public OTLPTraceQLApiHandler(final ModuleManager moduleManager, final TraceQLConfig config) {
        this.queryService = new OTLPTraceQueryService(moduleManager);
        this.tagAutoCompleteQueryService = moduleManager.find(CoreModule.NAME)
                                                        .provider()
                                                        .getService(TagAutoCompleteQueryService.class);
        this.config = config;
        final Set<String> allowedTagsInit = new HashSet<>();
        if (StringUtil.isNotBlank(config.getOtlpTracesListResultTags())) {
            allowedTagsInit.addAll(Arrays.stream(config.getOtlpTracesListResultTags().split(Const.COMMA))
                                         .map(String::trim)
                                         .filter(tag -> !tag.isEmpty())
                                         .collect(Collectors.toList()));
        }
        allowedTagsInit.add(SPAN_KIND);
        allowedTagsInit.add(SERVICE_NAME);
        this.allowedTags = allowedTagsInit;
    }

    @Override
    protected HttpResponse queryTraceImpl(final String traceId,
                                          final Optional<Long> start,
                                          final Optional<Long> end,
                                          final Optional<Boolean> coldStage,
                                          final Optional<String> accept) throws IOException {
        // Without a range the storage searches everything it retains, as the Zipkin by-id lookup does.
        Duration duration = null;
        if (start.isPresent() || end.isPresent() || coldStage.orElse(false)) {
            duration = buildDuration(start, end, config.getLookback());
            duration.setColdStage(coldStage.orElse(false));
        }
        final List<SpanWrapper> wrappers = queryService.queryTraceById(traceId.trim().toLowerCase(Locale.ROOT), duration);
        if (wrappers.isEmpty()) {
            return HttpResponse.of(HttpStatus.NOT_FOUND);
        }
        final TraceByIDResponse response = OTLPTraceAssembler.assemble(OTLPTraceAssembler.decode(wrappers));
        if (accept.isPresent() && accept.get().contains("application/protobuf")) {
            return buildProtobufHttpResponse(response);
        }
        return HttpResponse.of(
            ResponseHeaders.builder(HttpStatus.OK).contentType(MediaType.JSON).build(),
            HttpData.ofUtf8(OTLPTraceAssembler.toJson(response))
        );
    }

    @Override
    protected HttpResponse searchImpl(final Optional<String> query,
                                      final Optional<String> tags,
                                      final Optional<String> minDuration,
                                      final Optional<String> maxDuration,
                                      final Optional<Integer> limit,
                                      final Optional<Long> start,
                                      final Optional<Long> end,
                                      final Optional<Integer> spss,
                                      final Optional<Boolean> coldStage) throws IOException {
        try {
            final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
            final Duration duration = buildDuration(start, end, config.getLookback());
            duration.setColdStage(coldStage.orElse(false));
            condition.setQueryDuration(duration);
            condition.setLimit(limit.orElse(DEFAULT_LIMIT));

            if (query.isPresent() && !query.get().isEmpty()) {
                final TraceQLParseResult parseResult = TraceQLQueryParser.extractParams(query.get());
                if (parseResult.hasError()) {
                    return badRequestResponse(parseResult.getErrorInfo());
                }
                applyTraceQL(parseResult.getParams(), condition);
            } else {
                if (tags.isPresent() && !tags.get().isEmpty()) {
                    applyTraceQL(parseTagsParameter(tags.get()), condition);
                }
            }
            if (condition.getMinDurationNanos() == 0 && minDuration.isPresent()) {
                condition.setMinDurationNanos(parseDuration(minDuration.get()) * 1000L);
            }
            if (condition.getMaxDurationNanos() == 0 && maxDuration.isPresent()) {
                condition.setMaxDurationNanos(parseDuration(maxDuration.get()) * 1000L);
            }

            final List<List<SpanWrapper>> wrapped = queryService.queryTraces(condition);
            final List<List<ResourceSpans>> traces = new ArrayList<>(wrapped.size());
            for (final List<SpanWrapper> trace : wrapped) {
                traces.add(OTLPTraceAssembler.decode(trace));
            }
            final SearchResponse response = OTLPTraceAssembler.toSearchResponse(
                traces, allowedTags, new OTLPSpanMatcher(condition), spansPerSpanSet(spss));
            return successResponse(response);
        } catch (IllegalExpressionException | IllegalArgumentException e) {
            return badRequestResponse(e.getMessage());
        }
    }

    /**
     * TraceQL onto the index columns, with no attribute renaming: the named fields go to their columns, every
     * other {@code span.x}, {@code resource.x} or unscoped {@code x} becomes an equality condition on the
     * {@code key=value} index, as on the Zipkin datasource.
     */
    private void applyTraceQL(final TraceQLQueryParams params, final OTLPTraceQueryCondition condition) {
        if (StringUtil.isNotBlank(params.getServiceName()) && !ALL.equals(params.getServiceName())) {
            condition.setServiceName(params.getServiceName());
        }
        if (StringUtil.isNotBlank(params.getRemoteServiceName()) && !ALL.equals(params.getRemoteServiceName())) {
            condition.setPeerService(params.getRemoteServiceName());
        }
        if (StringUtil.isNotBlank(params.getServiceInstance()) && !ALL.equals(params.getServiceInstance())) {
            condition.setServiceInstance(params.getServiceInstance());
        }
        if (StringUtil.isNotBlank(params.getSpanName()) && !ALL.equals(params.getSpanName())) {
            condition.setSpanName(params.getSpanName());
        }
        if (params.getMinDuration() != null) {
            condition.setMinDurationNanos(params.getMinDuration() * 1000L);
        }
        if (params.getMaxDuration() != null) {
            condition.setMaxDurationNanos(params.getMaxDuration() * 1000L);
        }
        if (StringUtil.isNotBlank(params.getStatus())) {
            condition.setStatusCode(statusCode(params.getStatus()));
        }
        if (StringUtil.isNotBlank(params.getKind())) {
            condition.setKind(kind(params.getKind()));
        }
        if (StringUtil.isNotBlank(params.getHttpStatusCode())) {
            condition.getTags().add(new Tag(HTTP_STATUS_CODE, params.getHttpStatusCode()));
        }
        for (final Map.Entry<String, String> tag : params.getTags().entrySet()) {
            switch (tag.getKey()) {
                case OTEL_SCOPE_NAME:
                    condition.setScopeName(tag.getValue());
                    break;
                case SERVICE_INSTANCE_ID:
                    condition.setServiceInstance(tag.getValue());
                    break;
                case PEER_SERVICE:
                    condition.setPeerService(tag.getValue());
                    break;
                default:
                    condition.getTags().add(new Tag(tag.getKey(), tag.getValue()));
            }
        }
    }

    private static int statusCode(final String status) {
        switch (status.toLowerCase(Locale.ROOT)) {
            case ERROR:
                return Status.StatusCode.STATUS_CODE_ERROR.getNumber();
            case OK:
                return Status.StatusCode.STATUS_CODE_OK.getNumber();
            case UNSET:
                return Status.StatusCode.STATUS_CODE_UNSET.getNumber();
            default:
                throw new IllegalArgumentException("Unsupported status: " + status + ", expected error, ok or unset.");
        }
    }

    private static int kind(final String kind) {
        final int number = KINDS.indexOf(kind.toLowerCase(Locale.ROOT));
        if (number < 0) {
            throw new IllegalArgumentException("Unsupported kind: " + kind + ", expected one of " + KINDS + ".");
        }
        return Span.SpanKind.forNumber(number).getNumber();
    }

    @Override
    protected HttpResponse searchTagsImpl(final Optional<String> scope,
                                          final Optional<Integer> limit,
                                          final Optional<Long> start,
                                          final Optional<Long> end) throws IOException {
        final Duration duration = buildDuration(start, end, config.getLookback());
        return tagNames(scope, limit, requested -> namesOf(requested, duration));
    }

    @Override
    protected HttpResponse searchTagsV2Impl(final Optional<String> q,
                                            final Optional<String> scope,
                                            final Optional<Integer> limit,
                                            final Optional<Long> start,
                                            final Optional<Long> end) throws IOException {
        final Duration duration = buildDuration(start, end, config.getLookback());
        final TraceQLParseResult filter = parseFilter(q);
        if (filter != null && filter.hasError()) {
            return badRequestResponse(filter.getErrorInfo());
        }
        if (filter == null || !hasFilter(filter.getParams())) {
            return tagNamesV2(scope, limit, requested -> namesOf(requested, duration));
        }
        try {
            final Sample sample = sample(filter.getParams(), duration);
            return tagNamesV2(scope, limit, requested -> SCOPE_INTRINSIC.equals(requested)
                ? INTRINSIC_TAG_NAMES : OTLPTraceAssembler.tagNames(sample.traces, sample.matcher, requested));
        } catch (IllegalArgumentException e) {
            return badRequestResponse(e.getMessage());
        }
    }

    private List<String> namesOf(final String scope, final Duration duration) throws IOException {
        switch (scope) {
            case SCOPE_RESOURCE:
                return Arrays.asList(SERVICE_NAME, SERVICE_INSTANCE_ID, REMOTE_SERVICE);
            case SCOPE_SPAN:
                return new ArrayList<>(tagAutoCompleteQueryService.queryTagAutocompleteKeys(TagType.OTLP, duration));
            default:
                return INTRINSIC_TAG_NAMES;
        }
    }

    /**
     * Tempo's {@code q} on a tag lookup: the newest {@link #TAG_FILTER_SAMPLE_TRACES} traces matching the filter,
     * with the matcher that picks their matching spans.
     */
    private Sample sample(final TraceQLQueryParams params, final Duration duration) throws IOException {
        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        condition.setQueryDuration(duration);
        condition.setLimit(TAG_FILTER_SAMPLE_TRACES);
        applyTraceQL(params, condition);
        final List<List<ResourceSpans>> traces = new ArrayList<>();
        for (final List<SpanWrapper> trace : queryService.queryTraces(condition)) {
            traces.add(OTLPTraceAssembler.decode(trace));
        }
        return new Sample(traces, new OTLPSpanMatcher(condition));
    }

    private static final class Sample {
        private final List<List<ResourceSpans>> traces;
        private final OTLPSpanMatcher matcher;

        private Sample(final List<List<ResourceSpans>> traces, final OTLPSpanMatcher matcher) {
            this.traces = traces;
            this.matcher = matcher;
        }
    }

    @Override
    protected HttpResponse searchTagValuesImpl(final String tagName,
                                               final Optional<String> query,
                                               final Optional<Integer> limit,
                                               final Optional<Long> start,
                                               final Optional<Long> end) throws IOException {
        final Duration duration = buildDuration(start, end, config.getLookback());
        final String tag = normalizeTagName(tagName);
        final TraceQLParseResult filter = parseFilter(query);
        if (filter != null && filter.hasError()) {
            return badRequestResponse(filter.getErrorInfo());
        }
        // Anything narrower than a service name is answered from a sample of matching traces; the service-scoped
        // catalogs below are exact and complete, so they keep answering the common service-only case.
        if (filter != null && hasFilter(filter.getParams()) && !isEnumIntrinsic(tag)
            && !(onlyServiceName(filter.getParams()) && (NAME.equals(tag) || RESOURCE_REMOTE_SERVICE.equals(tag)))) {
            try {
                final Sample sample = sample(filter.getParams(), duration);
                return successResponse(stringValues(OTLPTraceAssembler.tagValues(sample.traces, sample.matcher, tag), limit));
            } catch (IllegalArgumentException e) {
                return badRequestResponse(e.getMessage());
            }
        }
        switch (tag) {
            case RESOURCE_SERVICE_NAME:
            case RESOURCE_SERVICE:
            case SERVICE_NAME:
                return successResponse(stringValues(queryService.getServiceNames(duration), limit));
            case RESOURCE_SERVICE_INSTANCE_ID:
            case RESOURCE_INSTANCE:
                return successResponse(stringValues(
                    tagAutoCompleteQueryService.queryTagAutocompleteValues(TagType.OTLP, SERVICE_INSTANCE_ID, duration), limit));
            case STATUS:
                return successResponse(stringValues(Arrays.asList(ERROR, OK, UNSET), limit));
            case KIND:
                return successResponse(stringValues(KINDS, limit));
            case DURATION:
                // A range intrinsic has no value list, Tempo answers an empty one too.
                return successResponse(new TagValuesResponse());
            case OTEL_SCOPE_NAME:
            case SPAN_OTEL_SCOPE_NAME:
                return successResponse(stringValues(
                    tagAutoCompleteQueryService.queryTagAutocompleteValues(TagType.OTLP, OTEL_SCOPE_NAME, duration), limit));
            case NAME:
            case RESOURCE_REMOTE_SERVICE:
                return scopedValues(tag, query, duration, limit);
            default:
                if (tag.startsWith(SPAN_PREFIX)) {
                    return successResponse(stringValues(tagAutoCompleteQueryService.queryTagAutocompleteValues(
                        TagType.OTLP, tag.substring(SPAN_PREFIX.length()), duration), limit));
                }
                return badRequestResponse("Unsupported tag value query.");
        }
    }

    /**
     * Span names and remote services are listed per service, so they need a {@code q} naming the service; without
     * one the list is empty rather than an error, because Grafana asks for these on every visit to the query page.
     */
    private HttpResponse scopedValues(final String tagName,
                                      final Optional<String> query,
                                      final Duration duration,
                                      final Optional<Integer> limit) throws IOException {
        if (!query.isPresent() || query.get().isEmpty()) {
            return successResponse(new TagValuesResponse());
        }
        final TraceQLParseResult parseResult = TraceQLQueryParser.extractParams(query.get());
        if (parseResult.hasError()) {
            return badRequestResponse(parseResult.getErrorInfo());
        }
        final String serviceName = parseResult.getParams().getServiceName();
        if (StringUtil.isBlank(serviceName) || ALL.equals(serviceName)) {
            return successResponse(new TagValuesResponse());
        }
        if (NAME.equals(tagName)) {
            return successResponse(stringValues(queryService.getSpanNames(serviceName, duration), limit));
        }
        return successResponse(stringValues(queryService.getPeerServiceNames(serviceName, duration), limit));
    }
}
