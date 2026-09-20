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
    private static final String NO_SERVICE_NAME_MESSAGE =
        "resource carries none of service.name, faas.name, k8s.deployment.name, process.executable.name";

    private ModuleManager manager;
    private OtelMetricReceiverConfig config;
    private SpanForwardService forwardService;
    private OTLPSpanForward otlpSpanForward;
    private SpanListenerManager spanListenerManager;

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

                try {
                    for (ScopeSpans scopeSpans : scopeSpansList) {
                        final InstrumentationScope scope = scopeSpans.getScope();
                        final String scopeName = scope.getName();
                        final String scopeVersion = scope.getVersion();
                        if (!nativeStorage) {
                            extractScopeTag(scope, resourceTags);
                        }
                        for (io.opentelemetry.proto.trace.v1.Span span : scopeSpans.getSpansList()) {
                            if (nativeStorage) {
                                // The response reports the batch as accepted, so one span's failure must not take the
                                // rest of the export with it; what is not stored is counted as dropped.
                                try {
                                    storeNative(resourceSpans, scopeSpans, span, serviceName, resourceTags);
                                } catch (Exception e) {
                                    log.warn("Storing OTLP span {} failed, dropping it: {}",
                                             OTLPValues.hexId(span.getSpanId()), e.getMessage());
                                    getDroppedSpans().inc(1);
                                }
                                continue;
                            }
                            final OTLPSpanReaderImpl reader = new OTLPSpanReaderImpl(span);
                            // Phase 1: notify OTLP span listeners before conversion or storage
                            final SpanListenerResult otlpResult = getSpanListenerManager()
                                .notifyOTLPPhase(reader, resourceTags, scopeName, scopeVersion);

                            // If any listener vetoed persistence, skip conversion and storage
                            if (!otlpResult.isShouldPersist()) {
                                continue;
                            }

                            // Per-span tag copy to avoid leaking listener tags into
                            // subsequent spans that share the same resourceTags map
                            final Map<String, String> spanTags;
                            if (!otlpResult.getAdditionalTags().isEmpty()) {
                                spanTags = new HashMap<>(resourceTags);
                                spanTags.putAll(otlpResult.getAdditionalTags());
                            } else {
                                spanTags = resourceTags;
                            }

                            Span zipkinSpan = convertSpan(span, serviceName, spanTags);
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

    private static long countSpans(final List<ScopeSpans> scopeSpansList) {
        long count = 0;
        for (final ScopeSpans scopeSpans : scopeSpansList) {
            count += scopeSpans.getSpansCount();
        }
        return count;
    }

    private Span convertSpan(io.opentelemetry.proto.trace.v1.Span span, String serviceName, Map<String, String> resourceTags) {
        final Span.Builder spanBuilder = Span.newBuilder();
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
     * The native path for one span: both listener phases, then the store. Exceptions propagate to the caller, which
     * drops this span only.
     */
    private void storeNative(final ResourceSpans resourceSpans,
                             final ScopeSpans scopeSpans,
                             final io.opentelemetry.proto.trace.v1.Span span,
                             final String serviceName,
                             final Map<String, String> resourceTags) {
        final InstrumentationScope scope = scopeSpans.getScope();
        final OTLPSpanReaderImpl reader = new OTLPSpanReaderImpl(span);
        // Phase 1: the listeners every OTLP span goes through
        final SpanListenerResult otlpResult = getSpanListenerManager()
            .notifyOTLPPhase(reader, resourceTags, scope.getName(), scope.getVersion());
        if (!otlpResult.isShouldPersist()) {
            return;
        }
        // Phase 2 for natively stored spans: no Zipkin span will ever exist for them.
        final SpanListenerResult storedResult = getSpanListenerManager()
            .notifyNativeOTLPPhase(reader, resourceTags, scope.getName(), scope.getVersion());
        if (!storedResult.isShouldPersist()) {
            return;
        }
        final Map<String, String> injectedTags = new HashMap<>(otlpResult.getAdditionalTags());
        injectedTags.putAll(storedResult.getAdditionalTags());
        if (!otlpSpanForward.send(
            resourceSpans.getResource(), resourceSpans.getSchemaUrl(), scope, scopeSpans.getSchemaUrl(),
            span, serviceName, resourceTags, injectedTags)) {
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
