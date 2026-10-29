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
import com.google.common.util.concurrent.Striped;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Builds a network address alias from one call: a CLIENT span and the SERVER span whose {@code parentSpanId} is that
 * CLIENT span's {@code spanId}, in the same trace. That match is the only thing that pairs two spans; names,
 * addresses and attributes never do. A proxy, a load balancer or a {@code Host} header kept end to end therefore
 * cannot move an alias onto the wrong service: each hop is learned from its own two spans.
 *
 * <p>The two spans arrive in separate batches, in either order, and in a cluster at different nodes. Each half is
 * sent as a {@link PairingFact} through the {@link #transport} to the node that owns the trace, chosen by the trace
 * id's hash the way a metric is routed to the node that aggregates it; a single node sends to itself. The owner keeps
 * two buffers of 60 seconds: callers keyed by their own span id, and callees keyed by their parent span id. A match
 * pushes the row under the address the callee's rule composes from the caller half through {@link AliasRegistrar};
 * {@code NetworkAddressAlias#combine} decides whether storage writes it, so an unchanged mapping is written once per
 * refresh interval and a changed one at the next flush.
 */
@Slf4j
public class SpanPairing implements Service {
    static final long TTL_SECONDS = 60L;
    static final long MAX_ENTRIES = 200_000L;
    private static final long WARN_INTERVAL_MILLIS = 60_000L;

    private final Cache<String, PairingFact> callers = buffer(TTL_SECONDS, TimeUnit.SECONDS);
    private final Cache<String, PairingFact> callees = buffer(TTL_SECONDS, TimeUnit.SECONDS);

    /**
     * Both halves of one call can arrive at once on two receiver threads; matching under the key's lock keeps them
     * from both missing the other and waiting out the buffer unmatched.
     */
    private final Striped<Lock> keyLocks = Striped.lock(64);

    private final DroppedPairs tooLong = new DroppedPairs();
    private final DroppedPairs uncomposed = new DroppedPairs();

    /**
     * Where a fact goes: the provider wires the cluster's remote sender; until then, and in tests, this node.
     */
    @Setter
    private Consumer<PairingFact> transport = this::receive;
    /**
     * Whether a fact is paired at all: the provider samples a share of the traces; until then, and in tests, every
     * one.
     */
    @Setter
    private Predicate<PairingFact> sampled = fact -> true;
    /**
     * Where a built row goes; set by the provider once the core's services are reachable.
     */
    @Getter
    @Setter
    private AliasRegistrar aliasRegistrar;
    /**
     * The alias address the callee's rule composes from a matched pair, (caller, callee) to the address, null when
     * the rule is gone; the provider runs the rule's {@code buildNetworkAlias} expression. Until then, and in tests,
     * the shipped composition.
     */
    @Setter
    private BiFunction<PairingFact, PairingFact, String> addressComposer =
        (caller, callee) -> IDManager.NetworkAddressAliasDefine.buildCallerScopedAddress(
            caller.getService(), caller.getPeer());

    private static <T> Cache<String, T> buffer(final long ttl, final TimeUnit unit) {
        return CacheBuilder.newBuilder().maximumSize(MAX_ENTRIES).expireAfterWrite(ttl, unit).build();
    }

    /**
     * The callee half of a call, sent to the trace's owner.
     *
     * @param meta      a SERVER span; any other kind is ignored, and so is a root span and one whose parent ran in
     *                  the same process, which no other service's CLIENT span is the parent of. A CONSUMER span's
     *                  parent is the PRODUCER span, and an alias would move every producer's edge off the broker
     * @param normal    the {@code normal} flag its service id carries
     * @param ruleLayer the layer of the rule whose {@code buildNetworkAlias} the span ran
     * @param ruleName  that rule's name
     */
    public void serverSeen(final SpanMetadata meta, final boolean normal,
                           final String ruleLayer, final String ruleName) {
        if (!SpanMetadata.KIND_SERVER.equals(meta.getKind())
            || meta.getParentSpanId() == null || meta.getParentSpanId().isEmpty()
            || SpanMetadata.PARENT_LOCAL.equals(meta.getParentLocation())) {
            return;
        }
        final PairingFact fact = PairingFact.server(meta, normal, ruleLayer, ruleName);
        if (sampled.test(fact)) {
            transport.accept(fact);
        }
    }

    /**
     * The caller half of a call, sent to the trace's owner for every sampled call; the pair it meets is pushed every
     * time, and the alias row decides whether to write it.
     *
     * @param meta a CLIENT span; any other kind is ignored. A PRODUCER span's peer is the broker, which answers with
     *             no span of its own, so there is nothing to pair it with
     * @param peer the peer as the rule spelled it
     */
    public void clientSeen(final SpanMetadata meta, final String peer) {
        if (!SpanMetadata.KIND_CLIENT.equals(meta.getKind()) || peer == null || peer.isEmpty()) {
            return;
        }
        final PairingFact fact = PairingFact.client(meta, peer);
        if (sampled.test(fact)) {
            transport.accept(fact);
        }
    }

    /**
     * A fact that reached its owner: this node, from {@link PairingWorker}.
     *
     * @param fact either half of a call
     */
    public void receive(final PairingFact fact) {
        final String key = key(fact.getTraceId(), fact.isServer() ? fact.getParentSpanId() : fact.getSpanId());
        final Cache<String, PairingFact> own = fact.isServer() ? callees : callers;
        final Cache<String, PairingFact> other = fact.isServer() ? callers : callees;
        final PairingFact match;
        final Lock lock = keyLocks.get(key);
        lock.lock();
        try {
            match = other.getIfPresent(key);
            if (match == null) {
                own.put(key, fact);
                return;
            }
            other.invalidate(key);
        } finally {
            lock.unlock();
        }
        if (fact.isServer()) {
            build(match, fact);
        } else {
            build(fact, match);
        }
    }

    /**
     * @return the pairs whose composed address did not fit the alias column, since start
     */
    public long tooLong() {
        return tooLong.total();
    }

    /**
     * @return the pairs whose callee's rule composed no address, being missing on this node or failing, since start
     */
    public long uncomposed() {
        return uncomposed.total();
    }

    private void build(final PairingFact caller, final PairingFact callee) {
        // An uninstrumented gateway keeps its own node, as on the agent path. A call to another replica of the caller
        // is built like any other: the row is the caller's own, so it draws the call as the loop it is.
        if (aliasRegistrar != null && aliasRegistrar.isGateway(caller.getPeer())) {
            return;
        }
        final String address;
        try {
            address = addressComposer.apply(caller, callee);
        } catch (final RuntimeException e) {
            reportUncomposed(callee, e);
            return;
        }
        if (address == null || address.isEmpty()) {
            reportUncomposed(callee, null);
            return;
        }
        if (address.length() > NetworkAddressAlias.ADDRESS_MAX_LENGTH) {
            reportTooLong(address);
            return;
        }
        if (aliasRegistrar != null) {
            aliasRegistrar.register(address, callee.getService(), callee.isNormal(), System.currentTimeMillis());
        }
    }

    /**
     * The callee's rule is looked up on this node, the trace's owner, which may not load it, as an Aggregator with
     * other {@code talFiles} or a rule removed here only.
     */
    private void reportUncomposed(final PairingFact callee, final RuntimeException failure) {
        final long sinceLastWarn = uncomposed.count();
        if (sinceLastWarn > 0) {
            log.warn("{} network aliases were not built since the last report, {} since start, because the callee's "
                    + "rule composed no address on this node, the trace's owner; their callers stay address-named. "
                    + "Latest: rule {} of layer {}, missing here, failing or composing an empty address",
                sinceLastWarn, uncomposed.total(), callee.getRuleName(), callee.getRuleLayer(), failure);
        } else if (failure != null && log.isDebugEnabled()) {
            log.debug("Rule {} failed to compose a network alias address", callee.getRuleName(), failure);
        }
    }

    private void reportTooLong(final String address) {
        final long sinceLastWarn = tooLong.count();
        if (sinceLastWarn > 0) {
            log.warn("{} network alias addresses were longer than the {} characters the alias column holds since the "
                    + "last report, {} since start, and were not built; their callers stay address-named. Latest: {}",
                sinceLastWarn, NetworkAddressAlias.ADDRESS_MAX_LENGTH, tooLong.total(), address);
        }
    }

    private static String key(final String traceId, final String spanId) {
        return traceId + '/' + spanId;
    }

    /**
     * Pairs dropped for one reason, reported at most once a minute with the count since the previous report, so a
     * sustained cause stays visible without a line per call.
     */
    private static final class DroppedPairs {
        private final AtomicLong total = new AtomicLong();
        private final AtomicLong totalAtLastWarn = new AtomicLong();
        private final AtomicLong lastWarnMillis = new AtomicLong(Long.MIN_VALUE / 2);

        /**
         * @return the drops since the previous report when one is due now, this one included; otherwise 0
         */
        long count() {
            final long now = System.currentTimeMillis();
            final long current = total.incrementAndGet();
            final long last = lastWarnMillis.get();
            if (now - last >= WARN_INTERVAL_MILLIS && lastWarnMillis.compareAndSet(last, now)) {
                return current - totalAtLastWarn.getAndSet(current);
            }
            return 0L;
        }

        long total() {
            return total.get();
        }
    }
}
