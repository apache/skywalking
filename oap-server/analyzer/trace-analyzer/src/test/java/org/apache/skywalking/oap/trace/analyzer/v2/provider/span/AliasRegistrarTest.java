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

/**
 * The rows the pairing writes, and which addresses are uninstrumented gateways.
 */
class AliasRegistrarTest {
    private static final long NOW = 1_700_000_000_000L;

    private final List<NetworkAddressAliasSetup> rows = new ArrayList<>();
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
    }

    @Test
    void aRowNamesTheServiceAndNoInstance() {
        assertTrue(registrar.register("shop::frontend->cart:7070", "shop::cart", true, NOW));
        assertFalse(registrar.register("", "shop::cart", true, NOW));
        assertFalse(registrar.register("shop::frontend->cart:7070", "", true, NOW));

        assertEquals(1, rows.size(), rows.toString());
        assertEquals("shop::frontend->cart:7070", rows.get(0).getAddress());
        assertEquals(IDManager.ServiceID.buildId("shop::cart", true), rows.get(0).getRepresentServiceId());
        assertEquals(IDManager.ServiceInstanceID.buildId(IDManager.ServiceID.buildId("shop::cart", true), ""),
            rows.get(0).getRepresentServiceInstanceId(), "one caller and peer reach every instance of the callee");
    }

    @Test
    void theLastWriteWinsAsOnTheAgentPath() {
        // A pod IP that moved from cart to ad is rewritten.
        assertTrue(registrar.register("frontend->10.0.0.7:8080", "cart", true, NOW));
        assertTrue(registrar.register("frontend->10.0.0.7:8080", "ad", true, NOW));
        assertEquals(2, rows.size(), "both writes go out: " + rows);
        assertEquals(IDManager.ServiceID.buildId("ad", true), rows.get(1).getRepresentServiceId());
    }

    @Test
    void noAddressIsAGatewayWithoutTheAgentAnalyzer() {
        assertFalse(registrar.isGateway("ingress:80"));
        // gateways.yml spells an instance without a port as port 80.
        registrar.setGateways("ingress:80"::equals);
        assertTrue(registrar.isGateway("ingress"));
        assertTrue(registrar.isGateway("ingress:80"));
        assertFalse(registrar.isGateway(""));
        assertFalse(registrar.isGateway("frontend:8080"));
    }
}
