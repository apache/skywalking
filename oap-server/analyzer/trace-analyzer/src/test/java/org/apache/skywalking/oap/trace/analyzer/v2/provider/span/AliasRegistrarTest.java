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
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The guards a fleet that mixes SkyWalking agents and OpenTelemetry SDKs needs, since both paths write the same
 * alias rows.
 */
class AliasRegistrarTest {
    private static final long NOW = 1_700_000_000_000L;

    private final List<NetworkAddressAliasSetup> rows = new ArrayList<>();
    private final KnownServices known = new KnownServices();
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
    }

    @Test
    void anUninstrumentedGatewayIsNeverAliased() {
        // gateways.yml spells an instance without a port as port 80.
        registrar.setGateways("ingress:80"::equals);
        assertFalse(registrar.register("ingress:80", "frontend", true, "", NOW));
        assertFalse(registrar.register("ingress", "frontend", true, "", NOW));
        assertTrue(registrar.register("frontend:8080", "frontend", true, "", NOW));
        assertEquals(1, rows.size(), "only the address that is no gateway: " + rows);
    }

    @Test
    void aRowTheAgentPathKeepsWritingIsNotOverwritten() {
        // An agent-instrumented proxy owns its address; the OTel service behind it sees the same Host.
        final NetworkAddressAliasCache cache = mock(NetworkAddressAliasCache.class);
        when(cache.get("10.0.0.5:80")).thenReturn(row("10.0.0.5:80", "nginx", NOW));
        when(cache.get("old-proxy:80")).thenReturn(row("old-proxy:80", "old-proxy", NOW - 20 * 60_000L));
        registrar.setAliasCache(cache);

        assertFalse(registrar.register("10.0.0.5:80", "frontend", true, "", NOW), "the agent's live row stands");
        assertTrue(registrar.register("10.0.0.5:80", "nginx", true, "", NOW), "the same service may refresh it");
        assertTrue(registrar.register("old-proxy:80", "frontend", true, "", NOW),
            "a row nobody wrote for twenty minutes is free");

        known.record("nginx");
        assertTrue(registrar.register("10.0.0.5:80", "frontend", true, "", NOW),
            "a service that sends OTLP spans is this path's own row, last writer wins as on the agent path");
    }

    private static NetworkAddressAlias row(final String address, final String service, final long lastWrite) {
        final NetworkAddressAlias row = new NetworkAddressAlias();
        row.setAddress(address);
        row.setRepresentServiceId(IDManager.ServiceID.buildId(service, true));
        row.setLastUpdateTimeBucket(TimeBucket.getMinuteTimeBucket(lastWrite));
        return row;
    }
}
