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

package org.apache.skywalking.oap.log.analyzer.v2.dsldebug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import java.util.Base64;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.server.core.dsldebug.LogDataDebugDump;
import org.apache.skywalking.oap.server.core.dsldebug.ToJson;

/**
 * Per-type renderers for LAL input/output payloads. All overloads are
 * statically typed — callers hold the typed reference (or narrow it
 * via {@code instanceof}) and the compiler picks the right overload.
 * No runtime {@code Object} dispatcher: dispatch belongs at the call
 * site where the static type is available.
 *
 * <p>Supported families:
 * <ul>
 *   <li>{@link ToJson} — explicit opt-in. Custom POJO inputs / outputs
 *       implement this and call {@link #toJson(ToJson)}.</li>
 *   <li>{@link LogData} / {@link LogData.Builder} — native renderer
 *       that surfaces service / instance / endpoint / timestamp / tags
 *       / trace context plus a body-type-aware view of the body
 *       (TEXT / JSON / YAML).</li>
 *   <li>Any {@link Message} / {@link Message.Builder} — protobuf
 *       {@link JsonFormat} printer. Walks the proto descriptor (a
 *       structural, compile-time form, not Java reflection) and
 *       emits the canonical proto3 JSON shape.</li>
 * </ul>
 *
 * <p>Each overload returns a JSON literal string suitable for inline
 * embedding in a parent object payload.
 *
 * <p>Use {@link #unknownTypeHint(Object)} when none of the supported
 * families match — the helper produces an operator-friendly hint
 * object pointing at the missing {@link ToJson} implementation.
 */
public final class LalPayloadDebugDump {

    private static final String ANY_TYPE_NAME = Any.getDescriptor().getFullName();

    private static final String VALUE_TYPE_NAME = Value.getDescriptor().getFullName();

    /**
     * Well-known type descriptors so {@link JsonFormat} can resolve and print
     * {@code google.protobuf.Any}-typed fields (e.g. Envoy ALS
     * {@code filter_state_objects}, which is {@code map<string, Any>}). Without
     * a registry a single unresolvable {@code Any} makes the whole message
     * un-printable. Anything outside this set is handled by
     * {@link #sanitizeUnresolvableAny(Message)} below.
     */
    private static final JsonFormat.TypeRegistry TYPE_REGISTRY =
        JsonFormat.TypeRegistry.newBuilder()
            .add(BytesValue.getDescriptor())
            .add(StringValue.getDescriptor())
            .add(BoolValue.getDescriptor())
            .add(Int32Value.getDescriptor())
            .add(Int64Value.getDescriptor())
            .add(UInt32Value.getDescriptor())
            .add(UInt64Value.getDescriptor())
            .add(FloatValue.getDescriptor())
            .add(DoubleValue.getDescriptor())
            .add(Struct.getDescriptor())
            .add(Value.getDescriptor())
            .add(ListValue.getDescriptor())
            .add(Timestamp.getDescriptor())
            .add(Duration.getDescriptor())
            .build();

    private static final JsonFormat.Printer JSON_PRINTER =
        JsonFormat.printer()
            .usingTypeRegistry(TYPE_REGISTRY)
            .omittingInsignificantWhitespace();

    private LalPayloadDebugDump() {
    }

    /** Explicit opt-in renderer — delegates to the type's own contract. */
    public static String toJson(final ToJson obj) {
        if (obj == null) {
            return "null";
        }
        final String value = obj.toJson();
        return value == null ? "null" : value;
    }

    /** Native LogData renderer — body-type-aware (TEXT / JSON / YAML). */
    public static String toJson(final LogData data) {
        return LogDataDebugDump.toJson(data);
    }

    /** Convenience: builds the snapshot once and renders via {@link #toJson(LogData)}. */
    public static String toJson(final LogData.Builder builder) {
        return builder == null ? "null" : LogDataDebugDump.toJson(builder.build());
    }

    /**
     * Generic protobuf renderer via {@link JsonFormat}. A receiver plugin can
     * override the rendering for its own input type via the
     * {@link org.apache.skywalking.oap.log.analyzer.v2.spi.LalInputDebugRenderer}
     * SPI (e.g. Envoy ALS decodes the Istio metadata-exchange peer instead of
     * emitting opaque base64); when no renderer matches, the generic printer
     * is used.
     */
    public static String toJson(final Message msg) {
        if (msg == null) {
            return "null";
        }
        final String custom = LalInputDebugRenderers.render(msg);
        if (custom != null) {
            return custom;
        }
        return renderProto(msg);
    }

    /**
     * Robust generic protobuf render — the well-known {@link #TYPE_REGISTRY}
     * plus the unresolvable-{@code Any} sanitizer — WITHOUT consulting the
     * {@link org.apache.skywalking.oap.log.analyzer.v2.spi.LalInputDebugRenderer}
     * SPI. A receiver renderer that decodes its own sub-fields (e.g. the Envoy
     * ALS metadata-exchange peer) calls this to serialize the decoded message
     * and inherit the same robustness: the decoded fields stay readable AND any
     * unrelated unregistered {@code Any} elsewhere in the same message degrades
     * to a placeholder instead of failing the whole render. Never re-enters the
     * SPI, so a renderer calling it cannot recurse.
     *
     * @param msg the message to render.
     * @return the proto3 JSON string; never {@code null}.
     */
    public static String renderProto(final Message msg) {
        if (msg == null) {
            return "null";
        }
        return printProtoOrError(msg, msg.getDescriptorForType().getFullName());
    }

    /** Convenience: builds once and renders via {@link #toJson(Message)}. */
    public static String toJson(final Message.Builder builder) {
        return builder == null ? "null" : toJson(builder.build());
    }

    /**
     * Defensive renderer for {@link MessageOrBuilder} — covers exotic
     * proto wrappers that aren't a direct {@link Message} but expose
     * the same printable shape.
     */
    public static String toJson(final MessageOrBuilder msg) {
        if (msg == null) {
            return "null";
        }
        return printProtoOrError(msg, msg.getClass().getSimpleName());
    }

    /**
     * Operator-friendly hint object emitted by callers when their
     * dispatch logic doesn't recognise the runtime type. The hint
     * surfaces the FQCN so the operator knows where to add the
     * {@link ToJson} implementation.
     */
    public static String unknownTypeHint(final Object obj) {
        if (obj == null) {
            return "null";
        }
        final JsonObject hint = new JsonObject();
        hint.addProperty("type", obj.getClass().getName());
        hint.addProperty("note", "requires-ToJson-impl");
        return hint.toString();
    }

    /**
     * Parse the result of any overload above into a {@link JsonElement}
     * so a caller building a parent {@link JsonObject} can splice the
     * result without going through a round-trip string parse.
     */
    public static JsonElement parse(final String json) {
        return JsonParser.parseString(json);
    }

    private static String printProtoOrError(final MessageOrBuilder msg, final String typeLabel) {
        try {
            final Message message = toMessage(msg);
            return JSON_PRINTER.print(message == null ? msg : sanitizeUnresolvableAny(message));
        } catch (final Exception e) {
            final JsonObject obj = new JsonObject();
            obj.addProperty("type", typeLabel);
            obj.addProperty("error", "jsonformat-failed");
            obj.addProperty("detail", e.getMessage());
            return obj.toString();
        }
    }

    private static Message toMessage(final MessageOrBuilder msg) {
        if (msg instanceof Message) {
            return (Message) msg;
        }
        if (msg instanceof Message.Builder) {
            return ((Message.Builder) msg).build();
        }
        return null;
    }

    /**
     * Recursively replace every {@code google.protobuf.Any} whose
     * {@code type_url} the {@link #TYPE_REGISTRY} cannot resolve with a
     * placeholder that preserves the type URL and the raw bytes. Because
     * {@code Any}-valued fields (e.g. Envoy ALS {@code filter_state_objects})
     * are open by design — any filter or operator config can inject arbitrary
     * messages — a single unresolvable {@code Any} must not blank the whole
     * input. The placeholder is itself an {@code Any} wrapping a
     * {@code Struct} so it stays canonical proto3 JSON and prints natively.
     * Returns the original message unchanged when nothing needs replacing.
     */
    private static Message sanitizeUnresolvableAny(final Message message) {
        final Descriptor descriptor = message.getDescriptorForType();
        Message.Builder builder = null;
        for (final FieldDescriptor field : descriptor.getFields()) {
            if (field.getJavaType() != FieldDescriptor.JavaType.MESSAGE) {
                continue;
            }
            if (field.isMapField()) {
                final FieldDescriptor valueField = field.getMessageType().findFieldByName("value");
                if (valueField == null || valueField.getJavaType() != FieldDescriptor.JavaType.MESSAGE) {
                    continue;
                }
                final int count = message.getRepeatedFieldCount(field);
                for (int i = 0; i < count; i++) {
                    final Message entry = (Message) message.getRepeatedField(field, i);
                    final Message value = (Message) entry.getField(valueField);
                    final Message fixed = fixMessage(value);
                    if (fixed != value) {
                        if (builder == null) {
                            builder = message.toBuilder();
                        }
                        builder.setRepeatedField(field, i,
                            entry.toBuilder().setField(valueField, fixed).build());
                    }
                }
            } else if (field.isRepeated()) {
                final int count = message.getRepeatedFieldCount(field);
                for (int i = 0; i < count; i++) {
                    final Message element = (Message) message.getRepeatedField(field, i);
                    final Message fixed = fixMessage(element);
                    if (fixed != element) {
                        if (builder == null) {
                            builder = message.toBuilder();
                        }
                        builder.setRepeatedField(field, i, fixed);
                    }
                }
            } else if (message.hasField(field)) {
                final Message child = (Message) message.getField(field);
                final Message fixed = fixMessage(child);
                if (fixed != child) {
                    if (builder == null) {
                        builder = message.toBuilder();
                    }
                    builder.setField(field, fixed);
                }
            }
        }
        return builder == null ? message : builder.build();
    }

    private static Message fixMessage(final Message message) {
        final String fullName = message.getDescriptorForType().getFullName();
        if (ANY_TYPE_NAME.equals(fullName)) {
            return fixAny(message);
        }
        if (VALUE_TYPE_NAME.equals(fullName)) {
            return fixValue(message);
        }
        return sanitizeUnresolvableAny(message);
    }

    /**
     * Neutralize an {@code Any} that {@link JsonFormat} cannot print: an
     * unresolvable {@code type_url}, a no-slash {@code type_url} (the printer
     * only accepts {@code .../type} form and throws otherwise — even for a
     * well-known type), or a resolvable type whose packed bytes are corrupt.
     * Each degrades to the {@code @unresolved} placeholder. A resolvable, well-
     * formed payload is recursively sanitized and re-packed under its original
     * {@code type_url} so a bad value nested inside it (e.g. a non-finite
     * {@code Value}) can't blank the whole message either.
     */
    private static Message fixAny(final Message message) {
        final Any any;
        try {
            any = message instanceof Any ? (Any) message : Any.parseFrom(message.toByteString());
        } catch (final Exception e) {
            return message;
        }
        final String typeUrl = any.getTypeUrl();
        if (typeUrl.indexOf('/') < 0) {
            return Any.pack(unresolvedPlaceholder(any));
        }
        final Descriptor descriptor = TYPE_REGISTRY.find(stripTypeUrlPrefix(typeUrl));
        if (descriptor == null) {
            return Any.pack(unresolvedPlaceholder(any));
        }
        try {
            final Message packed = sanitizeUnresolvableAny(DynamicMessage.parseFrom(descriptor, any.getValue()));
            return Any.newBuilder().setTypeUrl(typeUrl).setValue(packed.toByteString()).build();
        } catch (final Exception e) {
            return Any.pack(unresolvedPlaceholder(any));
        }
    }

    /**
     * Replace a {@code Value} carrying a non-finite {@code number_value}
     * (NaN / ±Infinity) — which {@link JsonFormat} refuses to encode and would
     * otherwise blank the whole message — with the same number rendered as a
     * string. Other {@code Value} kinds (struct / list) are recursed into so a
     * non-finite double nested anywhere inside is scrubbed too.
     */
    private static Message fixValue(final Message message) {
        final Value value;
        try {
            value = message instanceof Value ? (Value) message : Value.parseFrom(message.toByteString());
        } catch (final Exception e) {
            return message;
        }
        if (value.getKindCase() == Value.KindCase.NUMBER_VALUE) {
            final double number = value.getNumberValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                return Value.newBuilder().setStringValue(Double.toString(number)).build();
            }
            return message;
        }
        return sanitizeUnresolvableAny(message);
    }

    private static Struct unresolvedPlaceholder(final Any any) {
        return Struct.newBuilder()
            .putFields("@type", Value.newBuilder().setStringValue(any.getTypeUrl()).build())
            .putFields("@unresolved", Value.newBuilder().setBoolValue(true).build())
            .putFields("value", Value.newBuilder()
                .setStringValue(Base64.getEncoder().encodeToString(any.getValue().toByteArray()))
                .build())
            .build();
    }

    private static String stripTypeUrlPrefix(final String typeUrl) {
        final int slash = typeUrl.lastIndexOf('/');
        return slash < 0 ? typeUrl : typeUrl.substring(slash + 1);
    }
}
