/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.server.receiver.envoy.als.istio;

import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;
import io.fabric8.istio.api.networking.v1beta1.ServiceEntry;
import io.fabric8.istio.api.networking.v1beta1.ServiceEntryBuilder;
import io.fabric8.istio.api.networking.v1beta1.ServiceEntryResolution;
import io.fabric8.istio.api.networking.v1beta1.WorkloadEntrySpecBuilder;
import org.apache.skywalking.library.kubernetes.FailureSummaryLogger;
import org.apache.skywalking.library.kubernetes.IstioServiceEntries;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IstioServiceEntryRegistryTest {
    private IstioServiceEntries original;
    private IstioServiceEntries serviceEntries;

    @BeforeEach
    public void setUp() throws Exception {
        original = IstioServiceEntries.INSTANCE;
        serviceEntries = Mockito.mock(IstioServiceEntries.class);
        ReflectUtil.setInternalState(IstioServiceEntries.class, "INSTANCE", serviceEntries);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ReflectUtil.setInternalState(IstioServiceEntries.class, "INSTANCE", original);
    }

    @Test
    public void theAnalyzersOfOneConfigShareOneRegistry() throws Exception {
        final EnvoyMetricReceiverConfig config = config("");

        assertSame(IstioServiceEntryRegistry.sharedFor(config), IstioServiceEntryRegistry.sharedFor(config));
    }

    @Test
    public void matchesStaticAddressesRangesAndEndpoints() throws Exception {
        when(serviceEntries.list()).thenReturn(Arrays.asList(
            staticEntry("default", "range").editSpec().withAddresses("10.1.0.0/16").endSpec().build(),
            staticEntry("default", "workload").editSpec()
                .withEndpoints(new WorkloadEntrySpecBuilder().withAddress("10.2.0.5").build()).endSpec().build(),
            staticEntry("default", "single").editSpec().withAddresses("10.3.0.7").endSpec().build(),
            staticEntry("default", "partial").editSpec().withAddresses(null, "10.4.0.1").endSpec().build()
        ));
        final IstioServiceEntryRegistry registry = new IstioServiceEntryRegistry(config(""));

        assertEquals("range.default", registry.readServiceMetaInfo("10.1.2.3").getServiceName());
        assertEquals("workload.default", registry.readServiceMetaInfo("10.2.0.5").getServiceName());
        assertEquals("single.default", registry.readServiceMetaInfo("10.3.0.7").getServiceName());
        assertEquals("partial.default", registry.readServiceMetaInfo("10.4.0.1").getServiceName());
        assertNull(registry.readServiceMetaInfo("10.9.9.9"));
    }

    @Test
    public void skipsServiceEntriesOfIgnoredNamespaces() throws Exception {
        when(serviceEntries.list()).thenReturn(Collections.singletonList(
            staticEntry("istio-system", "mirror").editSpec().withAddresses("10.3.0.7").endSpec().build()));
        assertNull(new IstioServiceEntryRegistry(config("istio-system")).readServiceMetaInfo("10.3.0.7"));
    }

    @Test
    public void createsOneResolverPerDnsHostname() throws Exception {
        final Map<String, AtomicInteger> created = new ConcurrentHashMap<>();
        final IstioServiceEntryRegistry registry = new IstioServiceEntryRegistry(config("istio-system")) {
            @Override
            protected DnsAddressEndpointGroup createHostnameResolver(final String host) {
                created.computeIfAbsent(host, it -> new AtomicInteger()).incrementAndGet();
                return resolverOf(host, "1.2.3.4");
            }
        };

        registry.createHostnameResolvers(dnsEntry("default", "api", "a.example.com", "*.wildcard.example.com"));
        registry.createHostnameResolvers(dnsEntry("default", "api-v2", "a.example.com", "b.example.com"));
        registry.createHostnameResolvers(dnsEntry("istio-system", "mirror", "c.example.com"));
        registry.createHostnameResolvers(
            staticEntry("default", "static").editSpec().withHosts("d.example.com").endSpec().build());

        assertEquals(Set.of("a.example.com", "b.example.com"), registry.hostnameResolvers.keySet());
        assertEquals(1, created.get("a.example.com").get());
    }

    @Test
    public void readingStartsResolvingTheHostnamesAndMatchesThemOnceResolved() throws Exception {
        final Map<String, DnsAddressEndpointGroup> resolutions = new ConcurrentHashMap<>();
        resolutions.put("a.example.com", pendingResolver());
        resolutions.put("b.example.com", pendingResolver());
        final IstioServiceEntryRegistry registry = new IstioServiceEntryRegistry(config("")) {
            @Override
            protected DnsAddressEndpointGroup createHostnameResolver(final String host) {
                return resolutions.get(host);
            }
        };
        when(serviceEntries.list()).thenReturn(Collections.singletonList(
            dnsEntry("default", "api", "a.example.com", "b.example.com")));

        assertNull(registry.readServiceMetaInfo("1.2.3.4"));
        assertEquals(Set.of("a.example.com", "b.example.com"), registry.hostnameResolvers.keySet());

        registry.hostnameResolvers.put("b.example.com", resolverOf("b.example.com", "1.2.3.4"));
        assertEquals("api.default", registry.readServiceMetaInfo("1.2.3.4").getServiceName());
    }

    @Test
    public void aLookupDoesNotWaitForTheRead() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        when(serviceEntries.list()).thenAnswer(invocation -> {
            entered.countDown();
            release.await(30, TimeUnit.SECONDS);
            finished.countDown();
            return Collections.emptyList();
        });
        final EnvoyMetricReceiverConfig config = config("");
        final IstioServiceEntryRegistry registry = new IstioServiceEntryRegistry(config);
        try {
            assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertSame(config.serviceMetaInfoFactory().unknown(), registry.findService("10.3.0.7")));
            // The read runs on the shared background lane: it must use the mocks before tearDown restores the
            // singletons, so this waits for it to start and then to end.
            assertTrue(entered.await(30, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
        assertTrue(finished.await(30, TimeUnit.SECONDS));
    }

    @Test
    public void shortHostnameResolutionFailureLogsNothing() {
        final List<String> warnings = new ArrayList<>();
        final List<String> recoveries = new ArrayList<>();
        final IstioServiceEntryRegistry.HostnameResolutionListener listener =
            new IstioServiceEntryRegistry.HostnameResolutionListener(
                "a.example.com",
                new FailureSummaryLogger("Istio ServiceEntry hostname resolution",
                                         "lookups use the IPs last resolved, if any", warnings::add, recoveries::add));

        listener.onFailure(Collections.emptyList(), new UnknownHostException("a.example.com"), "", 1000, 1);
        listener.onFailure(Collections.emptyList(), new UnknownHostException("a.example.com"), "", 2000, 2);
        listener.onSuccess(Collections.emptyList(), Collections.emptyList(), "");

        // Resolved again within the first minute: nothing to report.
        assertTrue(warnings.isEmpty());
        assertTrue(recoveries.isEmpty());
    }

    private static ServiceEntryBuilder staticEntry(final String namespace, final String name) {
        return new ServiceEntryBuilder()
            .withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
            .withNewSpec().withResolution(ServiceEntryResolution.STATIC).endSpec();
    }

    private static ServiceEntry dnsEntry(final String namespace, final String name, final String... hosts) {
        return new ServiceEntryBuilder()
            .withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
            .withNewSpec().withResolution(ServiceEntryResolution.DNS).withHosts(hosts).endSpec()
            .build();
    }

    private static DnsAddressEndpointGroup resolverOf(final String host, final String ip) {
        final List<Endpoint> endpoints = Collections.singletonList(Endpoint.of(host).withIpAddr(ip));
        final DnsAddressEndpointGroup resolver = mock(DnsAddressEndpointGroup.class);
        when(resolver.whenReady()).thenReturn(CompletableFuture.completedFuture(endpoints));
        when(resolver.endpoints()).thenReturn(endpoints);
        return resolver;
    }

    private static DnsAddressEndpointGroup pendingResolver() {
        final DnsAddressEndpointGroup resolver = mock(DnsAddressEndpointGroup.class);
        when(resolver.whenReady()).thenReturn(new CompletableFuture<>());
        return resolver;
    }

    private static EnvoyMetricReceiverConfig config(final String ignoredNamespaces) throws Exception {
        final EnvoyMetricReceiverConfig config = new EnvoyMetricReceiverConfig();
        ReflectUtil.setInternalState(
            config, "istioServiceNameRule", "${serviceEntry.metadata.name}.${serviceEntry.metadata.namespace}");
        ReflectUtil.setInternalState(config, "istioServiceEntryIgnoredNamespaces", ignoredNamespaces);
        return config;
    }
}
