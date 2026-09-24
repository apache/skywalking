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
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The one place OTLP attribute values and ids are rendered as strings. The Zipkin conversion, the native span
 * index, the span listeners and the tag autocomplete all read the same spelling, so a value searched for on one
 * path matches what another path stored.
 * Lives beside the OTLP protos so the receiver, which writes the {@code key=value} index, and the TraceQL plugin,
 * which re-renders attributes to match against it, spell values identically.
 */
public final class OTLPValues {
    private OTLPValues() {
    }

    /**
     * Render an attribute value as text: scalars as their text form, arrays joined by {@code ,}, key-value lists
     * as a JSON object, bytes as base64. An unset value renders as the empty string.
     */
    public static String render(final AnyValue value) {
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
            return value.getArrayValue().getValuesList().stream().map(OTLPValues::render).collect(Collectors.joining(","));
        } else if (value.hasIntValue()) {
            return String.valueOf(value.getIntValue());
        } else if (value.hasKvlistValue()) {
            return renderKvList(value.getKvlistValue().getValuesList()).toString();
        } else if (value.hasBytesValue()) {
            return new String(Base64.getEncoder().encode(value.getBytesValue().toByteArray()), StandardCharsets.UTF_8);
        }
        return "";
    }

    public static JsonObject renderKvList(final List<KeyValue> keyValues) {
        final JsonObject json = new JsonObject();
        for (final KeyValue keyValue : keyValues) {
            json.addProperty(keyValue.getKey(), render(keyValue.getValue()));
        }
        return json;
    }

    /**
     * Attributes as a string map; the first occurrence of a duplicated key wins.
     */
    public static Map<String, String> toStringMap(final List<KeyValue> attributes) {
        return attributes.stream().collect(Collectors.toMap(
            KeyValue::getKey,
            keyValue -> render(keyValue.getValue()),
            (v1, v2) -> v1
        ));
    }

    /**
     * A trace, span or link id as lowercase hex; the empty string for a missing id.
     */
    public static String hexId(final ByteString id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        final char[] hex = new char[id.size() * 2];
        for (int i = 0; i < id.size(); i++) {
            final int value = id.byteAt(i) & 0xff;
            hex[i * 2] = Character.forDigit(value >>> 4, 16);
            hex[i * 2 + 1] = Character.forDigit(value & 0x0f, 16);
        }
        return new String(hex);
    }
}
