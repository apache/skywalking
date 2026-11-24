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
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SpanPairingDebugGateTest {
    private static final String PEER = "172.19.0.16:3550";

    private SpanPairing pairing;
    private TestListener listener;

    @BeforeEach
    void setUp() {
        pairing = new SpanPairing();
        pairing.setAliasRegistrar(new AliasRegistrar(
            mock(SourceReceiver.class), new NamingControl(70, 70, 150, new EndpointNameGrouping())));
        listener = new TestListener();
    }

    @Test
    void aListenerSeesTheWaitingHalfThenTheMatchItsPairCompletes() {
        pairing.getDebugGate().add(listener);
        pairing.clientSeen(client("frontend", "s2"), PEER);
        pairing.serverSeen(server("catalog", "s3", "s2"), true, "OTLP_GENERAL", "otlp-general");

        assertEquals(2, listener.received.size());
        assertEquals("frontend", listener.received.get(0).getService());
        assertNull(listener.matches.get(0), "the caller half waits: nothing to pair with yet");
        final PairingMatch match = listener.matches.get(1);
        assertSame(listener.received.get(0), match.getCaller());
        assertSame(listener.received.get(1), match.getCallee());
        assertEquals(MatchOutcome.REGISTERED, match.getOutcome());
        assertEquals(IDManager.NetworkAddressAliasDefine.buildCallerScopedAddress("frontend", PEER),
            match.getAddress());
        assertTrue(listener.left.isEmpty(), "a matched half leaves the buffer by the match, not unmatched");
    }

    @Test
    void aGatewayAndAFailingAddressSayWhyNoAliasWasPushed() {
        pairing.getDebugGate().add(listener);
        pairing.getAliasRegistrar().setGateways(PEER::equals);
        pairing.clientSeen(client("frontend", "s2"), PEER);
        pairing.serverSeen(server("catalog", "s3", "s2"), true, "OTLP_GENERAL", "otlp-general");
        assertEquals(MatchOutcome.GATEWAY, listener.matches.get(1).getOutcome());
        assertNull(listener.matches.get(1).getAddress());

        pairing.getAliasRegistrar().setGateways(address -> false);
        pairing.setAddressComposer((caller, callee) -> {
            throw new IllegalStateException("no peer");
        });
        pairing.clientSeen(client("frontend", "s4"), PEER);
        pairing.serverSeen(server("catalog", "s5", "s4"), true, "OTLP_GENERAL", "otlp-general");
        final PairingMatch failed = listener.matches.get(3);
        assertEquals(MatchOutcome.UNCOMPOSED, failed.getOutcome());
        assertEquals("IllegalStateException: no peer", failed.getFailure());
    }

    @Test
    void aWaitingHalfReplacedByALaterOneOfItsKeyLeavesUnmatched() {
        pairing.getDebugGate().add(listener);
        pairing.clientSeen(client("frontend", "s2"), PEER);
        pairing.clientSeen(client("frontend", "s2"), "other:80");

        assertEquals(1, listener.left.size());
        assertEquals(PEER, listener.left.get(0).getPeer());
        assertEquals("replaced", listener.causes.get(0));
    }

    @Test
    void withNoListenerTheGateIsOffAndARemovedOneHearsNothing() {
        assertFalse(pairing.getDebugGate().isOn());
        pairing.getDebugGate().add(listener);
        pairing.getDebugGate().remove(listener);
        assertFalse(pairing.getDebugGate().isOn());

        pairing.clientSeen(client("frontend", "s2"), PEER);
        assertTrue(listener.received.isEmpty());
    }

    private static SpanMetadata client(final String service, final String spanId) {
        return SpanMetadata.builder().service(service).kind(SpanMetadata.KIND_CLIENT).traceId("t1").spanId(spanId)
                           .parentSpanId("s1").timestamp(1_700_000_000_500L).build();
    }

    private static SpanMetadata server(final String service, final String spanId, final String parent) {
        return SpanMetadata.builder().service(service).kind(SpanMetadata.KIND_SERVER).traceId("t1").spanId(spanId)
                           .parentSpanId(parent).timestamp(1_700_000_000_600L).build();
    }

    private static final class TestListener implements PairingDebugListener {
        private final List<PairingFact> received = new ArrayList<>();
        private final List<PairingMatch> matches = new ArrayList<>();
        private final List<PairingFact> left = new ArrayList<>();
        private final List<String> causes = new ArrayList<>();

        @Override
        public void received(final PairingFact half, final PairingMatch match) {
            received.add(half);
            matches.add(match);
        }

        @Override
        public void leftUnmatched(final PairingFact half, final String cause) {
            left.add(half);
            causes.add(cause);
        }
    }
}
