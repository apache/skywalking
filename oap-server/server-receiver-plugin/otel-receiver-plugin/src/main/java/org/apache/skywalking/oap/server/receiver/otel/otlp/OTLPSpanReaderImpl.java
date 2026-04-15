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

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;
import org.apache.skywalking.oap.server.core.otel.OTLPSpanReader;

/**
 * {@link OTLPSpanReader} implementation wrapping the real OTLP {@link Span} proto.
 */
public class OTLPSpanReaderImpl implements OTLPSpanReader {
    private final Span span;

    public OTLPSpanReaderImpl(final Span span) {
        this.span = span;
    }

    @Override
    public String spanName() {
        return span.getName();
    }

    @Override
    public String spanKind() {
        return span.getKind().name();
    }

    @Override
    public long startTimeNanos() {
        return span.getStartTimeUnixNano();
    }

    @Override
    public long endTimeNanos() {
        return span.getEndTimeUnixNano();
    }

    @Override
    public String getAttribute(final String key) {
        for (final KeyValue kv : span.getAttributesList()) {
            if (key.equals(kv.getKey())) {
                return convertValue(kv.getValue());
            }
        }
        return "";
    }

    private static String convertValue(final AnyValue value) {
        if (value.hasStringValue()) {
            return value.getStringValue();
        }
        if (value.hasIntValue()) {
            return String.valueOf(value.getIntValue());
        }
        if (value.hasDoubleValue()) {
            return String.valueOf(value.getDoubleValue());
        }
        if (value.hasBoolValue()) {
            return String.valueOf(value.getBoolValue());
        }
        return "";
    }
}
