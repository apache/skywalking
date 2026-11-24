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

import com.linecorp.armeria.common.HttpMethod;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.trace.v1.ExportTracePartialSuccess;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.SpanFlags;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.core.trace.SpanAnalysisResult;
import org.apache.skywalking.oap.server.core.trace.SpanListenerManager;
import org.apache.skywalking.oap.server.core.trace.SpanListenerResult;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.core.trace.TraceAnalysisError;
import org.apache.skywalking.oap.server.core.trace.TraceInLatency;
import org.apache.skywalking.oap.server.core.trace.TraceSpansDropped;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.otel.Handler;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.ITraceAnalyzerService;

/**
 * The {@code otlp-traces} handler. Every span goes through the phase-one {@code SpanListener}s and the TAL rules,
 * then into the {@link OTLPTraceStorage} {@code otlpTraceStorage} selects: stored natively
 * ({@link OTLPNativeTraceStorage}, {@code otlp}, the default) or converted for the Zipkin receiver
 * ({@link OTLPZipkinTraceStorage}, {@code zipkin}). One handler, two storages: two trace handlers would both
 * register {@code TraceServiceGrpc} on the shared gRPC server.
 */
@Slf4j
public class OpenTelemetryTraceHandler
    extends TraceServiceGrpc.TraceServiceImplBase
    implements Handler {
    public static final String TYPE = "otlp-traces";
    /**
     * The {@code protocol} labels on {@code trace_in_latency} and {@code trace_analysis_error_count}, beside the
     * agent's {@code grpc}, {@code http} and {@code kafka} and the Zipkin receiver's {@code zipkin-http} and
     * {@code zipkin-kafka}.
     */
    static final String PROTOCOL_GRPC = "otlp-grpc";
    static final String PROTOCOL_HTTP = "otlp-http";
    /**
     * The resource attributes an instance name is taken from, in the order the OTLP logs handler uses.
     */
    private static final List<String> INSTANCE_KEYS = List.of("service.instance.id", "service.instance");
    static final String SERVICE_LAYER_KEY = "service.layer";
    private static final String NO_SERVICE_NAME_MESSAGE =
        "resource carries none of service.name, faas.name, k8s.deployment.name, process.executable.name";

    private ModuleManager manager;
    /**
     * {@code receiver-otel.otlpServiceGroupKeys}, split once; empty groups nothing.
     */
    private List<String> serviceGroupKeys = Collections.emptyList();
    private OTLPTraceStorage storage;
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
    private final HistogramMetrics grpcLatency = getMetricsCreator().createHistogramMetric(
        TraceInLatency.NAME, TraceInLatency.HELP,
        new MetricsTag.Keys("protocol"), new MetricsTag.Values(PROTOCOL_GRPC)
    );
    @Getter(lazy = true)
    private final CounterMetrics rejectedSpans = getMetricsCreator().createCounter(
        "otel_spans_rejected",
        "The count of spans rejected because their resource names no service",
        MetricsTag.EMPTY_KEY,
        MetricsTag.EMPTY_VALUE
    );
    @Getter(lazy = true)
    private final TraceSpansDropped grpcDroppedSpans = new TraceSpansDropped(getMetricsCreator(), PROTOCOL_GRPC);
    @Getter(lazy = true)
    private final CounterMetrics grpcErrors = getMetricsCreator().createCounter(
        TraceAnalysisError.NAME, TraceAnalysisError.HELP,
        new MetricsTag.Keys("protocol"), new MetricsTag.Values(PROTOCOL_GRPC)
    );

    @Override
    public void init(ModuleManager manager, OtelMetricReceiverConfig config) {
        this.manager = manager;
        this.serviceGroupKeys = config.getOtlpServiceGroupKeys();
        this.storage = config.isOtlpTraceStorageNative()
            ? new OTLPNativeTraceStorage(config, manager, this::getSpanListenerManager)
            : new OTLPZipkinTraceStorage(manager);
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
            new OpenTelemetryTraceHTTPHandler(this, getMetricsCreator()),
            Collections.singletonList(HttpMethod.POST));
    }

    @Override
    public void export(ExportTraceServiceRequest request, StreamObserver<ExportTraceServiceResponse> responseObserver) {
        try {
            responseObserver.onNext(processExport(request, getGrpcLatency(), getGrpcDroppedSpans()));
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            getGrpcErrors().inc();
            throw e;
        }
    }

    /**
     * Process an OTLP trace export request. Shared by both gRPC and HTTP handlers.
     *
     * @param spanLatency observes each span's latency in seconds times {@link TraceInLatency#SPAN_LATENCY_SCALE}: the
     *                    {@code trace_in_latency} histogram of the protocol the request came in on.
     * @param droppedSpans the {@code spans_dropped_count} counters of the protocol the request came in on.
     * @return the export response. When the storage {@link OTLPTraceStorage#reportsRejections() reports
     * rejections}, the native one, it reports the spans of a resource without a service name through
     * {@code partial_success}, as the OTLP specification requires for rejected data; spans dropped by head sampling
     * or the rate limit are not rejections and stay visible through {@code spans_dropped_count}. The Zipkin storage
     * keeps answering an empty response.
     * <p>
     * Every span of a resource with a service name is one {@code trace_in_latency} observation, as every OTLP log
     * record is one {@code otel_logs_latency} observation; the spans of a resource without one count in
     * {@code otel_spans_rejected}. The ones not stored count in {@code spans_dropped_count} by reason; a span the
     * listeners or a rule chose not to persist is not dropped.
     */
    ExportTraceServiceResponse processExport(final ExportTraceServiceRequest request,
                                             final HistogramMetrics spanLatency,
                                             final TraceSpansDropped droppedSpans) {
        final OTLPTraceStorage.Export export = storage.newExport(droppedSpans);
        long rejectedSpans = 0;

        for (final ResourceSpans resourceSpans : request.getResourceSpansList()) {
            final List<ScopeSpans> scopeSpansList = resourceSpans.getScopeSpansList();
            if (resourceSpans.getResource().getAttributesCount() == 0 && scopeSpansList.size() == 0) {
                continue;
            }
            // As sent: what TAL's resource() reads and the service grouping keys on.
            final Map<String, String> resourceTags = OTLPValues.toStringMap(
                resourceSpans.getResource().getAttributesList());
            final String serviceName = resolveServiceName(resourceTags);
            if (StringUtil.isEmpty(serviceName)) {
                log.warn("No service name found in resource attributes, discarding the trace");
                final long resourceSpanCount = countSpans(scopeSpansList);
                getRejectedSpans().inc(resourceSpanCount);
                if (storage.reportsRejections()) {
                    rejectedSpans += resourceSpanCount;
                }
                continue;
            }
            // The service the analysis names and the store files the span under; the resource attributes,
            // service.name among them, stay as sent.
            final String groupedServiceName = OTLPServiceGroup.apply(serviceName, resourceTags, serviceGroupKeys);
            final Map<String, String> storedTags = export.openResource(resourceSpans, resourceTags);

            for (final ScopeSpans scopeSpans : scopeSpansList) {
                export.openScope(scopeSpans, storedTags);
                final InstrumentationScope scope = scopeSpans.getScope();
                final Map<String, String> scopeTags = OTLPValues.toStringMap(scope.getAttributesList());
                for (final Span span : scopeSpans.getSpansList()) {
                    // One span's failure costs that span only, and is counted.
                    final long startNanos = System.nanoTime();
                    try {
                        processSpan(export, span, scopeTags, scope.getName(), scope.getVersion(),
                                    groupedServiceName, resourceTags, storedTags);
                    } catch (Exception e) {
                        log.warn("Processing OTLP span {} failed, dropping it: {}",
                                 OTLPValues.hexId(span.getSpanId()), e.getMessage());
                        droppedSpans.count(TraceSpansDropped.Reason.PROCESS_FAILED, 1);
                    } finally {
                        TraceInLatency.observeSpans(spanLatency, startNanos, 1);
                    }
                }
            }
        }
        export.finish();

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
     * Run one span through the phase-one listeners and the rules, and hand it to the storage unless either chose
     * not to persist it.
     *
     * @param resourceTags the resource attributes as sent, which the rules read
     * @param storedTags   the resource tags the storage opened the resource with, which the listeners see
     */
    private void processSpan(final OTLPTraceStorage.Export export,
                             final Span span,
                             final Map<String, String> scopeTags,
                             final String scopeName,
                             final String scopeVersion,
                             final String groupedServiceName,
                             final Map<String, String> resourceTags,
                             final Map<String, String> storedTags) {
        final SpanListenerResult otlpResult = getSpanListenerManager()
            .notifyOTLPPhase(new OTLPSpanReaderImpl(span), groupedServiceName, storedTags, scopeName, scopeVersion);

        // TAL runs on every span whatever the storage and whatever the listeners decided, and before the storage
        // applies head sampling, so metrics count what the SDK sent rather than what OAP chose to store.
        final SpanAnalysisResult analysis = analyze(
            span, groupedServiceName, resourceTags, scopeTags, scopeName, scopeVersion);

        if (!otlpResult.isShouldPersist() || !analysis.isPersist()) {
            return;
        }

        // What the listeners and the rules injected, and only that: the native store appends these to the span as
        // attributes, so the resource attributes must not be among them. Last writer wins, and a rule's tag is
        // written after a listener's.
        final Map<String, String> injectedTags;
        if (!otlpResult.getAdditionalTags().isEmpty() || !analysis.getAdditionalTags().isEmpty()) {
            injectedTags = new HashMap<>(otlpResult.getAdditionalTags());
            injectedTags.putAll(analysis.getAdditionalTags());
        } else {
            injectedTags = Collections.emptyMap();
        }
        export.store(span, groupedServiceName, storedTags, injectedTags, analysis.isEnforced());
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
    private SpanAnalysisResult analyze(final Span span,
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
                spanMetadataOf(span, serviceName, resourceTags, scopeName, scopeVersion, storage.traceStorage()),
                span, resourceTags, scopeTags);
        } catch (final Exception e) {
            log.warn("TAL analysis failed for span {}; the span is unaffected",
                     OTLPValues.hexId(span.getSpanId()), e);
            return SpanAnalysisResult.CONTINUE;
        }
    }

    /**
     * The protocol-neutral view a rule reads as {@code meta.*}.
     *
     * @param traceStorage how the span is stored, {@code otlp} or {@code zipkin}, which a rule reads as
     *                     {@code meta.traceStorage}
     */
    static SpanMetadata spanMetadataOf(final Span span,
                                               final String serviceName,
                                               final Map<String, String> resourceTags,
                                               final String scopeName,
                                               final String scopeVersion,
                                               final String traceStorage) {
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
                           .traceStorage(traceStorage)
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
     * The service a resource's spans belong to: the first of {@link OTLPSpanRecord#SERVICE_NAME_RESOURCE_KEYS}
     * present, empty when none is.
     */
    private static String resolveServiceName(final Map<String, String> resourceTags) {
        for (final String key : OTLPSpanRecord.SERVICE_NAME_RESOURCE_KEYS) {
            final String name = resourceTags.get(key);
            if (StringUtil.isNotEmpty(name)) {
                return name;
            }
        }
        return "";
    }

    private SpanListenerManager getSpanListenerManager() {
        if (spanListenerManager == null) {
            spanListenerManager = manager.find(CoreModule.NAME).provider().getService(SpanListenerManager.class);
        }
        return spanListenerManager;
    }
}
