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

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.trace.v1.ExportTracePartialSuccess;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.SpanFlags;
import io.opentelemetry.proto.trace.v1.Status;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.core.trace.SpanListenerManager;
import org.apache.skywalking.oap.server.core.trace.SpanListenerResult;
import com.linecorp.armeria.common.HttpMethod;
import java.util.Collections;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.otel.Handler;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;
import org.apache.skywalking.oap.server.receiver.zipkin.SpanForwardService;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverModule;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import zipkin2.Endpoint;
import zipkin2.Span;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.trace.SpanAnalysisResult;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.ITraceAnalyzerService;

/**
 * The {@code otlp-traces} handler. Every span goes through the phase-one {@code SpanListener}s, then either is
 * stored natively by {@link OTLPSpanForward} ({@code otlpTraceStorage: otlp}, the default) or, with
 * {@code otlpTraceStorage: zipkin}, converted to a Zipkin span and handed to the Zipkin receiver. One handler, two
 * backends: two trace handlers would both register {@code TraceServiceGrpc} on the shared gRPC server.
 */
@Slf4j
public class OpenTelemetryTraceHandler
    extends TraceServiceGrpc.TraceServiceImplBase
    implements Handler {
    public static final String TYPE = "otlp-traces";
    /**
     * The resource attributes an instance name is taken from, in the order the OTLP logs handler uses.
     */
    private static final List<String> INSTANCE_KEYS = List.of("service.instance.id", "service.instance");
    static final String SERVICE_LAYER_KEY = "service.layer";
    private static final String NO_SERVICE_NAME_MESSAGE =
        "resource carries none of service.name, faas.name, k8s.deployment.name, process.executable.name";

    private ModuleManager manager;
    private OtelMetricReceiverConfig config;
    /**
     * {@code receiver-otel.otlpServiceGroupKeys}, split once; empty groups nothing.
     */
    private List<String> serviceGroupKeys = Collections.emptyList();
    private SpanForwardService forwardService;
    private OTLPSpanForward otlpSpanForward;
    private SpanListenerManager spanListenerManager;

    /**
     * The TAL analyzer, or null when the trace-analyzer module is not loaded, which is the operator's way of
     * storing OTLP traces without analysis; the provider declares the module only when it is loaded. Resolved on
     * first use, because the module manager cannot be asked at construction.
     */
    private volatile ITraceAnalyzerService traceAnalyzerService;

    private volatile boolean traceAnalyzerResolved;

    private ITraceAnalyzerService traceAnalyzerService() {
        if (!traceAnalyzerResolved) {
            traceAnalyzerService = manager.has(TraceAnalyzerModule.NAME)
                ? manager.find(TraceAnalyzerModule.NAME).provider().getService(ITraceAnalyzerService.class)
                : null;
            traceAnalyzerResolved = true;
        }
        return traceAnalyzerService;
    }

    @Getter(lazy = true)
    private final MetricsCreator metricsCreator = manager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);

    @Getter(lazy = true)
    private final HistogramMetrics processHistogram = getMetricsCreator().createHistogramMetric(
        "otel_spans_latency",
        "The latency to process the span request",
        MetricsTag.EMPTY_KEY,
        MetricsTag.EMPTY_VALUE
    );
    @Getter(lazy = true)
    private final CounterMetrics droppedSpans = getMetricsCreator().createCounter(
        "otel_spans_dropped",
        "The count of spans that were dropped due to rate limit",
        MetricsTag.EMPTY_KEY,
        MetricsTag.EMPTY_VALUE
    );

    @Override
    public void init(ModuleManager manager, OtelMetricReceiverConfig config) {
        this.manager = manager;
        this.config = config;
        this.serviceGroupKeys = config.getOtlpServiceGroupKeys();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void active() throws ModuleStartException {
        GRPCHandlerRegister grpcHandlerRegister = manager.find(SharingServerModule.NAME)
            .provider()
            .getService(GRPCHandlerRegister.class);
        grpcHandlerRegister.addHandler(this);

        HTTPHandlerRegister httpHandlerRegister = manager.find(SharingServerModule.NAME)
            .provider()
            .getService(HTTPHandlerRegister.class);
        httpHandlerRegister.addHandler(
            new OpenTelemetryTraceHTTPHandler(this),
            Collections.singletonList(HttpMethod.POST));

        if (config.isOtlpTraceStorageNative()) {
            // Built here, once, so the rate limiter is shared by every export; a lazy build raced on the first two.
            otlpSpanForward = new OTLPSpanForward(config, manager);
        }
    }

    @Override
    public void export(ExportTraceServiceRequest request, StreamObserver<ExportTraceServiceResponse> responseObserver) {
        responseObserver.onNext(processExport(request));
        responseObserver.onCompleted();
    }

    /**
     * Process an OTLP trace export request. Shared by both gRPC and HTTP handlers.
     *
     * @return the export response. In native storage mode it reports the spans rejected for a resource without a
     * service name through {@code partial_success}, as the OTLP specification requires for rejected data; spans
     * dropped by head sampling or the rate limit are not rejections and stay visible through
     * {@code otel_spans_dropped}. The Zipkin mode keeps answering an empty response.
     */
    ExportTraceServiceResponse processExport(ExportTraceServiceRequest request) {
        final boolean nativeStorage = config != null && config.isOtlpTraceStorageNative();
        final ArrayList<Span> result = new ArrayList<>();
        long rejectedSpans = 0;

        try (final var unused = getProcessHistogram().createTimer()) {
            for (final ResourceSpans resourceSpans : request.getResourceSpansList()) {
                final Resource resource = resourceSpans.getResource();
                final List<ScopeSpans> scopeSpansList = resourceSpans.getScopeSpansList();
                if (resource.getAttributesCount() == 0 && scopeSpansList.size() == 0) {
                    continue;
                }
                final Map<String, String> resourceTags = OTLPValues.toStringMap(resource.getAttributesList());
                // TAL's resource() reads the attributes as sent; the Zipkin path below rewrites resourceTags in place
                // (the service-name fallback key, the scope's otel.library.* tags), so it analyzes a copy taken first.
                final Map<String, String> analyzedResourceTags = nativeStorage
                    ? resourceTags : new HashMap<>(resourceTags);
                // The native path fills the service_name column only and leaves the attributes as sent; the Zipkin
                // path rewrites the fallback key into an `otlp.service.name.source` tag.
                final String serviceName = nativeStorage
                    ? resolveServiceName(resourceTags) : extractZipkinServiceName(resourceTags);
                if (StringUtil.isEmpty(serviceName)) {
                    log.warn("No service name found in resource attributes, discarding the trace");
                    if (nativeStorage) {
                        rejectedSpans += countSpans(scopeSpansList);
                    }
                    continue;
                }
                // The service the analysis names and the store files the span under; the resource attributes,
                // service.name among them, stay as sent.
                final String groupedServiceName = OTLPServiceGroup.apply(
                    serviceName, analyzedResourceTags, serviceGroupKeys);
                // The analysis reads service.layer as sent; the stored span carries the layer the service catalog
                // files the service under, UNDEFINED for an unregistered or absent one, so a search on it agrees
                // with the catalog.
                final Resource storedResource = nativeStorage
                    ? OTLPSpanForward.withCatalogLayer(resource, OTLPSpanForward.catalogLayerOf(resourceTags))
                    : resource;

                try {
                    for (ScopeSpans scopeSpans : scopeSpansList) {
                        final InstrumentationScope scope = scopeSpans.getScope();
                        final String scopeName = scope.getName();
                        final String scopeVersion = scope.getVersion();
                        if (!nativeStorage) {
                            extractScopeTag(scope, resourceTags);
                        }
                        final Map<String, String> scopeTags = OTLPValues.toStringMap(scope.getAttributesList());
                        for (io.opentelemetry.proto.trace.v1.Span span : scopeSpans.getSpansList()) {
                            final OTLPSpanReaderImpl reader = new OTLPSpanReaderImpl(span);
                            // Phase 1: notify OTLP span listeners before conversion or storage
                            final SpanListenerResult otlpResult = getSpanListenerManager()
                                .notifyOTLPPhase(reader, groupedServiceName, resourceTags, scopeName, scopeVersion);

                            // TAL runs on every span whatever the storage mode and whatever the listeners decided,
                            // and before the forward applies head sampling, so metrics count what the SDK sent
                            // rather than what OAP chose to store.
                            final SpanAnalysisResult analysis = analyze(
                                span, groupedServiceName, analyzedResourceTags, scopeTags, scopeName, scopeVersion);

                            if (!otlpResult.isShouldPersist() || !analysis.isPersist()) {
                                continue;
                            }

                            // What the listeners and the rules injected, and only that: the native store appends
                            // these to the span as attributes, so the resource attributes must not be among them.
                            // Last writer wins, and a rule's tag is written after a listener's.
                            final Map<String, String> injectedTags;
                            if (!otlpResult.getAdditionalTags().isEmpty() || !analysis.getAdditionalTags().isEmpty()) {
                                injectedTags = new HashMap<>(otlpResult.getAdditionalTags());
                                injectedTags.putAll(analysis.getAdditionalTags());
                            } else {
                                injectedTags = Collections.emptyMap();
                            }

                            if (nativeStorage) {
                                // The response reports the batch as accepted, so one span's failure must not take the
                                // rest of the export with it; what is not stored is counted as dropped.
                                try {
                                    storeNative(storedResource, resourceSpans.getSchemaUrl(), scopeSpans, span,
                                                groupedServiceName, resourceTags, injectedTags,
                                                analysis.isEnforced());
                                } catch (Exception e) {
                                    log.warn("Storing OTLP span {} failed, dropping it: {}",
                                             OTLPValues.hexId(span.getSpanId()), e.getMessage());
                                    getDroppedSpans().inc(1);
                                }
                                continue;
                            }

                            // The Zipkin conversion carries the resource attributes as span tags, merged into a
                            // per-span copy so the injected tags do not leak into the next span that shares the
                            // resourceTags map.
                            final Map<String, String> spanTags;
                            if (injectedTags.isEmpty()) {
                                spanTags = resourceTags;
                            } else {
                                spanTags = new HashMap<>(resourceTags);
                                spanTags.putAll(injectedTags);
                            }
                            Span zipkinSpan = convertSpan(span, groupedServiceName, spanTags, analysis.isEnforced());
                            result.add(zipkinSpan);
                        }
                    }
                } catch (Exception e) {
                    log.warn("convert span error, discarding the span: {}", e.getMessage());
                }
            }
            if (!nativeStorage) {
                final var processedSpans = getForwardService().send(result);
                if (processedSpans.size() < result.size()) {
                    getDroppedSpans().inc(result.size() - processedSpans.size());
                }
            }
        }

        if (rejectedSpans == 0) {
            return ExportTraceServiceResponse.getDefaultInstance();
        }
        return ExportTraceServiceResponse.newBuilder()
                                         .setPartialSuccess(ExportTracePartialSuccess.newBuilder()
                                                                                     .setRejectedSpans(rejectedSpans)
                                                                                     .setErrorMessage(NO_SERVICE_NAME_MESSAGE))
                                         .build();
    }


    /**
     * Run the TAL rules for one span. A failure here never costs the span: analysis is an addition to the trace
     * path, so a broken rule leaves storage exactly as it was.
     *
     * @param span          the span as it arrived
     * @param serviceName   the service name resolved from the resource
     * @param resourceTags  the resource attributes as strings
     * @param scopeTags     the instrumentation scope's attributes
     * @param scopeName     the instrumentation scope's name
     * @param scopeVersion  the instrumentation scope's version
     * @return the rules' verdict, or {@code CONTINUE} when no rule ran
     */
    private SpanAnalysisResult analyze(final io.opentelemetry.proto.trace.v1.Span span,
                                       final String serviceName,
                                       final Map<String, String> resourceTags,
                                       final Map<String, String> scopeTags,
                                       final String scopeName,
                                       final String scopeVersion) {
        final ITraceAnalyzerService analyzer = traceAnalyzerService();
        if (analyzer == null) {
            return SpanAnalysisResult.CONTINUE;
        }
        try {
            return analyzer.doAnalysis(
                spanMetadataOf(span, serviceName, resourceTags, scopeName, scopeVersion),
                span, resourceTags, scopeTags);
        } catch (final Exception e) {
            log.warn("TAL analysis failed for span {}; the span is unaffected",
                     OTLPValues.hexId(span.getSpanId()), e);
            return SpanAnalysisResult.CONTINUE;
        }
    }

    /**
     * The protocol-neutral view a rule reads as {@code meta.*}.
     */
    static SpanMetadata spanMetadataOf(final io.opentelemetry.proto.trace.v1.Span span,
                                               final String serviceName,
                                               final Map<String, String> resourceTags,
                                               final String scopeName,
                                               final String scopeVersion) {
        final String parentSpanId = OTLPValues.hexId(span.getParentSpanId());
        // A sender that leaves a time out sends 0. The other time, or the arrival time when both are missing, stands
        // in, rather than metrics in the epoch's minute or a latency of decades.
        final long startNanos = span.getStartTimeUnixNano() > 0 ? span.getStartTimeUnixNano()
            : span.getEndTimeUnixNano() > 0 ? span.getEndTimeUnixNano()
            : TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
        final long endNanos = span.getEndTimeUnixNano() > 0 ? span.getEndTimeUnixNano() : startNanos;
        return SpanMetadata.builder()
                           .service(serviceName)
                           .serviceInstance(firstPresent(resourceTags, INSTANCE_KEYS))
                           .endpoint(span.getName())
                           .layer(resourceTags.getOrDefault(SERVICE_LAYER_KEY, ""))
                           .kind(SpanMetadata.normalizeKind(span.getKind().name()))
                           .statusCode(SpanMetadata.normalizeStatusCode(span.getStatus().getCode().name()))
                           .statusMessage(span.getStatus().getMessage())
                           .startTime(TimeUnit.NANOSECONDS.toMillis(startNanos))
                           .timestamp(TimeUnit.NANOSECONDS.toMillis(endNanos))
                           .latency(SpanMetadata.latencyMillis(startNanos, endNanos))
                           .traceId(OTLPValues.hexId(span.getTraceId()))
                           .spanId(OTLPValues.hexId(span.getSpanId()))
                           .parentSpanId(parentSpanId)
                           .root(parentSpanId.isEmpty())
                           .parentLocation(parentLocation(parentSpanId, span.getFlags()))
                           .scopeName(scopeName)
                           .scopeVersion(scopeVersion)
                           .build();
    }

    /**
     * Where the parent ran, from the OTLP span flags: bit 8 says whether bit 9, "the parent is remote", is set at
     * all. SDKs that predate the bits, and Envoy, leave both clear.
     */
    static String parentLocation(final String parentSpanId, final int flags) {
        if (parentSpanId.isEmpty() || (flags & SpanFlags.SPAN_FLAGS_CONTEXT_HAS_IS_REMOTE_MASK_VALUE) == 0) {
            return SpanMetadata.PARENT_UNKNOWN;
        }
        return (flags & SpanFlags.SPAN_FLAGS_CONTEXT_IS_REMOTE_MASK_VALUE) != 0
            ? SpanMetadata.PARENT_REMOTE : SpanMetadata.PARENT_LOCAL;
    }

    private static String firstPresent(final Map<String, String> source, final List<String> keys) {
        for (final String key : keys) {
            final String value = source.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static long countSpans(final List<ScopeSpans> scopeSpansList) {
        long count = 0;
        for (final ScopeSpans scopeSpans : scopeSpansList) {
            count += scopeSpans.getSpansCount();
        }
        return count;
    }

    /**
     * A span a rule's {@code enforcer {}} kept becomes a Zipkin {@code debug} span, which
     * {@code SpanForward.getSampledTraces} keeps past its head sampling, as {@link OTLPSpanForward} does for an
     * enforced span in the native store.
     */
    private Span convertSpan(io.opentelemetry.proto.trace.v1.Span span, String serviceName, Map<String, String> resourceTags,
                             boolean enforced) {
        final Span.Builder spanBuilder = Span.newBuilder();
        if (enforced) {
            spanBuilder.debug(true);
        }
        final Map<String, String> tags = aggregateSpanTags(span.getAttributesList(), resourceTags);

        if (span.getTraceId().isEmpty()) {
            throw new IllegalArgumentException("No trace id found in span");
        }
        spanBuilder.traceId(
            ByteBuffer.wrap(span.getTraceId().toByteArray(), 0, 8).getLong(),
            ByteBuffer.wrap(span.getTraceId().toByteArray(), 8, span.getTraceId().size() - 8).getLong()
        );

        if (span.getSpanId().isEmpty()) {
            throw new IllegalArgumentException("No span id found in span");
        }
        spanBuilder.id(convertSpanId(span.getSpanId()));

        tags.put("w3c.tracestate", span.getTraceState());

        if (!span.getParentSpanId().isEmpty()) {
            spanBuilder.parentId(convertSpanId(span.getParentSpanId()));
        }

        spanBuilder.name(span.getName());
        final long startMicro = TimeUnit.NANOSECONDS.toMicros(span.getStartTimeUnixNano());
        final long endMicro = TimeUnit.NANOSECONDS.toMicros(span.getEndTimeUnixNano());
        spanBuilder.timestamp(startMicro);
        spanBuilder.duration(endMicro - startMicro);

        spanBuilder.kind(convertKind(span.getKind()));
        if (span.getKind() == io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_INTERNAL) {
            tags.put("span.kind", "internal");
        }

        final Set<String> redundantKeys = new HashSet<>();
        spanBuilder.localEndpoint(convertEndpointFromTags(tags, serviceName, false, redundantKeys));
        spanBuilder.remoteEndpoint(convertEndpointFromTags(tags, "", true, redundantKeys));

        removeRedundantTags(tags, redundantKeys);
        populateStatus(span.getStatus(), tags);

        convertAnnotations(spanBuilder, span.getEventsList());
        convertLink(tags, span.getLinksList());

        tags.forEach(spanBuilder::putTag);

        return spanBuilder.build();
    }

    private void convertAnnotations(Span.Builder spanBuilder, List<io.opentelemetry.proto.trace.v1.Span.Event> events) {
        events.forEach(event -> {
            final long eventTime = TimeUnit.NANOSECONDS.toMicros(event.getTimeUnixNano());
            if (event.getAttributesList().size() == 0 && event.getDroppedAttributesCount() == 0) {
                spanBuilder.addAnnotation(eventTime, event.getName());
                return;
            }

            final JsonObject attrObj = OTLPValues.renderKvList(event.getAttributesList());
            spanBuilder.addAnnotation(eventTime,
                event.getName() + "|" + attrObj + "|" + event.getDroppedAttributesCount());
        });
    }

    private void convertLink(Map<String, String> tags, List<io.opentelemetry.proto.trace.v1.Span.Link> links) {
        for (int i = 0; i < links.size(); i++) {
            final io.opentelemetry.proto.trace.v1.Span.Link link = links.get(i);
            tags.put("otlp.link." + i,
                idToHexString(link.getTraceId()) + "|" + idToHexString(link.getSpanId()) + "|" +
                link.getTraceState() + "|" + OTLPValues.renderKvList(link.getAttributesList()) + "|" +
                link.getDroppedAttributesCount());
        }
    }

    /**
     * The Zipkin path has always written link ids in this decimal spelling; kept as is so stored tags stay
     * comparable across versions. The native path stores the link bytes themselves.
     */
    private String idToHexString(ByteString id) {
        if (id == null) {
            return "";
        }
        return new BigInteger(1, id.toByteArray()).toString();
    }

    private void populateStatus(Status status, Map<String, String> tags) {
        if (status.getCode() == Status.StatusCode.STATUS_CODE_ERROR) {
            tags.put("error", "true");
        } else {
            tags.remove("error");
        }

        if (status.getCode() == Status.StatusCode.STATUS_CODE_UNSET) {
            return;
        }

        tags.put("otel.status_code", status.getCode().name());
        if (StringUtil.isNotEmpty(status.getMessage())) {
            tags.put("otel.status_description", status.getMessage());
        }
    }

    private void removeRedundantTags(Map<String, String> resourceKeys, Set<String> redundantKeys) {
        for (String key : redundantKeys) {
            resourceKeys.remove(key);
        }
    }

    private Endpoint convertEndpointFromTags(Map<String, String> resourceTags, String localServiceName, boolean isRemote, Set<String> redundantKeys) {
        final Endpoint.Builder builder = Endpoint.newBuilder();
        String serviceName = localServiceName;
        String tmpVal;
        if (isRemote && StringUtil.isNotEmpty(tmpVal = getAndPutRedundantKey(resourceTags, "peer.service", redundantKeys))) {
            serviceName = tmpVal;
        } else if (isRemote &&
            StringUtil.isNotEmpty(tmpVal = getAndPutRedundantKey(resourceTags, "net.peer.name", redundantKeys)) &&
            // if it's not IP, then define it as service name
            !builder.parseIp(tmpVal)) {
            serviceName = tmpVal;
        }

        String ipKey, portKey;
        if (isRemote) {
            ipKey = "net.peer.ip";
            portKey = "net.peer.port";
        } else {
            ipKey = "net.host.ip";
            portKey = "net.host.port";
        }

        boolean ipParseSuccess = false;
        if (StringUtil.isNotEmpty(tmpVal = getAndPutRedundantKey(resourceTags, ipKey, redundantKeys))) {
            if (!(ipParseSuccess = builder.parseIp(tmpVal))) {
                // if ip parse failed, use the value as service name
                serviceName = StringUtil.isEmpty(serviceName) ? tmpVal : serviceName;
            }
        }
        if (StringUtil.isNotEmpty(tmpVal = getAndPutRedundantKey(resourceTags, portKey, redundantKeys))) {
            builder.port(Integer.parseInt(tmpVal));
        }
        if (StringUtil.isEmpty(serviceName) && !ipParseSuccess) {
            return null;
        }

        builder.serviceName(serviceName);
        return builder.build();
    }

    private String getAndPutRedundantKey(Map<String, String> resourceTags, String key, Set<String> redundantKeys) {
        String val = resourceTags.get(key);
        if (StringUtil.isEmpty(val)) {
            return null;
        }
        redundantKeys.add(key);
        return val;
    }

    private Span.Kind convertKind(io.opentelemetry.proto.trace.v1.Span.SpanKind kind) {
        switch (kind) {
            case SPAN_KIND_CLIENT:
                return Span.Kind.CLIENT;
            case SPAN_KIND_SERVER:
                return Span.Kind.SERVER;
            case SPAN_KIND_PRODUCER:
                return Span.Kind.PRODUCER;
            case SPAN_KIND_CONSUMER:
                return Span.Kind.CONSUMER;
        }
        return null;
    }

    private long convertSpanId(ByteString spanId) {
        return ByteBuffer.wrap(spanId.toByteArray()).getLong();
    }

    private Map<String, String> aggregateSpanTags(List<KeyValue> spanAttrs, Map<String, String> resourceTags) {
        final HashMap<String, String> result = new HashMap<>();
        result.putAll(resourceTags);
        result.putAll(OTLPValues.toStringMap(spanAttrs));
        return result;
    }

    private void extractScopeTag(InstrumentationScope scope, Map<String, String> resourceTags) {
        if (scope == null) {
            return;
        }

        if (StringUtil.isNotEmpty(scope.getName())) {
            resourceTags.put("otel.library.name", scope.getName());
        }
        if (StringUtil.isNotEmpty(scope.getVersion())) {
            resourceTags.put("otel.library.version", scope.getVersion());
        }
    }

    /**
     * The service name for the native store: the same fallback order as {@link #extractZipkinServiceName}, but
     * the attributes are left exactly as sent.
     */
    private static String resolveServiceName(Map<String, String> resourceTags) {
        for (final String key : OTLPSpanRecord.SERVICE_NAME_RESOURCE_KEYS) {
            final String name = resourceTags.get(key);
            if (StringUtil.isNotEmpty(name)) {
                return name;
            }
        }
        return "";
    }

    private String extractZipkinServiceName(Map<String, String> resourceTags) {
        String name = null;
        name = getServiceNameFromTags(name, resourceTags, "service.name", false);
        name = getServiceNameFromTags(name, resourceTags, "faas.name", true);
        name = getServiceNameFromTags(name, resourceTags, "k8s.deployment.name", true);
        name = getServiceNameFromTags(name, resourceTags, "process.executable.name", true);

        return name;
    }

    private String getServiceNameFromTags(String serviceName, Map<String, String> resourceTags, String tagKey, boolean addingSource) {
        if (StringUtil.isNotEmpty(serviceName)) {
            return serviceName;
        }

        String name = resourceTags.get(tagKey);
        if (StringUtil.isNotEmpty(name)) {
            if (addingSource) {
                resourceTags.remove(tagKey);
                resourceTags.put("otlp.service.name.source", tagKey);
            }
            return name;
        }
        return "";
    }

    private SpanForwardService getForwardService() {
        if (forwardService == null) {
            forwardService = manager.find(ZipkinReceiverModule.NAME).provider().getService(SpanForwardService.class);
        }
        return forwardService;
    }

    /**
     * Store one span natively. Phase one has already run in the caller's loop, along with the TAL rules, so what
     * is left here is the native-only phase-two notification and the forward. Exceptions propagate to the caller,
     * which drops this span only.
     *
     * @param storedResource the span's resource as stored: as received but for its {@code service.layer}, see
     *                       {@link OTLPSpanForward#withCatalogLayer}
     * @param injectedTags the tags phase one and the rules injected, which the caller merged; they are appended
     *                     to the stored span, so the resource attributes are never among them
     * @param enforced     whether a rule's {@code enforcer {}} kept this span past head sampling
     */
    private void storeNative(final Resource storedResource,
                             final String resourceSchemaUrl,
                             final ScopeSpans scopeSpans,
                             final io.opentelemetry.proto.trace.v1.Span span,
                             final String serviceName,
                             final Map<String, String> resourceTags,
                             final Map<String, String> injectedTags,
                             final boolean enforced) {
        final InstrumentationScope scope = scopeSpans.getScope();
        final OTLPSpanReaderImpl reader = new OTLPSpanReaderImpl(span);
        // Phase 2 for natively stored spans: no Zipkin span will ever exist for them.
        final SpanListenerResult storedResult = getSpanListenerManager()
            .notifyNativeOTLPPhase(reader, serviceName, resourceTags, scope.getName(), scope.getVersion());
        if (!storedResult.isShouldPersist()) {
            return;
        }
        final Map<String, String> storedTags = new HashMap<>(injectedTags);
        storedTags.putAll(storedResult.getAdditionalTags());
        if (!otlpSpanForward.send(
            storedResource, resourceSchemaUrl, scope, scopeSpans.getSchemaUrl(),
            span, serviceName, resourceTags, storedTags, enforced)) {
            getDroppedSpans().inc(1);
        }
    }

    private SpanListenerManager getSpanListenerManager() {
        if (spanListenerManager == null) {
            spanListenerManager = manager.find(CoreModule.NAME).provider().getService(SpanListenerManager.class);
        }
        return spanListenerManager;
    }
}
