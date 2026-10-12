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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SpanPairingTest {
    private final List<NetworkAddressAliasSetup> rows = new ArrayList<>();
    private final KnownServices known = new KnownServices();
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
        registrar = new AliasRegistrar(receiver, new NamingControl(70, 70, 150, new EndpointNameGrouping()), known);
        pairing = new SpanPairing();
        pairing.setAliasRegistrar(registrar);
    }

    @Test
    void serverFirstResolvesTheClientOnTheSpot() {
        pairing.serverSeen(server("product-catalog", "s3", "s2"), true);
        assertEquals("product-catalog", pairing.clientSeen(client("frontend", "s2"), "172.19.0.16:3550"));
        assertEquals(1, rows.size());
        assertEquals("172.19.0.16:3550", rows.get(0).getAddress());
        assertEquals(IDManager.ServiceID.buildId("product-catalog", true), rows.get(0).getRepresentServiceId());
    }

    @Test
    void clientFirstLearnsWhenTheServerArrives() {
        assertNull(pairing.clientSeen(client("frontend", "s2"), "172.19.0.16:3550"),
            "nothing to pair with yet: the client waits");
        assertTrue(rows.isEmpty());
        pairing.serverSeen(server("product-catalog", "s3", "s2"), true);
        assertEquals(1, rows.size());
        assertEquals(IDManager.ServiceID.buildId("product-catalog", true), rows.get(0).getRepresentServiceId());
        // The pair is consumed: a second SERVER span with the same parent learns nothing new.
        pairing.serverSeen(server("product-catalog", "s4", "s2"), true);
        assertEquals(1, rows.size());
        // The answer is kept: the next caller of the address resolves without waiting for the cache timer.
        assertEquals("product-catalog", pairing.clientSeen(client("frontend", "s9"), "172.19.0.16:3550"));
        assertEquals(1, rows.size(), "a remembered address writes no second row");
    }

    @Test
    void aCallInsideOneServiceIsNotAnAlias() {
        pairing.serverSeen(server("frontend", "s3", "s2"), true);
        assertNull(pairing.clientSeen(client("frontend", "s2"), "frontend:8080"));
        assertTrue(rows.isEmpty());
    }

    @Test
    void rootAndNonClientSpansAreIgnored() {
        pairing.serverSeen(server("frontend", "s1", ""), true);
        assertNull(pairing.clientSeen(server("frontend", "s1", ""), "x:1"), "a SERVER span is no caller");
        assertTrue(rows.isEmpty());
    }

    @Test
    void aLearnedPairStillPassesTheGuard() {
        // The browser dialed the proxy, and the proxy's own SERVER span is what pairs with it: allowed.
        known.record("frontend-proxy");
        pairing.serverSeen(server("frontend-proxy", "s3", "s2"), true);
        assertEquals("frontend-proxy", pairing.clientSeen(client("frontend-web", "s2"), "frontend-proxy:8080"));
        assertEquals(1, rows.size());
        // A pair that would map another service's name elsewhere is refused by the registrar.
        assertFalse(registrar.register("frontend-proxy:8080", "frontend", true, "", 1_700_000_000_000L));
        assertEquals(1, rows.size());
    }

    @Test
    void factsTravelToTheTraceOwnerAndBackThroughTheWorker() {
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
        assertNull(pairing.clientSeen(client("frontend", "s2"), "172.19.0.16:3550"),
            "the fact went to the owner; this node has no answer yet");
        pairing.serverSeen(server("product-catalog", "s3", "s2"), true);
        assertEquals(2, wire.size());
        assertEquals(wire.get(0).remoteHashCode(), wire.get(1).remoteHashCode(), "both halves hash to one owner");
        assertEquals(1, rows.size(), "the owner paired them and wrote the row");
        assertEquals("172.19.0.16:3550", rows.get(0).getAddress());
        assertEquals(IDManager.ServiceID.buildId("product-catalog", true), rows.get(0).getRepresentServiceId());
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
        final SpanMetadata meta = SpanMetadata.builder().service("cart").serviceInstance("cart-1").kind(SpanMetadata.KIND_SERVER)
            .traceId("t1").spanId("s3").parentSpanId("s2").timestamp(1_700_000_000_600L).build();
        final PairingFact sent = PairingFact.server(meta, false);
        final PairingFact got = new PairingFact();
        got.deserialize(sent.serialize().build());
        assertEquals("t1", got.getTraceId());
        assertEquals("s3", got.getSpanId());
        assertEquals("s2", got.getParentSpanId());
        assertEquals("cart", got.getService());
        assertEquals("cart-1", got.getInstance());
        assertTrue(got.isServer());
        assertFalse(got.isNormal());
        assertEquals(1_700_000_000_600L, got.getTimestampMillis());
        assertEquals(sent.remoteHashCode(), got.remoteHashCode());
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
