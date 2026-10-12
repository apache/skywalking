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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.spi;

import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.Collections;
import java.util.Map;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OTLPValues;

/**
 * The OTLP span as a TAL input, under the alias {@code otlp}. It is the default a rule gets when it declares no
 * {@code inputType}.
 */
public class OtlpTALSourceTypeProvider implements TALSourceTypeProvider {
    @Override
    public String alias() {
        return "otlp";
    }

    @Override
    public Class<?> inputType() {
        return Span.class;
    }

    @Override
    public Map<String, String> spanAttributes(final Object input) {
        return input instanceof Span
            ? OTLPValues.toStringMap(((Span) input).getAttributesList()) : Collections.emptyMap();
    }

    /**
     * The instrumentation scope is carried by the enclosing {@code ScopeSpans}, not by the span, so the receiver
     * passes its attributes to the analysis directly and this returns empty.
     */
    @Override
    public Map<String, String> scopeAttributes(final Object input) {
        return Collections.emptyMap();
    }

    @Override
    public Object event(final Object input, final String name) {
        if (!(input instanceof Span)) {
            return null;
        }
        for (final Span.Event event : ((Span) input).getEventsList()) {
            if (event.getName().equals(name)) {
                return event;
            }
        }
        return null;
    }

    @Override
    public String eventAttribute(final Object input, final String eventName, final String key) {
        final Object event = event(input, eventName);
        if (!(event instanceof Span.Event)) {
            return "";
        }
        // One scan per key rather than a map per key: eventAttr() probes its keys one at a time. The first
        // occurrence of a duplicated key wins, as in OTLPValues.toStringMap.
        for (final KeyValue attribute : ((Span.Event) event).getAttributesList()) {
            if (key.equals(attribute.getKey())) {
                return OTLPValues.render(attribute.getValue());
            }
        }
        return "";
    }
}
