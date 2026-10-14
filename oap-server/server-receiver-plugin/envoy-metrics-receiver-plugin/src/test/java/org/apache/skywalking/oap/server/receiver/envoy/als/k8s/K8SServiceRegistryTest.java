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

package org.apache.skywalking.oap.server.receiver.envoy.als.k8s;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.apache.skywalking.library.kubernetes.CachedRead;
import org.apache.skywalking.library.kubernetes.KubernetesEndpoints;
import org.apache.skywalking.library.kubernetes.KubernetesPods;
import org.apache.skywalking.library.kubernetes.KubernetesReadFailedException;
import org.apache.skywalking.library.kubernetes.KubernetesServices;
import org.apache.skywalking.library.kubernetes.ObjectID;
import org.apache.skywalking.oap.server.core.config.K8sMetadataReadSettings;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class K8SServiceRegistryTest {
    private KubernetesPods originalPods;
    private KubernetesServices originalServices;
    private KubernetesEndpoints originalEndpoints;

    private KubernetesPods pods;
    private KubernetesServices services;
    private KubernetesEndpoints endpoints;
    private final Set<String> nodeAddresses = new HashSet<>();

    @BeforeEach
    public void setUp() throws Exception {
        originalPods = KubernetesPods.INSTANCE;
        originalServices = KubernetesServices.INSTANCE;
        originalEndpoints = KubernetesEndpoints.INSTANCE;
        pods = Mockito.mock(KubernetesPods.class);
        services = Mockito.mock(KubernetesServices.class);
        endpoints = Mockito.mock(KubernetesEndpoints.class);
        ReflectUtil.setInternalState(KubernetesPods.class, "INSTANCE", pods);
        ReflectUtil.setInternalState(KubernetesServices.class, "INSTANCE", services);
        ReflectUtil.setInternalState(KubernetesEndpoints.class, "INSTANCE", endpoints);
        when(pods.findByIP(anyString())).thenReturn(Optional.empty());
        when(endpoints.list()).thenReturn(Collections.emptyList());
    }

    @AfterEach
    public void tearDown() throws Exception {
        ReflectUtil.setInternalState(KubernetesPods.class, "INSTANCE", originalPods);
        ReflectUtil.setInternalState(KubernetesServices.class, "INSTANCE", originalServices);
        ReflectUtil.setInternalState(KubernetesEndpoints.class, "INSTANCE", originalEndpoints);
    }

    @Test
    public void theAnalyzersOfOneConfigShareOneRegistry() throws Exception {
        final EnvoyMetricReceiverConfig config = config("${service.metadata.name}");

        assertSame(K8SServiceRegistry.sharedFor(config), K8SServiceRegistry.sharedFor(config));
        assertNotSame(K8SServiceRegistry.sharedFor(config),
                      K8SServiceRegistry.sharedFor(config("${service.metadata.name}")));
    }

    @Test
    public void readsTheServiceOfThePodThroughItsEndpoints() throws Exception {
        stubWebPod("10.0.0.1");

        final ServiceMetaInfo found = registry("${service.metadata.name}").readServiceMetaInfo("10.0.0.1");

        assertEquals("web", found.getServiceName());
        assertEquals("web-1.default", found.getServiceInstanceName());
    }

    @Test
    public void aNodeAddressMatchesNothing() throws Exception {
        nodeAddresses.add("192.168.1.10");

        assertNull(registry("${service.metadata.name}").readServiceMetaInfo("192.168.1.10"));
        verify(pods, never()).findByIP(anyString());
    }

    @Test
    public void anIPWithoutPodOrEndpointsMatchesNothing() throws Exception {
        final K8SServiceRegistry registry = registry("${service.metadata.name}");
        assertNull(registry.readServiceMetaInfo("10.0.0.1"));

        when(pods.findByIP("10.0.0.2")).thenReturn(Optional.of(webPod("10.0.0.2")));
        assertNull(registry.readServiceMetaInfo("10.0.0.2"));
    }

    @Test
    public void aPodNotInTheEndpointsReadIsLookedUpInEndpointsReadAgainForBeingNew() throws Exception {
        stubWebPod("10.0.0.1");
        when(pods.findByIP("10.0.0.2")).thenReturn(Optional.of(webPod("10.0.0.2")));
        when(endpoints.list(K8sMetadataReadSettings.getNotFoundRetryPeriod()))
            .thenReturn(Collections.singletonList(webEndpoints("10.0.0.1", "10.0.0.2")));

        final ServiceMetaInfo found = registry("${service.metadata.name}").readServiceMetaInfo("10.0.0.2");

        assertEquals("web", found.getServiceName());
        verify(endpoints).list(K8sMetadataReadSettings.getNotFoundRetryPeriod());
    }

    @Test
    public void withoutTheNodesNoIPIsANodes() throws Exception {
        final K8SServiceRegistry registry = new K8SServiceRegistry(config("${service.metadata.name}"));
        ReflectUtil.setInternalState(registry, "nodeIPs", new CachedRead<Set<String>>(() -> {
            throw new KubernetesReadFailedException("list nodes", "code=403");
        }, Collections.emptySet()));
        stubWebPod("10.0.0.1");

        assertEquals("web", registry.readServiceMetaInfo("10.0.0.1").getServiceName());
    }

    @Test
    public void aFailedReadIsAFailureNotFindingNothing() throws Exception {
        when(pods.findByIP("10.0.0.1")).thenThrow(new KubernetesReadFailedException("list pods", "code=403"));

        assertThrows(KubernetesReadFailedException.class,
                     () -> registry("${service.metadata.name}").readServiceMetaInfo("10.0.0.1"));
    }

    @Test
    public void aLookupDoesNotWaitForTheRead() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        when(pods.findByIP("10.0.0.1")).thenAnswer(invocation -> {
            entered.countDown();
            release.await(30, TimeUnit.SECONDS);
            finished.countDown();
            return Optional.empty();
        });
        final EnvoyMetricReceiverConfig config = config("${service.metadata.name}");
        final K8SServiceRegistry registry = registry(config);
        try {
            assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertSame(config.serviceMetaInfoFactory().unknown(), registry.findService("10.0.0.1")));
            // The read runs on the shared background lane: it must use the mocks before tearDown restores the
            // singletons, so this waits for it to start and then to end.
            assertTrue(entered.await(30, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
        assertTrue(finished.await(30, TimeUnit.SECONDS));
    }

    private K8SServiceRegistry registry(final String k8sServiceNameRule) throws Exception {
        return registry(config(k8sServiceNameRule));
    }

    private K8SServiceRegistry registry(final EnvoyMetricReceiverConfig config) {
        return new K8SServiceRegistry(config) {
            @Override
            public boolean isNode(final String ip) {
                return nodeAddresses.contains(ip);
            }
        };
    }

    private void stubWebPod(final String ip) {
        final Service service = new ServiceBuilder()
            .withNewMetadata().withNamespace("default").withName("web").endMetadata()
            .build();
        when(pods.findByIP(ip)).thenReturn(Optional.of(webPod(ip)));
        when(endpoints.list()).thenReturn(Collections.singletonList(webEndpoints(ip)));
        when(services.findByID(ObjectID.builder().namespace("default").name("web").build()))
            .thenReturn(Optional.of(service));
    }

    private static Endpoints webEndpoints(final String... ips) {
        final EndpointsBuilder builder = new EndpointsBuilder()
            .withNewMetadata().withNamespace("default").withName("web").endMetadata();
        final var subset = builder.addNewSubset();
        for (final String ip : ips) {
            subset.addNewAddress().withIp(ip).endAddress();
        }
        return subset.endSubset().build();
    }

    private static Pod webPod(final String ip) {
        return new PodBuilder()
            .withNewMetadata().withNamespace("default").withName("web-1").addToLabels("app", "web").endMetadata()
            .withNewStatus().withPodIP(ip).endStatus()
            .build();
    }

    private static EnvoyMetricReceiverConfig config(final String k8sServiceNameRule) throws Exception {
        final EnvoyMetricReceiverConfig config = new EnvoyMetricReceiverConfig();
        ReflectUtil.setInternalState(config, "k8sServiceNameRule", k8sServiceNameRule);
        return config;
    }
}
