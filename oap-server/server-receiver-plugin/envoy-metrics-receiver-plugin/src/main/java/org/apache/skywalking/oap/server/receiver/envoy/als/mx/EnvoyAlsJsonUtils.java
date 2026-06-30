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

package org.apache.skywalking.oap.server.receiver.envoy.als.mx;

import Wasm.Common.FlatNode;
import com.google.protobuf.Any;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.TCPAccessLogEntry;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;
import org.apache.skywalking.oap.log.analyzer.v2.dsldebug.LalPayloadDebugDump;

/**
 * Canonical "Envoy ALS entry to readable JSON" for SkyWalking, covering both
 * the HTTP and TCP access-log shapes (both feed the LAL pipeline — see
 * {@code LogsPersistence} / {@code TCPLogsPersistence}). The Istio
 * metadata-exchange peer objects in {@code common_properties.filter_state_objects}
 * are decoded into readable {@code Struct}s instead of opaque base64, then the
 * entry is serialized.
 *
 * <p>Two encodings are handled, both reusing the same decoders the runtime
 * analyzer uses ({@link ServiceMetaInfoAdapter} / {@link MetaExchangeALSHTTPAnalyzer}):
 * <ul>
 *   <li>legacy Wasm ({@code wasm.upstream_peer} / {@code wasm.downstream_peer}):
 *       {@code Any{BytesValue}} wrapping a {@code FlatNode} FlatBuffer.</li>
 *   <li>modern native ({@code upstream_peer} / {@code downstream_peer}):
 *       {@code Any{Struct}}.</li>
 * </ul>
 *
 * <p>A decoded peer is re-packed as {@code Any{Struct}} so the canonical
 * proto3 JSON shape is preserved, then the whole entry is serialized by
 * {@link LalPayloadDebugDump#renderProto(com.google.protobuf.Message)} — the
 * framework's robust printer (well-known {@code TypeRegistry} plus an
 * unresolvable-{@code Any} sanitizer). This matters because
 * {@code filter_state_objects} is open by design: a non-peer entry can carry
 * an arbitrary unregistered type, and the robust printer degrades just that
 * one field to a placeholder while keeping the decoded peer readable — rather
 * than failing the whole entry and losing the peer decode.
 */
public final class EnvoyAlsJsonUtils {

    private static final Set<String> PEER_KEYS = Set.of(
        MetaExchangeALSHTTPAnalyzer.UPSTREAM_KEY,     // "wasm.upstream_peer"  (legacy FlatBuffer)
        MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_KEY,   // "wasm.downstream_peer"
        MetaExchangeALSHTTPAnalyzer.UPSTREAM_PEER,    // "upstream_peer"       (modern Struct)
        MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_PEER   // "downstream_peer"
    );

    private EnvoyAlsJsonUtils() {
    }

    /**
     * Serialize an HTTP ALS entry to JSON with metadata-exchange peers decoded.
     * Non-peer {@code filter_state_objects} of unregistered types do not fail
     * the render — they degrade to a placeholder while the peer stays decoded.
     *
     * @param entry the HTTP access log entry to render.
     * @return the entry as proto3 JSON, peers decoded where present.
     */
    public static String toJSON(final HTTPAccessLogEntry entry) {
        if (!entry.hasCommonProperties()) {
            return LalPayloadDebugDump.renderProto(entry);
        }
        return LalPayloadDebugDump.renderProto(
            entry.toBuilder().setCommonProperties(decodePeers(entry.getCommonProperties())).build());
    }

    /**
     * Serialize a TCP ALS entry to JSON with metadata-exchange peers decoded.
     * TCP shares {@code AccessLogCommon} with HTTP, so the same peer keys and
     * encodings apply.
     *
     * @param entry the TCP access log entry to render.
     * @return the entry as proto3 JSON, peers decoded where present.
     */
    public static String toJSON(final TCPAccessLogEntry entry) {
        if (!entry.hasCommonProperties()) {
            return LalPayloadDebugDump.renderProto(entry);
        }
        return LalPayloadDebugDump.renderProto(
            entry.toBuilder().setCommonProperties(decodePeers(entry.getCommonProperties())).build());
    }

    /**
     * Dispatch overload for callers holding only a {@link Message} (e.g. the
     * LAL output builder's {@code bindInput}). Routes ALS entries to the
     * peer-decoding overloads above; any other message is rendered by the
     * framework's robust printer so an unregistered {@code Any} still cannot
     * throw.
     *
     * @param entry the message to render.
     * @return proto3 JSON; peers decoded for ALS entries.
     */
    public static String toJSON(final Message entry) {
        if (entry instanceof HTTPAccessLogEntry) {
            return toJSON((HTTPAccessLogEntry) entry);
        }
        if (entry instanceof TCPAccessLogEntry) {
            return toJSON((TCPAccessLogEntry) entry);
        }
        return LalPayloadDebugDump.renderProto(entry);
    }

    private static AccessLogCommon decodePeers(final AccessLogCommon common) {
        if (common.getFilterStateObjectsCount() == 0) {
            return common;
        }
        final AccessLogCommon.Builder builder = common.toBuilder();
        // iterate the original (immutable) map; mutate the builder
        common.getFilterStateObjectsMap().forEach((key, value) -> {
            if (PEER_KEYS.contains(key)) {
                decodePeer(key, value).ifPresent(struct ->
                    builder.putFilterStateObjects(key, Any.pack(struct)));
            }
        });
        return builder.build();
    }

    private static Optional<Struct> decodePeer(final String key, final Any value) {
        try {
            if (key.equals(MetaExchangeALSHTTPAnalyzer.UPSTREAM_KEY)
                || key.equals(MetaExchangeALSHTTPAnalyzer.DOWNSTREAM_KEY)) {
                // legacy Wasm: Any{BytesValue} -> FlatBuffer -> Struct
                final ByteBuffer buffer = ByteBuffer.wrap(
                    BytesValue.parseFrom(value.getValue()).getValue().toByteArray());
                return Optional.of(
                    ServiceMetaInfoAdapter.extractStructFromNodeFlatBuffer(
                        FlatNode.getRootAsFlatNode(buffer)));
            }
            // modern native: Any{Struct}
            return Optional.of(value.unpack(Struct.class));
        } catch (final Exception e) {
            return Optional.empty(); // leave the raw Any; the registry still renders it as base64
        }
    }
}
