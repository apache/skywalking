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

package org.apache.skywalking.oap.log.analyzer.v2.dsldebug;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Struct;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.Value;
import io.envoyproxy.envoy.config.core.v3.Metadata;
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPResponseProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the generic protobuf input renderer no longer blanks the whole
 * message on an {@code Any}-typed field. The Envoy ALS shape
 * ({@code filter_state_objects} is {@code map<string, google.protobuf.Any>})
 * is used because it is the reported failure. No Envoy receiver SPI is on this
 * module's test classpath, so these exercise the generic path:
 * {@code TypeRegistry} (well-known types) plus the unresolvable-{@code Any}
 * sanitizer — not a {@code LalInputDebugRenderer} (tested in the envoy module).
 */
class LalPayloadDebugDumpTest {

    @Test
    void wellKnownAnyRendersAsBase64InsteadOfFailing() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCode(UInt32Value.of(503)))
            .setCommonProperties(AccessLogCommon.newBuilder()
                .putFilterStateObjects(
                    "some.bytes_state",
                    Any.pack(BytesValue.newBuilder().setValue(ByteString.copyFromUtf8("hello")).build())))
            .build();

        final String json = LalPayloadDebugDump.toJson(entry);

        // Regression: the bare printer used to throw on the BytesValue Any.
        assertFalse(json.contains("jsonformat-failed"), json);
        // BytesValue renders as base64 in proto3 JSON ("hello" -> "aGVsbG8=").
        assertTrue(json.contains(Base64.getEncoder().encodeToString("hello".getBytes())), json);
        // The rest of the entry still serialized.
        assertTrue(json.contains("503"), json);
    }

    @Test
    void unresolvableInjectedAnyDegradesToPlaceholderAndKeepsRest() {
        final Any injected = Any.newBuilder()
            .setTypeUrl("type.googleapis.com/test.skywalking.CustomFilterState")
            .setValue(ByteString.copyFromUtf8("opaque-bytes"))
            .build();
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCode(UInt32Value.of(200)))
            .setCommonProperties(AccessLogCommon.newBuilder()
                .putFilterStateObjects("test.skywalking.injected", injected))
            .build();

        final String json = LalPayloadDebugDump.toJson(entry);

        // No whole-message blanking despite an unregistered injected type.
        assertFalse(json.contains("jsonformat-failed"), json);
        // The placeholder preserves the original type URL and flags it.
        assertTrue(json.contains("@unresolved"), json);
        assertTrue(json.contains("test.skywalking.CustomFilterState"), json);
        assertTrue(json.contains(Base64.getEncoder().encodeToString("opaque-bytes".getBytes())), json);
        // The rest of the entry still serialized.
        assertTrue(json.contains("200"), json);
    }

    @Test
    void nonFiniteValueInStructDoesNotBlankEntry() {
        // metadata.filter_metadata is map<string, Struct>; a NaN/Infinity in a
        // Value makes JsonFormat throw, which used to blank the whole entry.
        final Struct meta = Struct.newBuilder()
            .putFields("ratio", Value.newBuilder().setNumberValue(Double.NaN).build())
            .putFields("ceiling", Value.newBuilder().setNumberValue(Double.POSITIVE_INFINITY).build())
            .build();
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCode(UInt32Value.of(200)))
            .setCommonProperties(AccessLogCommon.newBuilder()
                .setMetadata(Metadata.newBuilder().putFilterMetadata("istio.authz", meta)))
            .build();

        final String json = LalPayloadDebugDump.toJson(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("NaN"), json);        // non-finite scrubbed to a string
        assertTrue(json.contains("Infinity"), json);
        assertTrue(json.contains("200"), json);        // rest of the entry survived
    }

    @Test
    void noSlashTypeUrlAnyDegradesToPlaceholder() {
        // A no-slash type_url throws in JsonFormat.printAny even when the bare
        // name resolves to a well-known type — must degrade, not blank.
        final Any noSlash = Any.newBuilder()
            .setTypeUrl("google.protobuf.BytesValue")
            .setValue(ByteString.copyFromUtf8("x"))
            .build();
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCode(UInt32Value.of(200)))
            .setCommonProperties(AccessLogCommon.newBuilder()
                .putFilterStateObjects("no.slash", noSlash))
            .build();

        final String json = LalPayloadDebugDump.toJson(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("@unresolved"), json);
        assertTrue(json.contains("200"), json);
    }

    @Test
    void resolvableTypeUrlWithCorruptBytesDegradesToPlaceholder() {
        // type_url resolves (Struct) but the packed bytes are garbage — the
        // printer parses them and throws; must degrade, not blank.
        final Any corrupt = Any.newBuilder()
            .setTypeUrl("type.googleapis.com/google.protobuf.Struct")
            .setValue(ByteString.copyFrom(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}))
            .build();
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCode(UInt32Value.of(200)))
            .setCommonProperties(AccessLogCommon.newBuilder()
                .putFilterStateObjects("corrupt", corrupt))
            .build();

        final String json = LalPayloadDebugDump.toJson(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("@unresolved"), json);
        assertTrue(json.contains("200"), json);
    }
}
