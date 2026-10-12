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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Pairs a CLIENT span with the SERVER span it called, to learn the callee's name for an address nothing else
 * names: a peer the caller dialed by IP, or one the callee reports under another spelling or not at all. The
 * trace context says which two spans were one call, {@code parentSpanId} of the SERVER span is the CLIENT span's
 * id, and that needs no attribute convention on either side.
 *
 * <p>The two spans arrive in separate batches, in either order, and in a cluster at different nodes. Each half is
 * therefore sent as a {@link PairingFact} through the {@link #transport} to the node that owns the trace, chosen by
 * the trace id's hash the way a metric is routed to the node that aggregates it; a single node sends to itself.
 * The owner keeps two buffers of 60 seconds: callers whose peer could not be named, keyed by their own span id,
 * and callees, keyed by their parent span id. A match writes the alias row through {@link AliasRegistrar}, after
 * which every node resolves the address from its cache, and remembers the answer locally so the owner's own next
 * calls do not wait for the cache timer. Root spans have nothing to pair with and are not kept.
 */
public class SpanPairing implements Service {
    static final long TTL_SECONDS = 60L;
    static final long MAX_ENTRIES = 200_000L;
    static final long LEARNED_TTL_MINUTES = 10L;

    @RequiredArgsConstructor
    static final class Caller {
        final String address;
        final String service;
    }

    @RequiredArgsConstructor
    static final class Callee {
        final String service;
        final boolean normal;
        final String instance;
        final long timestampMillis;
    }

    private final Cache<String, Caller> callers = buffer(TTL_SECONDS, TimeUnit.SECONDS);
    private final Cache<String, Callee> callees = buffer(TTL_SECONDS, TimeUnit.SECONDS);
    /**
     * Addresses this node learned, so a caller here resolves them before the row has come back through storage.
     */
    private final Cache<String, String> learned = buffer(LEARNED_TTL_MINUTES, TimeUnit.MINUTES);

    /**
     * Where a fact goes: the provider wires the cluster's remote sender; until then, and in tests, this node.
     */
    @Setter
    private Consumer<PairingFact> transport = this::receive;
    /**
     * Where a learned row goes; set by the provider once the core's services are reachable.
     */
    @Getter
    @Setter
    private AliasRegistrar aliasRegistrar;

    private static <T> Cache<String, T> buffer(final long ttl, final TimeUnit unit) {
        return CacheBuilder.newBuilder().maximumSize(MAX_ENTRIES).expireAfterWrite(ttl, unit).build();
    }

    /**
     * The SERVER side of a call, sent to the trace's owner.
     *
     * @param meta   a SERVER or CONSUMER span; a root span is ignored
     * @param normal the {@code normal} flag its service id carries
     */
    public void serverSeen(final SpanMetadata meta, final boolean normal) {
        if (meta.getParentSpanId() == null || meta.getParentSpanId().isEmpty()) {
            return;
        }
        transport.accept(PairingFact.server(meta, normal));
    }

    /**
     * The CLIENT side of a call, for a peer the host rule and the alias cache could not name, sent to the
     * trace's owner.
     *
     * @param meta    a CLIENT span; any other kind is ignored. A PRODUCER span's peer is the broker, which answers
     *                with no span of its own, so there is nothing to pair it with
     * @param address the peer as the rule spelled it
     * @return the callee's service when this node already knows it, from an earlier pair or because it owns the
     * trace and holds the SERVER span, so the edge resolves on the spot; null otherwise, and the edge stays
     * address-named this once
     */
    public String clientSeen(final SpanMetadata meta, final String address) {
        if (!SpanMetadata.KIND_CLIENT.equals(meta.getKind())) {
            return null;
        }
        final String known = learned.getIfPresent(address);
        if (known != null) {
            return known;
        }
        transport.accept(PairingFact.client(meta, address));
        return learned.getIfPresent(address);
    }

    /**
     * A fact that reached its owner: this node, from {@link PairingWorker}.
     *
     * @param fact either half of a call
     */
    public void receive(final PairingFact fact) {
        if (fact.isServer()) {
            pairServer(fact);
        } else {
            pairClient(fact);
        }
    }

    private void pairServer(final PairingFact fact) {
        final String key = key(fact.getTraceId(), fact.getParentSpanId());
        callees.put(key, new Callee(fact.getService(), fact.isNormal(), fact.getInstance(), fact.getTimestampMillis()));
        final Caller caller = callers.getIfPresent(key);
        if (caller == null) {
            return;
        }
        callers.invalidate(key);
        if (!caller.service.equals(fact.getService())) {
            learn(caller.address, fact.getService(), fact.isNormal(), fact.getInstance(), fact.getTimestampMillis());
        }
    }

    private void pairClient(final PairingFact fact) {
        final String key = key(fact.getTraceId(), fact.getSpanId());
        final Callee callee = callees.getIfPresent(key);
        if (callee == null) {
            callers.put(key, new Caller(fact.getAddress(), fact.getService()));
            return;
        }
        if (!callee.service.equals(fact.getService())) {
            learn(fact.getAddress(), callee.service, callee.normal, callee.instance, callee.timestampMillis);
        }
    }

    private void learn(final String address, final String service, final boolean normal, final String instance,
                       final long timestampMillis) {
        // An alias the registrar's guard refuses must not resolve edges on this node either.
        if (aliasRegistrar == null || aliasRegistrar.register(address, service, normal, instance, timestampMillis)) {
            learned.put(address, service);
        }
    }

    private static String key(final String traceId, final String spanId) {
        return traceId + '/' + spanId;
    }
}
