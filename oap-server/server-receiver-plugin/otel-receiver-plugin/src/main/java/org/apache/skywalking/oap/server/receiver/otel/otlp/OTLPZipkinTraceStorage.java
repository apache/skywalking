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
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Status;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.core.trace.TraceSpansDropped;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.SpanForwardService;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverModule;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import zipkin2.Endpoint;
import zipkin2.Span;

/**
 * {@code otlpTraceStorage: zipkin}: each span is converted to a Zipkin span, and the spans of an export request are
 * handed to the Zipkin receiver's {@link SpanForwardService} as one batch, which samples, rate-limits and stores
 * them, counting the ones it drops under the OTLP protocol the request came in on.
 */
final class OTLPZipkinTraceStorage implements OTLPTraceStorage {
    /**
     * The handler times each span, conversion included, so the Zipkin receiver must not observe the same span into
     * {@code trace_in_latency} again.
     */
    private static final HistogramMetrics NOT_TIMED = new HistogramMetrics() {
        @Override
        public void observe(final double value) {
        }
    };
    private static final String SERVICE_NAME_KEY = "service.name";
    private static final String SERVICE_NAME_SOURCE_TAG = "otlp.service.name.source";

    private final ModuleManager manager;
    private SpanForwardService forwardService;

    OTLPZipkinTraceStorage(final ModuleManager manager) {
        this.manager = manager;
    }

    @Override
    public String traceStorage() {
        return OtelMetricReceiverConfig.OTLP_TRACE_STORAGE_ZIPKIN;
    }

    /**
     * The Zipkin mode has always answered an empty response.
     */
    @Override
    public boolean reportsRejections() {
        return false;
    }

    @Override
    public Export newExport(final TraceSpansDropped droppedSpans) {
        return new ZipkinExport(droppedSpans);
    }

    private final class ZipkinExport implements Export {
        private final TraceSpansDropped droppedSpans;
        private final List<Span> batch = new ArrayList<>();

        private ZipkinExport(final TraceSpansDropped droppedSpans) {
            this.droppedSpans = droppedSpans;
        }

        /**
         * @return a copy of the resource tags, the analysis reading them as sent, with a service name taken from a
         * fallback key moved into an {@code otlp.service.name.source} tag naming that key.
         */
        @Override
        public Map<String, String> openResource(final ResourceSpans resourceSpans,
                                                final Map<String, String> resourceTags) {
            final Map<String, String> storedTags = new HashMap<>(resourceTags);
            for (final String key : OTLPSpanRecord.SERVICE_NAME_RESOURCE_KEYS) {
                if (StringUtil.isNotEmpty(storedTags.get(key))) {
                    if (!SERVICE_NAME_KEY.equals(key)) {
                        storedTags.remove(key);
                        storedTags.put(SERVICE_NAME_SOURCE_TAG, key);
                    }
                    break;
                }
            }
            return storedTags;
        }

        @Override
        public void openScope(final ScopeSpans scopeSpans, final Map<String, String> storedTags) {
            extractScopeTag(scopeSpans.getScope(), storedTags);
        }

        /**
         * Convert the span and add it to the batch. The Zipkin span carries the resource attributes as tags, merged
         * into a per-span copy so the injected tags do not leak into the next span of the resource.
         */
        @Override
        public void store(final io.opentelemetry.proto.trace.v1.Span span, final String serviceName,
                          final Map<String, String> storedTags, final Map<String, String> injectedTags,
                          final boolean enforced) {
            final Map<String, String> spanTags;
            if (injectedTags.isEmpty()) {
                spanTags = storedTags;
            } else {
                spanTags = new HashMap<>(storedTags);
                spanTags.putAll(injectedTags);
            }
            batch.add(convertSpan(span, serviceName, spanTags, enforced));
        }

        @Override
        public void finish() {
            getForwardService().send(batch, NOT_TIMED, droppedSpans);
        }
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

    private SpanForwardService getForwardService() {
        if (forwardService == null) {
            forwardService = manager.find(ZipkinReceiverModule.NAME).provider().getService(SpanForwardService.class);
        }
        return forwardService;
    }
}
