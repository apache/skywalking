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

package org.apache.skywalking.oap.server.receiver.envoy.als.mx;

import Wasm.Common.FlatNode;
import Wasm.Common.KeyVal;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Struct;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.Value;
import io.envoyproxy.envoy.config.core.v3.Metadata;
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPRequestProperties;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPResponseProperties;
import io.envoyproxy.envoy.data.accesslog.v3.TCPAccessLogEntry;
import org.apache.skywalking.oap.log.analyzer.v2.dsldebug.LalPayloadDebugDump;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Istio metadata-exchange peer injected into Envoy ALS
 * {@code filter_state_objects} is decoded into readable metadata instead of
 * opaque base64, for both encodings, and that the
 * {@link EnvoyAlsHttpDebugRenderer} / {@link EnvoyAlsTcpDebugRenderer} SPI
 * shims are wired into the framework dump path.
 */
class EnvoyAlsDebugRendererTest {

    private static HTTPAccessLogEntry entryWith(final String key, final Any peer) {
        return HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCode(UInt32Value.of(200)))
            .setCommonProperties(AccessLogCommon.newBuilder().putFilterStateObjects(key, peer))
            .build();
    }

    private static TCPAccessLogEntry tcpEntryWith(final String key, final Any peer) {
        return TCPAccessLogEntry.newBuilder()
            .setCommonProperties(AccessLogCommon.newBuilder().putFilterStateObjects(key, peer))
            .build();
    }

    /** Legacy Wasm form: Any{BytesValue} wrapping a FlatNode FlatBuffer. */
    private static Any legacyWasmPeer(final String name, final String namespace,
                                      final String labelKey, final String labelVal) {
        final FlatBufferBuilder fbb = new FlatBufferBuilder();
        final int nameOff = fbb.createString(name);
        final int nsOff = fbb.createString(namespace);
        final int keyOff = fbb.createString(labelKey);
        final int valOff = fbb.createString(labelVal);
        final int kvOff = KeyVal.createKeyVal(fbb, keyOff, valOff);
        final int labelsOff = FlatNode.createLabelsVector(fbb, new int[] {kvOff});
        final int node = FlatNode.createFlatNode(fbb, nameOff, nsOff, labelsOff, 0, 0, 0, 0, 0, 0, 0);
        fbb.finish(node);
        final BytesValue wrapped = BytesValue.newBuilder()
            .setValue(ByteString.copyFrom(fbb.sizedByteArray()))
            .build();
        return Any.pack(wrapped);
    }

    /** Modern native form: Any{Struct}. */
    private static Any modernStructPeer(final String name, final String namespace) {
        final Struct struct = Struct.newBuilder()
            .putFields("NAME", Value.newBuilder().setStringValue(name).build())
            .putFields("NAMESPACE", Value.newBuilder().setStringValue(namespace).build())
            .build();
        return Any.pack(struct);
    }

    @Test
    void decodesLegacyWasmFlatBufferPeer() throws Exception {
        final HTTPAccessLogEntry entry = entryWith(
            MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_KEY,
            legacyWasmPeer("reviews-v3-pod", "bookinfo", "app", "reviews"));

        final String json = EnvoyAlsJsonUtils.toJSON(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("reviews-v3-pod"), json);
        assertTrue(json.contains("bookinfo"), json);
        assertTrue(json.contains("reviews"), json);
        assertTrue(json.contains("200"), json);
    }

    @Test
    void decodesModernStructPeer() throws Exception {
        final HTTPAccessLogEntry entry = entryWith(
            MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_PEER,
            modernStructPeer("ratings-v1-pod", "bookinfo"));

        final String json = EnvoyAlsJsonUtils.toJSON(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("ratings-v1-pod"), json);
        assertTrue(json.contains("bookinfo"), json);
    }

    /** The renderer is discovered via ServiceLoader and used by the framework dump path. */
    @Test
    void spiWiredIntoFrameworkDumpPath() {
        final HTTPAccessLogEntry entry = entryWith(
            MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_KEY,
            legacyWasmPeer("details-v1-pod", "bookinfo", "app", "details"));

        final String json = LalPayloadDebugDump.toJson(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("details-v1-pod"), json);
    }

    @Test
    void decodesBothUpstreamAndDownstreamLegacyPeers() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setCommonProperties(AccessLogCommon.newBuilder()
                .putFilterStateObjects(MetaExchangeALSHTTPAnalyzer.UPSTREAM_KEY,
                    legacyWasmPeer("reviews-v3-pod", "bookinfo", "app", "reviews"))
                .putFilterStateObjects(MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_KEY,
                    legacyWasmPeer("productpage-v1-pod", "bookinfo", "app", "productpage")))
            .build();

        final String json = EnvoyAlsJsonUtils.toJSON(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("reviews-v3-pod"), json);
        assertTrue(json.contains("productpage-v1-pod"), json);
    }

    @Test
    void decodesBothUpstreamAndDownstreamModernPeers() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setCommonProperties(AccessLogCommon.newBuilder()
                .putFilterStateObjects(MetaExchangeALSHTTPAnalyzer.UPSTREAM_PEER,
                    modernStructPeer("reviews-v3-pod", "bookinfo"))
                .putFilterStateObjects(MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_PEER,
                    modernStructPeer("productpage-v1-pod", "bookinfo")))
            .build();

        final String json = EnvoyAlsJsonUtils.toJSON(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("reviews-v3-pod"), json);
        assertTrue(json.contains("productpage-v1-pod"), json);
    }

    /**
     * Regression for the all-or-nothing bug: an unrelated unregistered
     * {@code Any} in {@code filter_state_objects} must NOT make the decoded
     * peer revert to opaque base64 — the peer stays decoded and the unknown
     * type degrades to a placeholder, in the same output.
     */
    @Test
    void peerStaysDecodedAlongsideUnregisteredFilterState() {
        final Any unknown = Any.newBuilder()
            .setTypeUrl("type.googleapis.com/test.skywalking.CustomFilterState")
            .setValue(ByteString.copyFromUtf8("opaque-bytes"))
            .build();
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCode(UInt32Value.of(200)))
            .setCommonProperties(AccessLogCommon.newBuilder()
                .putFilterStateObjects(MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_KEY,
                    legacyWasmPeer("productpage-v1-pod", "bookinfo", "app", "productpage"))
                .putFilterStateObjects("test.skywalking.injected", unknown))
            .build();

        // Direct util.
        final String json = EnvoyAlsJsonUtils.toJSON(entry);
        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("productpage-v1-pod"), json); // peer still decoded
        assertTrue(json.contains("@unresolved"), json);        // unknown degraded
        assertTrue(json.contains("test.skywalking.CustomFilterState"), json);
        assertTrue(json.contains("200"), json);                // standard field intact

        // Same through the framework SPI dump path.
        final String viaFramework = LalPayloadDebugDump.toJson(entry);
        assertTrue(viaFramework.contains("productpage-v1-pod"), viaFramework);
        assertTrue(viaFramework.contains("@unresolved"), viaFramework);
    }

    /** A realistic full entry: standard scalar/wrapper fields, metadata Struct, and a decoded peer. */
    @Test
    void rendersFullEntryWithPeerAndStandardFields() {
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setRequest(HTTPRequestProperties.newBuilder().setPath("/productpage").setAuthority("productpage:9080"))
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCode(UInt32Value.of(200)))
            .setCommonProperties(AccessLogCommon.newBuilder()
                .setMetadata(Metadata.newBuilder().putFilterMetadata("istio.authz",
                    Struct.newBuilder()
                        .putFields("policy", Value.newBuilder().setStringValue("allow").build())
                        .build()))
                .putFilterStateObjects(MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_KEY,
                    legacyWasmPeer("productpage-v1-pod", "bookinfo", "version", "v1")))
            .build();

        final String json = EnvoyAlsJsonUtils.toJSON(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("/productpage"), json);        // scalar
        assertTrue(json.contains("productpage:9080"), json);    // scalar
        assertTrue(json.contains("200"), json);                 // UInt32Value wrapper
        assertTrue(json.contains("allow"), json);               // metadata filter_metadata Struct
        assertTrue(json.contains("productpage-v1-pod"), json);  // decoded peer
        assertTrue(json.contains("v1"), json);                  // decoded peer label
    }

    // --- TCP access logs: TCPAccessLogEntry feeds LAL via TCPLogsPersistence, shares AccessLogCommon ---

    @Test
    void decodesTcpLegacyWasmPeer() {
        final TCPAccessLogEntry entry = tcpEntryWith(
            MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_KEY,
            legacyWasmPeer("ratings-v1-pod", "bookinfo", "app", "ratings"));

        final String json = EnvoyAlsJsonUtils.toJSON(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("ratings-v1-pod"), json);
        assertTrue(json.contains("bookinfo"), json);
    }

    @Test
    void decodesTcpModernStructPeer() {
        final TCPAccessLogEntry entry = tcpEntryWith(
            MetaExchangeALSHTTPAnalyzer.UPSTREAM_PEER,
            modernStructPeer("mysql-pod", "bookinfo"));

        final String json = EnvoyAlsJsonUtils.toJSON(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("mysql-pod"), json);
    }

    /** The TCP renderer is discovered via ServiceLoader and used by the framework dump path. */
    @Test
    void tcpPeerWiredIntoFrameworkDumpPath() {
        final TCPAccessLogEntry entry = tcpEntryWith(
            MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_KEY,
            legacyWasmPeer("mongodb-pod", "bookinfo", "app", "mongodb"));

        final String json = LalPayloadDebugDump.toJson(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("mongodb-pod"), json);
    }

    // --- decodePeer failure fallback: a malformed value under a real peer key must degrade, not blank ---

    @Test
    void corruptLegacyWasmPeerDoesNotBlankEntry() {
        // BytesValue parses, but the inner bytes are not a FlatNode FlatBuffer:
        // decodePeer catches and keeps the raw Any; the entry must still render.
        final Any corrupt = Any.pack(BytesValue.newBuilder()
            .setValue(ByteString.copyFromUtf8("not-a-flatbuffer")).build());
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder().setResponseCode(UInt32Value.of(200)))
            .setCommonProperties(AccessLogCommon.newBuilder()
                .putFilterStateObjects(MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_KEY, corrupt))
            .build();

        final String json = EnvoyAlsJsonUtils.toJSON(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("200"), json); // non-peer field survives regardless of peer outcome
    }

    @Test
    void modernPeerKeyWithNonStructValueDegradesGracefully() {
        // A *_peer key whose value is not a Struct: value.unpack(Struct) throws,
        // decodePeer catches, the raw (unregistered) Any degrades to @unresolved.
        final Any notStruct = Any.newBuilder()
            .setTypeUrl("type.googleapis.com/test.skywalking.NotAStruct")
            .setValue(ByteString.copyFromUtf8("opaque"))
            .build();
        final HTTPAccessLogEntry entry = entryWith(MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_PEER, notStruct);

        final String json = EnvoyAlsJsonUtils.toJSON(entry);

        assertFalse(json.contains("jsonformat-failed"), json);
        assertTrue(json.contains("@unresolved"), json);
    }
}
