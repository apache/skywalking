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

import com.google.common.util.concurrent.RateLimiter;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.core.otlp.source.OTLPService;
import org.apache.skywalking.oap.server.core.otlp.source.OTLPServiceRelation;
import org.apache.skywalking.oap.server.core.otlp.source.OTLPServiceSpan;
import org.apache.skywalking.oap.server.core.otlp.source.OTLPSpan;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TagAutocomplete;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;

/**
 * Stores OTLP spans natively. The counterpart of the Zipkin receiver's {@code SpanForward}: head sampling and the
 * rate limit, the index columns, the {@code key=value} search index, tag autocomplete, the name catalogs, and
 * the {@code data_binary} that keeps the span exactly as the SDK sent it.
 */
@Slf4j
public class OTLPSpanForward {
    /**
     * The attribute the remote-service catalog is built from, per the OpenTelemetry semantic conventions.
     */
    public static final String PEER_SERVICE = OTLPSpanRecord.PEER_SERVICE_ATTRIBUTE;
    /**
     * The tag autocomplete key the instrumentation scope name is always published under, so TraceQL can list
     * {@code otel.scope.name} values without configuration. Like every autocomplete key it carries its TraceQL
     * scope, so the TraceQL datasource lists each key under the scope a query must name it with.
     */
    public static final String SCOPE_NAME_TAG = OTLPSpanRecord.SPAN_TAG_PREFIX + "otel.scope.name";
    /**
     * The tag autocomplete key the resolved instance is always published under, so TraceQL's
     * {@code resource.service.instance.id} dropdown fills regardless of {@code otlpTraceSearchableTags}.
     */
    public static final String INSTANCE_TAG = OTLPSpanRecord.RESOURCE_TAG_PREFIX + "service.instance.id";
    private static final int TRACE_ID_LENGTH = 16;

    private final ModuleManager moduleManager;
    private final Set<String> searchableTagKeys;
    private final long samplerBoundary;
    private final boolean sampleAll;
    private final RateLimiter rateLimiter;
    private NamingControl namingControl;
    private SourceReceiver receiver;

    public OTLPSpanForward(final OtelMetricReceiverConfig config, final ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.searchableTagKeys = new HashSet<>(config.getOtlpTraceSearchableTags());
        final float sampleRate = (float) config.getOtlpTraceSampleRate() / 10000;
        this.samplerBoundary = (long) (Long.MAX_VALUE * sampleRate);
        this.sampleAll = config.getOtlpTraceSampleRate() == 10000;
        this.rateLimiter = config.getOtlpTraceMaxSpansPerSecond() > 0
            ? RateLimiter.create(config.getOtlpTraceMaxSpansPerSecond()) : null;
    }

    /**
     * Index and store one span.
     *
     * @param resource       the span's resource, stored verbatim
     * @param scope          the span's instrumentation scope, stored verbatim
     * @param span           the span exactly as received
     * @param serviceName    the service name resolved from the resource, not yet formatted
     * @param resourceTags   the resource attributes rendered as strings, see {@link OTLPValues#toStringMap}
     * @param additionalTags attributes the span listeners injected; appended to the stored span as strings, the
     *                       only way the stored span differs from the wire span
     * @return false when head sampling or the rate limit dropped the span
     */
    public boolean send(final Resource resource,
                        final String resourceSchemaUrl,
                        final InstrumentationScope scope,
                        final String scopeSchemaUrl,
                        final Span span,
                        final String serviceName,
                        final Map<String, String> resourceTags,
                        final Map<String, String> additionalTags) {
        if (!sampled(span)) {
            return false;
        }
        final Span stored = appendAttributes(span, additionalTags);
        final String formattedServiceName = getNamingControl().formatServiceName(serviceName);
        final long startTimeMillis = TimeUnit.NANOSECONDS.toMillis(stored.getStartTimeUnixNano());
        final long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(startTimeMillis);

        final OTLPSpan source = new OTLPSpan();
        source.setTraceId(OTLPValues.hexId(stored.getTraceId()));
        source.setSpanId(OTLPValues.hexId(stored.getSpanId()));
        source.setParentSpanId(OTLPValues.hexId(stored.getParentSpanId()));
        source.setServiceName(formattedServiceName);
        final String serviceInstance = OTLPSpanRecord.SERVICE_INSTANCE_RESOURCE_KEYS.stream()
                                                                                  .map(resourceTags::get)
                                                                                  .filter(StringUtil::isNotEmpty)
                                                                                  .findFirst()
                                                                                  .orElse("");
        source.setServiceInstance(serviceInstance);
        source.setScopeName(scope.getName());
        source.setName(getNamingControl().formatEndpointName(formattedServiceName, stored.getName()));
        source.setKind(stored.getKindValue());
        source.setStatusCode(stored.getStatus().getCodeValue());
        source.setStartTime(startTimeMillis);
        source.setDuration(Math.max(0L, stored.getEndTimeUnixNano() - stored.getStartTimeUnixNano()));
        source.setTimeBucket(TimeBucket.getRecordTimeBucket(startTimeMillis));

        final List<String> tags = new ArrayList<>(resource.getAttributesCount() + stored.getAttributesCount());
        indexAttributes(resource.getAttributesList(), OTLPSpanRecord.RESOURCE_TAG_PREFIX, tags, minuteTimeBucket);
        final String peerService = indexAttributes(
            stored.getAttributesList(), OTLPSpanRecord.SPAN_TAG_PREFIX, tags, minuteTimeBucket);
        source.setTags(tags);
        if (StringUtil.isNotEmpty(peerService)) {
            source.setPeerService(getNamingControl().formatServiceName(peerService));
        }
        if (StringUtil.isNotEmpty(scope.getName())) {
            addAutocompleteTag(minuteTimeBucket, SCOPE_NAME_TAG, scope.getName());
        }
        if (StringUtil.isNotEmpty(serviceInstance)) {
            addAutocompleteTag(minuteTimeBucket, INSTANCE_TAG, serviceInstance);
        }

        source.setDataBinary(ResourceSpans.newBuilder()
                                          .setResource(resource)
                                          .setSchemaUrl(resourceSchemaUrl)
                                          .addScopeSpans(ScopeSpans.newBuilder()
                                                                   .setScope(scope)
                                                                   .setSchemaUrl(scopeSchemaUrl)
                                                                   .addSpans(stored))
                                          .build()
                                          .toByteArray());
        getReceiver().receive(source);

        final OTLPService service = new OTLPService();
        service.setServiceName(formattedServiceName);
        service.setTimeBucket(minuteTimeBucket);
        getReceiver().receive(service);

        final OTLPServiceSpan serviceSpan = new OTLPServiceSpan();
        serviceSpan.setServiceName(formattedServiceName);
        serviceSpan.setSpanName(source.getName());
        serviceSpan.setTimeBucket(minuteTimeBucket);
        getReceiver().receive(serviceSpan);

        if (StringUtil.isNotEmpty(source.getPeerService())) {
            final OTLPServiceRelation relation = new OTLPServiceRelation();
            relation.setServiceName(formattedServiceName);
            relation.setPeerService(source.getPeerService());
            relation.setTimeBucket(minuteTimeBucket);
            getReceiver().receive(relation);
        }
        return true;
    }

    /**
     * Head sampling on the low 64 bits of the trace id, the arithmetic {@code SpanForward.getSampledTraces} uses,
     * so a trace is kept or dropped as a whole on every OAP node. The rate limit is applied first.
     */
    private boolean sampled(final Span span) {
        if (rateLimiter != null && !rateLimiter.tryAcquire()) {
            log.debug("Span dropped due to the maximum spans per second limit: {}", OTLPValues.hexId(span.getSpanId()));
            return false;
        }
        if (sampleAll) {
            return true;
        }
        final byte[] traceId = span.getTraceId().toByteArray();
        if (traceId.length < 8) {
            return true;
        }
        final int lowOffset = traceId.length >= TRACE_ID_LENGTH ? 8 : traceId.length - 8;
        long low = ByteBuffer.wrap(traceId, lowOffset, 8).getLong();
        low = low == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(low);
        return low <= samplerBoundary;
    }

    private static Span appendAttributes(final Span span, final Map<String, String> additionalTags) {
        if (additionalTags == null || additionalTags.isEmpty()) {
            return span;
        }
        final Span.Builder builder = span.toBuilder();
        additionalTags.forEach((key, value) -> builder.addAttributes(
            KeyValue.newBuilder()
                    .setKey(key)
                    .setValue(AnyValue.newBuilder().setStringValue(value))
        ));
        return builder.build();
    }

    /**
     * Append every attribute as {@code <scope>.key=value} to the search index, skipping entries longer than
     * {@link Tag#TAG_LENGTH}, and publish the attributes {@code otlpTraceSearchableTags} names with this scope to
     * tag autocomplete under the same prefix, so the TraceQL datasource lists each key under the scope a query must
     * name it with.
     *
     * @param scopePrefix {@link OTLPSpanRecord#RESOURCE_TAG_PREFIX} or {@link OTLPSpanRecord#SPAN_TAG_PREFIX}
     * @return the {@code peer.service} value, or null when the attributes carry none
     */
    private String indexAttributes(final List<KeyValue> attributes,
                                   final String scopePrefix,
                                   final List<String> tags,
                                   final long minuteTimeBucket) {
        String peerService = null;
        for (final KeyValue attribute : attributes) {
            final String value = OTLPValues.render(attribute.getValue());
            if (PEER_SERVICE.equals(attribute.getKey())) {
                peerService = value;
            }
            final String tag = scopePrefix + attribute.getKey() + "=" + value;
            if (tag.length() > Tag.TAG_LENGTH) {
                if (log.isDebugEnabled()) {
                    log.debug("Span attribute {} length > {}, not indexed", tag, Tag.TAG_LENGTH);
                }
                continue;
            }
            tags.add(tag);
            final String scopedKey = scopePrefix + attribute.getKey();
            if (searchableTagKeys.contains(scopedKey)) {
                addAutocompleteTag(minuteTimeBucket, scopedKey, value);
            }
        }
        return peerService;
    }

    private void addAutocompleteTag(final long minuteTimeBucket, final String key, final String value) {
        final TagAutocomplete tagAutocomplete = new TagAutocomplete();
        tagAutocomplete.setTagKey(key);
        tagAutocomplete.setTagValue(value);
        tagAutocomplete.setTagType(TagType.OTLP);
        tagAutocomplete.setTimeBucket(minuteTimeBucket);
        getReceiver().receive(tagAutocomplete);
    }

    private NamingControl getNamingControl() {
        if (namingControl == null) {
            namingControl = moduleManager.find(CoreModule.NAME).provider().getService(NamingControl.class);
        }
        return namingControl;
    }

    private SourceReceiver getReceiver() {
        if (receiver == null) {
            receiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        }
        return receiver;
    }
}
