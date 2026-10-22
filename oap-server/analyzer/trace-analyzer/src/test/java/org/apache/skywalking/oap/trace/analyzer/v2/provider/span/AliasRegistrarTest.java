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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

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
    void theLastWriteWinsAsOnTheAgentPath() {
        // A pod IP that moved from cart to ad is rewritten, whatever service this node has seen spans from.
        assertTrue(registrar.register("10.0.0.7:8080", "cart", true, "", NOW));
        assertTrue(registrar.register("10.0.0.7:8080", "ad", true, "", NOW));
        assertEquals(2, rows.size(), "both writes go out: " + rows);
        assertEquals(IDManager.ServiceID.buildId("ad", true), rows.get(1).getRepresentServiceId());
    }

    @Test
    void aGroupedServiceIsNamedByTheHostItsCallersDial() {
        known.record("shop::cart");
        known.record("shop::frontend-proxy");
        assertEquals("shop::cart", registrar.serviceNamedBy("cart", "shop::frontend"));
        assertEquals("shop::cart", registrar.serviceNamedBy("cart.shop.svc.cluster.local", "frontend-web"));
        assertEquals("shop::cart", registrar.serviceNamedBy("shop::cart", "frontend-web"));
        assertNull(registrar.serviceNamedBy("payment", "shop::frontend"));
        assertEquals("shop::cart", registrar.serviceByShortName("cart"), "another group's caller only guesses it");
    }

    @Test
    void theProxyGuardHoldsForGroupedServices() {
        // frontend behind an Envoy that keeps the Host header reports the proxy's address as its own.
        known.record("shop::frontend-proxy");
        assertFalse(registrar.register("frontend-proxy:8080", "shop::frontend", true, "", NOW));
        assertTrue(registrar.register("frontend-proxy:8080", "shop::frontend-proxy", true, "", NOW));
        assertEquals(1, rows.size(), "only the proxy's own registration: " + rows);
    }

    @Test
    void aBareHostNamesTheCallersOwnGroup() {
        // One application in two namespaces: each frontend dials `cart`, which Kubernetes resolves in its own one.
        known.record("shop::cart");
        known.record("outlet::cart");
        assertEquals("shop::cart", registrar.serviceNamedBy("cart", "shop::frontend"));
        assertEquals("outlet::cart", registrar.serviceNamedBy("cart", "outlet::frontend"));
        assertNull(registrar.serviceNamedBy("cart", "frontend-web"), "an ungrouped caller has no group to prefer");
        assertNull(registrar.serviceByShortName("cart"), "two groups share the short name");
    }

    @Test
    void aQualifiedHostNamesTheGroupItIsQualifiedWith() {
        known.record("shop::cart");
        known.record("outlet::cart");
        assertEquals("outlet::cart", registrar.serviceNamedBy("cart.outlet", "shop::frontend"));
        assertEquals("outlet::cart", registrar.serviceNamedBy("cart.outlet.svc", "shop::frontend"));
        assertEquals("outlet::cart", registrar.serviceNamedBy("cart.outlet.svc.cluster.local", "shop::frontend"));
        assertEquals("shop::cart", registrar.serviceNamedBy("cart.shop.svc.cluster.local", "frontend-web"));
        assertNull(registrar.serviceNamedBy("cart.example.com", "shop::frontend"),
            "a qualified host is not resolved in the caller's group");
    }

    @Test
    void aHostQualifiedWithAGroupThisNodeHasNotSeenIsLeftToTheAddressRow() {
        // outlet's cart emits to another node: the host names outlet, so shop's cart must not answer for it.
        known.record("shop::cart");
        assertNull(registrar.serviceNamedBy("cart.outlet.svc.cluster.local", "shop::frontend"));
        assertNull(registrar.serviceNamedBy("cart.outlet", "shop::frontend"));
        assertEquals("shop::cart", registrar.serviceNamedBy("cart", "shop::frontend"));
    }

    @Test
    void anUngroupedFleetNamesHostsAsBefore() {
        known.record("cart");
        known.record("frontend-proxy");
        assertEquals("cart", registrar.serviceNamedBy("cart", "frontend"));
        assertEquals("cart", registrar.serviceNamedBy("cart.default.svc.cluster.local", "frontend"));
        assertNull(registrar.serviceNamedBy("payment", "frontend"));
        assertFalse(registrar.register("frontend-proxy:8080", "frontend", true, "", NOW));
    }

    @Test
    void anotherGroupsServiceIsOnlyAGuess() {
        // This node has seen outlet's cart and not shop's: a cart a shop service dials is not named here.
        known.record("outlet::cart");
        assertNull(registrar.serviceNamedBy("cart", "shop::frontend"));
        assertNull(registrar.serviceNamedBy("cart.shop.svc.cluster.local", "shop::frontend"));
        assertEquals("outlet::cart", registrar.serviceByShortName("cart"));
        assertNull(registrar.serviceByShortName("cart.shop.svc.cluster.local"), "a qualified host says its group");
    }

    @Test
    void theCallersGroupWinsOverAnUngroupedServiceOfTheSameName() {
        known.record("shop::cart");
        known.record("cart");
        assertEquals("shop::cart", registrar.serviceNamedBy("cart", "shop::frontend"));
        assertEquals("cart", registrar.serviceNamedBy("cart", "frontend-web"));
        assertEquals("cart", registrar.serviceNamedBy("cart.default.svc.cluster.local", "frontend-web"));
    }

    @Test
    void theProxyGuardHoldsWhenTwoGroupsShareTheProxysName() {
        known.record("shop::frontend-proxy");
        known.record("outlet::frontend-proxy");
        assertFalse(registrar.register("frontend-proxy:8080", "shop::frontend", true, "", NOW));
        assertFalse(registrar.register("frontend-proxy:8080", "outlet::frontend", true, "", NOW));
        assertTrue(registrar.register("frontend-proxy:8080", "shop::frontend-proxy", true, "", NOW));
        assertEquals(1, rows.size(), "only the proxy's own registration: " + rows);
    }

    @Test
    void theProxyGuardHoldsForAProxyInAnotherGroup() {
        // An ingress namespace's Envoy keeps the Host header, so shop's frontend reports the proxy's address.
        known.record("infra::frontend-proxy");
        assertFalse(registrar.register("frontend-proxy:8080", "shop::frontend", true, "", NOW));
        assertTrue(registrar.register("frontend-proxy:8080", "infra::frontend-proxy", true, "", NOW));
        assertEquals(1, rows.size(), "only the proxy's own registration: " + rows);
    }

    @Test
    void theProxyGuardHoldsForAQualifiedHostOfAProxyGroupedOtherThanItsNamespace() {
        // Grouped by service.namespace=infra, the proxy runs in the k8s namespace ingress.
        known.record("infra::frontend-proxy");
        assertFalse(registrar.register("frontend-proxy.ingress:8080", "shop::frontend", true, "", NOW));
        assertFalse(registrar.register("frontend-proxy.ingress.svc.cluster.local:8080", "shop::frontend", true, "", NOW));
        assertTrue(rows.isEmpty(), "the proxy's address is never shop's frontend's: " + rows);
    }

    @Test
    void theProxyGuardHoldsWhenTwoOtherGroupsShareTheProxysName() {
        known.record("infra::frontend-proxy");
        known.record("edge::frontend-proxy");
        assertFalse(registrar.register("frontend-proxy:8080", "shop::frontend", true, "", NOW));
        assertTrue(registrar.register("frontend-proxy:8080", "infra::frontend-proxy", true, "", NOW));
        assertEquals(1, rows.size(), "only a proxy's own registration: " + rows);
    }

    @Test
    void aGroupedServiceCutByTheNamingRulesRegistersItsOwnAddress() {
        // The naming rules cut a service name at 20 characters; spans record its short name uncut.
        final List<NetworkAddressAliasSetup> written = new ArrayList<>();
        final SourceReceiver receiver = mock(SourceReceiver.class);
        doAnswer(inv -> {
            final ISource s = inv.getArgument(0);
            s.prepare();
            written.add((NetworkAddressAliasSetup) s);
            return null;
        }).when(receiver).receive(any());
        final AliasRegistrar cutting = new AliasRegistrar(
            receiver, new NamingControl(20, 70, 150, new EndpointNameGrouping()), known);
        known.record("payments-team::shippingservice");
        assertTrue(cutting.register("shippingservice.prod.svc:50051", "payments-team::shippingservice", true, "", NOW));
        assertEquals(1, written.size(), "its own address: " + written);
    }

    @Test
    void aShortNameHoldingADotIsMatchedWhole() {
        known.record("shop::checkout.api");
        assertEquals("shop::checkout.api", registrar.serviceNamedBy("checkout.api", "shop::frontend"));
        assertNull(registrar.serviceNamedBy("checkout.api", "outlet::frontend"), "outlet has no checkout.api");
        assertEquals("shop::checkout.api", registrar.serviceByShortName("checkout.api"));
        assertNull(registrar.serviceByShortName("checkout"), "never by the first label");
    }

    @Test
    void theProxyGuardHoldsForAProxyWhoseShortNameHoldsADot() {
        known.record("infra::edge.proxy");
        assertFalse(registrar.register("edge.proxy:8080", "shop::frontend", true, "", NOW));
        assertTrue(registrar.register("edge.proxy:8080", "infra::edge.proxy", true, "", NOW));
        assertEquals(1, rows.size(), "only the proxy's own registration: " + rows);
    }

    @Test
    void aGroupedCalleeRegistersItsAddressWhereAnotherGroupsServiceOfItsNameIsKnown() {
        // A pairing owner that has seen only outlet's cart still writes the row shop's cart was dialed at.
        known.record("outlet::cart");
        assertTrue(registrar.register("cart:7070", "shop::cart", true, "", NOW));
        // An ungrouped one is still refused an address another service is named by.
        assertFalse(registrar.register("cart:7070", "frontend", true, "", NOW));
        assertEquals(1, rows.size(), "only shop's cart: " + rows);
    }

    @Test
    void noAddressIsAGatewayWithoutTheAgentAnalyzer() {
        assertFalse(registrar.isGateway("ingress:80"));
        registrar.setGateways("ingress:80"::equals);
        assertTrue(registrar.isGateway("ingress"));
        assertFalse(registrar.isGateway(""));
        assertFalse(registrar.isGateway("frontend:8080"));
    }
}
