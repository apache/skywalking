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
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Status;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class OpenTelemetryTraceHandler
    extends TraceServiceGrpc.TraceServiceImplBase
    implements Handler {
    private ModuleManager manager;
    private SpanForwardService forwardService;

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
    }

    @Override
    public String type() {
        return "otlp-traces";
    }

    @Override
    public void active() throws ModuleStartException {
        GRPCHandlerRegister grpcHandlerRegister = manager.find(SharingServerModule.NAME)
            .provider()
            .getService(GRPCHandlerRegister.class);
        grpcHandlerRegister.addHandler(this);
    }

    @Override
    public void export(ExportTraceServiceRequest request, StreamObserver<ExportTraceServiceResponse> responseObserver) {
        final ArrayList<Span> result = new ArrayList<>();

        try (final var unused = getProcessHistogram().createTimer()) {
            request.getResourceSpansList().forEach(resourceSpans -> {
                final Resource resource = resourceSpans.getResource();
                final List<ScopeSpans> scopeSpansList = resourceSpans.getScopeSpansList();
                if (resource.getAttributesCount() == 0 && scopeSpansList.size() == 0) {
                    return;
                }
                final Map<String, String> resourceTags = convertAttributeToMap(resource.getAttributesList());
                String serviceName = extractZipkinServiceName(resourceTags);
                if (StringUtil.isEmpty(serviceName)) {
                    log.warn("No service name found in resource attributes, discarding the trace");
                    return;
                }

                try {
                    for (ScopeSpans scopeSpans : scopeSpansList) {
                        extractScopeTag(scopeSpans.getScope(), resourceTags);
                        for (io.opentelemetry.proto.trace.v1.Span span : scopeSpans.getSpansList()) {
                            Span zipkinSpan = convertSpan(span, serviceName, resourceTags);
                            result.add(zipkinSpan);
                        }
                    }
                } catch (Exception e) {
                    log.warn("convert span error, discarding the span: {}", e.getMessage());
                }
            });
            final var processedSpans = getForwardService().send(result);
            if (processedSpans.size() < result.size()) {
                getDroppedSpans().inc(result.size() - processedSpans.size());
            }
        }

        responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
        responseObserver.onCompleted();
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

            final JsonObject attrObj = convertToString(event.getAttributesList());
            spanBuilder.addAnnotation(eventTime,
                event.getName() + "|" + attrObj + "|" + event.getDroppedAttributesCount());
        });
    }

    private void convertLink(Map<String, String> tags, List<io.opentelemetry.proto.trace.v1.Span.Link> links) {
        for (int i = 0; i < links.size(); i++) {
            final io.opentelemetry.proto.trace.v1.Span.Link link = links.get(i);
            tags.put("otlp.link." + i,
                idToHexString(link.getTraceId()) + "|" + idToHexString(link.getSpanId()) + "|" +
                link.getTraceState() + "|" + convertToString(link.getAttributesList()) + "|" +
                link.getDroppedAttributesCount());
        }
    }

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
        result.putAll(convertAttributeToMap(spanAttrs));
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

    private Map<String, String> convertAttributeToMap(List<KeyValue> attrs) {
        return attrs.stream().collect(Collectors.toMap(
            KeyValue::getKey,
            attributeKeyValue -> convertToString(attributeKeyValue.getValue()),
            (v1, v2) -> v1
        ));
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

    private String convertToString(AnyValue value) {
        if (value == null) {
            return "";
        }

        if (value.hasBoolValue()) {
            return String.valueOf(value.getBoolValue());
        } else if (value.hasDoubleValue()) {
            return String.valueOf(value.getDoubleValue());
        } else if (value.hasStringValue()) {
            return value.getStringValue();
        } else if (value.hasArrayValue()) {
            return value.getArrayValue().getValuesList().stream().map(this::convertToString).collect(Collectors.joining(","));
        } else if (value.hasIntValue()) {
            return String.valueOf(value.getIntValue());
        } else if (value.hasKvlistValue()) {
            final JsonObject kvObj = convertToString(value.getKvlistValue().getValuesList());
            return kvObj.toString();
        } else if (value.hasBytesValue()) {
            return new String(Base64.getEncoder().encode(value.getBytesValue().toByteArray()), StandardCharsets.UTF_8);
        }
        return "";
    }

    private JsonObject convertToString(List<KeyValue> keyValues) {
        final JsonObject json = new JsonObject();
        for (KeyValue keyValue : keyValues) {
            json.addProperty(keyValue.getKey(), convertToString(keyValue.getValue()));
        }
        return json;
    }

    private SpanForwardService getForwardService() {
        if (forwardService == null) {
            forwardService = manager.find(ZipkinReceiverModule.NAME).provider().getService(SpanForwardService.class);
        }
        return forwardService;
    }
}
