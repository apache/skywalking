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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SpanPairingTest {
    private static final String LAYER = "OTLP_GENERAL";
    private static final String RULE = "otlp-general";

    private final List<NetworkAddressAliasSetup> rows = new ArrayList<>();
    private SpanPairing pairing;
    private AliasRegistrar registrar;

    @BeforeEach
    void setUp() {
        final SourceReceiver receiver = mock(SourceReceiver.class);
        doAnswer(inv -> {
            final ISource s = inv.getArgument(0);
            s.prepare();
            rows.add((NetworkAddressAliasSetup) s);
            return null;
        }).when(receiver).receive(any());
        registrar = new AliasRegistrar(receiver, new NamingControl(70, 70, 150, new EndpointNameGrouping()));
        pairing = new SpanPairing();
        pairing.setAliasRegistrar(registrar);
    }

    @Test
    void serverFirstBuildsWhenTheClientArrives() {
        serverSeen(server("product-catalog", "s3", "s2"));
        pairing.clientSeen(client("frontend", "s2"), "172.19.0.16:3550");
        assertEquals(1, rows.size());
        assertEquals("frontend->172.19.0.16:3550", rows.get(0).getAddress());
        assertEquals(IDManager.ServiceID.buildId("product-catalog", true), rows.get(0).getRepresentServiceId());
    }

    @Test
    void clientFirstBuildsWhenTheServerArrives() {
        pairing.clientSeen(client("frontend", "s2"), "172.19.0.16:3550");
        assertTrue(rows.isEmpty(), "nothing to pair with yet: the client waits");
        serverSeen(server("product-catalog", "s3", "s2"));
        assertEquals(1, rows.size());
        assertEquals(IDManager.ServiceID.buildId("product-catalog", true), rows.get(0).getRepresentServiceId());
        // The pair is consumed: a second SERVER span with the same parent builds nothing new.
        serverSeen(server("product-catalog", "s4", "s2"));
        assertEquals(1, rows.size());
    }

    @Test
    void eachHopIsLearnedFromItsOwnTwoSpans() {
        // The browser dials the proxy, and the proxy forwards to frontend keeping Host: frontend-proxy:8080, so
        // frontend's SERVER span reports the proxy's address. Only the parent ids pair spans, so each hop is right.
        pairing.clientSeen(client("frontend-web", "s2"), "frontend-proxy:8080");
        serverSeen(server("frontend-proxy", "s3", "s2"));
        pairing.clientSeen(client("frontend-proxy", "s4"), "172.19.0.19:8080");
        serverSeen(server("frontend", "s5", "s4"));

        assertEquals(2, rows.size(), rows.toString());
        assertEquals("frontend-web->frontend-proxy:8080", rows.get(0).getAddress());
        assertEquals(IDManager.ServiceID.buildId("frontend-proxy", true), rows.get(0).getRepresentServiceId());
        assertEquals("frontend-proxy->172.19.0.19:8080", rows.get(1).getAddress());
        assertEquals(IDManager.ServiceID.buildId("frontend", true), rows.get(1).getRepresentServiceId());
    }

    @Test
    void oneAddressDialedByTwoCallersNamesTwoServices() {
        // cart:7070 is shop::cart from the shop namespace and outlet::cart from the outlet one.
        pairing.clientSeen(client("shop::frontend", "s2"), "cart:7070");
        serverSeen(server("shop::cart", "s3", "s2"));
        pairing.clientSeen(client("outlet::frontend", "s4"), "cart:7070");
        serverSeen(server("outlet::cart", "s5", "s4"));
        assertEquals(2, rows.size());
        assertEquals("shop::frontend->cart:7070", rows.get(0).getAddress());
        assertEquals(IDManager.ServiceID.buildId("shop::cart", true), rows.get(0).getRepresentServiceId());
        assertEquals("outlet::frontend->cart:7070", rows.get(1).getAddress());
        assertEquals(IDManager.ServiceID.buildId("outlet::cart", true), rows.get(1).getRepresentServiceId());
    }

    @Test
    void theCalleesRuleComposesTheAddress() {
        final List<PairingFact> callees = new ArrayList<>();
        pairing.setAddressComposer((caller, callee) -> {
            callees.add(callee);
            return "custom:" + caller.getService() + "|" + caller.getPeer();
        });
        pairing.clientSeen(client("frontend", "s2"), "b:8080(v2)");
        serverSeen(server("b-canary", "s3", "s2"));
        assertEquals("custom:frontend|b:8080(v2)", rows.get(0).getAddress());
        assertEquals(LAYER, callees.get(0).getRuleLayer());
        assertEquals(RULE, callees.get(0).getRuleName());

        // A rule that is gone composes nothing, and nothing is built.
        pairing.setAddressComposer((caller, callee) -> null);
        pairing.clientSeen(client("frontend", "s4"), "c:8080");
        serverSeen(server("c", "s5", "s4"));
        assertEquals(1, rows.size());
    }

    @Test
    void aCallToAnotherReplicaNamesTheCallerItself() {
        serverSeen(server("frontend", "s3", "s2"));
        pairing.clientSeen(client("frontend", "s2"), "frontend:8080");
        assertEquals(1, rows.size());
        assertEquals("frontend->frontend:8080", rows.get(0).getAddress());
        assertEquals(IDManager.ServiceID.buildId("frontend", true), rows.get(0).getRepresentServiceId());
    }

    @Test
    void aGatewayPeerKeepsItsOwnNode() {
        registrar.setGateways("ingress:80"::equals);
        pairing.clientSeen(client("frontend", "s2"), "ingress:80");
        serverSeen(server("cart", "s3", "s2"));
        assertTrue(rows.isEmpty());
    }

    @Test
    void anAddressLongerThanTheAliasColumnIsCountedNotBuilt() {
        final String peer = "x".repeat(250) + ":8080";
        pairing.clientSeen(client("frontend", "s2"), peer);
        serverSeen(server("cart", "s3", "s2"));
        assertTrue(rows.isEmpty());
        assertEquals(1, pairing.tooLong());
    }

    @Test
    void rootConsumerAndNonClientSpansSendNoFact() {
        final List<PairingFact> wire = new ArrayList<>();
        pairing.setTransport(wire::add);
        pairing.serverSeen(server("frontend", "s1", ""), true, LAYER, RULE);
        // A CONSUMER span's parent is the PRODUCER span: an alias would move the producers' edges off the broker.
        final SpanMetadata consumer = server("billing", "s5", "s4");
        consumer.setKind(SpanMetadata.KIND_CONSUMER);
        pairing.serverSeen(consumer, true, LAYER, RULE);
        pairing.clientSeen(server("frontend", "s1", ""), "x:1");
        final SpanMetadata producer = client("orders", "s6");
        producer.setKind(SpanMetadata.KIND_PRODUCER);
        pairing.clientSeen(producer, "kafka:9092");
        assertTrue(wire.isEmpty(), wire.toString());
    }

    @Test
    void aServerSpanUnderALocalParentSendsNoFact() {
        // Next.js's SERVER span under the HTTP instrumentation's: its parent is a span of the same process.
        final SpanMetadata nested = server("frontend", "s3", "s2");
        nested.setParentLocation(SpanMetadata.PARENT_LOCAL);
        serverSeen(nested);
        pairing.clientSeen(client("frontend-proxy", "s2"), "172.19.0.19:8080");
        assertTrue(rows.isEmpty(), "no fact was sent, so nothing pairs with the span's parent");
    }

    @Test
    void factsTravelToTheTraceOwnerThroughTheWire() {
        // A transport that stands in for the cluster: it delivers to another node's pairing, the trace's owner.
        final SpanPairing owner = new SpanPairing();
        owner.setAliasRegistrar(registrar);
        final List<PairingFact> wire = new ArrayList<>();
        pairing.setTransport(fact -> {
            wire.add(fact);
            final PairingFact copy = new PairingFact();
            copy.deserialize(fact.serialize().build());
            owner.receive(copy);
        });
        pairing.clientSeen(client("frontend", "s2"), "172.19.0.16:3550");
        serverSeen(server("product-catalog", "s3", "s2"));
        assertEquals(2, wire.size());
        assertEquals(wire.get(0).remoteHashCode(), wire.get(1).remoteHashCode(), "both halves hash to one owner");
        assertEquals(1, rows.size(), "the owner paired them and wrote the row");
        assertEquals("frontend->172.19.0.16:3550", rows.get(0).getAddress());
    }

    @Test
    void theSampleIsSpreadOverEveryOwnerNode() {
        // The owner is picked by remoteHashCode() % size. Sampling on the same hash would tie the two: at 50 nodes
        // and 25 percent, every sampled trace has hash % 100 < 25, hence hash % 50 < 25, and nodes 25..49 never
        // own one. The sample bucket comes from another hash of the trace id, so every node does.
        final Set<Integer> sampledOwners = new HashSet<>();
        final Set<Integer> sameHashOwners = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            final PairingFact fact = PairingFact.client(
                SpanMetadata.builder().service("a").kind(SpanMetadata.KIND_CLIENT).traceId("trace-" + i)
                            .spanId("s").build(), "x:1");
            assertTrue(fact.sampleBucket() >= 0 && fact.sampleBucket() < 100);
            if (fact.sampleBucket() < 25) {
                sampledOwners.add(Math.abs(fact.remoteHashCode() % 50));
            }
            if (Math.abs(fact.remoteHashCode() % 100) < 25) {
                sameHashOwners.add(Math.abs(fact.remoteHashCode() % 50));
            }
        }
        assertEquals(50, sampledOwners.size(), "every node owns some sampled traces: " + sampledOwners);
        assertTrue(sameHashOwners.stream().allMatch(owner -> owner < 25),
            "the routing hash alone would leave half the cluster idle: " + sameHashOwners);
    }

    @Test
    void aFactSurvivesTheWire() {
        final SpanMetadata meta = SpanMetadata.builder().service("cart").kind(SpanMetadata.KIND_SERVER)
            .traceId("t1").spanId("s3").parentSpanId("s2").timestamp(1_700_000_000_600L).build();
        final PairingFact sent = PairingFact.server(meta, false, LAYER, RULE);
        final PairingFact got = new PairingFact();
        got.deserialize(sent.serialize().build());
        assertEquals("t1", got.getTraceId());
        assertEquals("s3", got.getSpanId());
        assertEquals("s2", got.getParentSpanId());
        assertEquals("cart", got.getService());
        assertEquals(LAYER, got.getRuleLayer());
        assertEquals(RULE, got.getRuleName());
        assertTrue(got.isServer());
        assertFalse(got.isNormal());
        assertEquals(1_700_000_000_600L, got.getTimestampMillis());
        assertEquals(sent.remoteHashCode(), got.remoteHashCode());

        final PairingFact caller = new PairingFact();
        caller.deserialize(PairingFact.client(client("frontend", "s2"), "cart:7070").serialize().build());
        assertEquals("cart:7070", caller.getPeer());
        assertFalse(caller.isServer());
    }

    @Test
    void anUnsampledCallSendsNothing() {
        final List<PairingFact> wire = new ArrayList<>();
        pairing.setTransport(wire::add);
        pairing.setSampled(fact -> !"s2".equals(fact.getSpanId()));
        pairing.clientSeen(client("frontend", "s2"), "cart:7070");
        pairing.clientSeen(client("frontend", "s4"), "cart:7070");
        pairing.clientSeen(client("frontend", "s6"), "cart:7070");
        assertEquals(List.of("s4", "s6"), wire.stream().map(PairingFact::getSpanId).collect(Collectors.toList()),
            "every sampled call sends its half, the same pair's included");
    }

    @Test
    void aRuleThatComposesNoAddressIsCountedNotBuilt() {
        pairing.setAddressComposer((caller, callee) -> null);
        pairing.clientSeen(client("frontend", "s2"), "c:8080");
        serverSeen(server("c", "s3", "s2"));
        pairing.setAddressComposer((caller, callee) -> {
            throw new IllegalStateException("a failing buildNetworkAlias");
        });
        pairing.clientSeen(client("frontend", "s4"), "d:8080");
        serverSeen(server("d", "s5", "s4"));
        assertTrue(rows.isEmpty());
        assertEquals(2, pairing.uncomposed(), "the failure stays in the pairing, not the span's receiver thread");
    }

    @Test
    void bothHalvesArrivingAtOnceStillMatch() throws Exception {
        final List<String> built = Collections.synchronizedList(new ArrayList<>());
        final SourceReceiver receiver = mock(SourceReceiver.class);
        doAnswer(inv -> {
            final ISource source = inv.getArgument(0);
            source.prepare();
            built.add(((NetworkAddressAliasSetup) source).getAddress());
            return null;
        }).when(receiver).receive(any());
        final SpanPairing concurrent = new SpanPairing();
        concurrent.setAliasRegistrar(
            new AliasRegistrar(receiver, new NamingControl(70, 70, 150, new EndpointNameGrouping())));
        final int calls = 5_000;
        final CyclicBarrier start = new CyclicBarrier(2);
        final Thread callers = new Thread(() -> {
            await(start);
            for (int i = 0; i < calls; i++) {
                concurrent.clientSeen(SpanMetadata.builder().service("a").kind(SpanMetadata.KIND_CLIENT)
                    .traceId("t" + i).spanId("c").parentSpanId("p").build(), "b-" + i + ":80");
            }
        });
        callers.start();
        await(start);
        for (int i = 0; i < calls; i++) {
            concurrent.serverSeen(SpanMetadata.builder().service("b").kind(SpanMetadata.KIND_SERVER)
                .traceId("t" + i).spanId("s").parentSpanId("c").build(), true, LAYER, RULE);
        }
        callers.join();
        assertEquals(calls, built.size(), "no call paired twice");
        assertEquals(calls, new HashSet<>(built).size(), "every call paired");
    }

    private static void await(final CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void serverSeen(final SpanMetadata meta) {
        pairing.serverSeen(meta, true, LAYER, RULE);
    }

    private static SpanMetadata client(final String service, final String spanId) {
        return SpanMetadata.builder().service(service).kind(SpanMetadata.KIND_CLIENT).traceId("t1").spanId(spanId)
                           .parentSpanId("s1").timestamp(1_700_000_000_500L).build();
    }

    private static SpanMetadata server(final String service, final String spanId, final String parent) {
        return SpanMetadata.builder().service(service).kind(SpanMetadata.KIND_SERVER).traceId("t1").spanId(spanId)
                           .parentSpanId(parent).root(parent.isEmpty()).timestamp(1_700_000_000_600L).build();
    }
}
