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

package org.apache.skywalking.oap.query.traceql.converter;

import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OTLPValues;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Re-evaluates an {@link OTLPTraceQueryCondition} against one stored span, so a search result lists the spans that
 * matched instead of every span of the matched trace, as Tempo does. The storage applied the same conditions per
 * span already; this mirrors them over the decoded {@code ResourceSpans}, resolving service and instance from the
 * resource the way the receiver did and rendering attributes the way the {@code key=value} index did.
 */
public final class OTLPSpanMatcher {
    private final OTLPTraceQueryCondition condition;
    private final Set<String> tags = new HashSet<>();

    public OTLPSpanMatcher(final OTLPTraceQueryCondition condition) {
        this.condition = condition;
        if (condition.getTags() != null) {
            for (final Tag tag : condition.getTags()) {
                tags.add(tag.getKey() + "=" + tag.getValue());
            }
        }
    }

    public boolean matches(final Resource resource, final InstrumentationScope scope, final Span span) {
        if (differs(condition.getServiceName(), attribute(resource.getAttributesList(), OTLPSpanRecord.SERVICE_NAME_RESOURCE_KEYS))
            || differs(condition.getServiceInstance(), attribute(resource.getAttributesList(), OTLPSpanRecord.SERVICE_INSTANCE_RESOURCE_KEYS))
            || differs(condition.getScopeName(), scope.getName())
            || differs(condition.getSpanName(), span.getName())
            || differs(condition.getPeerService(), attribute(span.getAttributesList(), List.of(OTLPSpanRecord.PEER_SERVICE_ATTRIBUTE)))) {
            return false;
        }
        if (condition.getKind() != null && condition.getKind() != span.getKindValue()) {
            return false;
        }
        if (condition.getStatusCode() != null && condition.getStatusCode() != span.getStatus().getCodeValue()) {
            return false;
        }
        final long duration = span.getEndTimeUnixNano() - span.getStartTimeUnixNano();
        if (condition.getMinDurationNanos() > 0 && duration < condition.getMinDurationNanos()) {
            return false;
        }
        if (condition.getMaxDurationNanos() > 0 && duration > condition.getMaxDurationNanos()) {
            return false;
        }
        if (tags.isEmpty()) {
            return true;
        }
        final Set<String> rendered = new HashSet<>();
        for (final KeyValue attribute : resource.getAttributesList()) {
            rendered.add(attribute.getKey() + "=" + OTLPValues.render(attribute.getValue()));
        }
        for (final KeyValue attribute : span.getAttributesList()) {
            rendered.add(attribute.getKey() + "=" + OTLPValues.render(attribute.getValue()));
        }
        return rendered.containsAll(tags);
    }

    private static boolean differs(final String expected, final String actual) {
        return StringUtil.isNotEmpty(expected) && !expected.equals(actual);
    }

    /**
     * The rendered value of the first key present with a non-empty value, or an empty string.
     */
    static String attribute(final List<KeyValue> attributes, final List<String> keys) {
        for (final String key : keys) {
            for (final KeyValue attribute : attributes) {
                if (key.equals(attribute.getKey())) {
                    final String value = OTLPValues.render(attribute.getValue());
                    if (StringUtil.isNotEmpty(value)) {
                        return value;
                    }
                }
            }
        }
        return "";
    }
}
