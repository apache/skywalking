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

package org.apache.skywalking.oap.meter.analyzer.v2.k8s;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.apache.skywalking.library.kubernetes.KubernetesPods;
import org.apache.skywalking.library.kubernetes.KubernetesServices;
import org.apache.skywalking.library.kubernetes.ObjectID;
import org.apache.skywalking.oap.server.core.config.K8sMetadataReadSettings;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class K8sInfoRegistryTest {
    private static final long READ_MILLIS = 700;

    private KubernetesPods originalPods;
    private KubernetesServices originalServices;
    private KubernetesPods pods;
    private KubernetesServices services;
    /**
     * Unique per test, as the registry is a singleton caching what earlier tests read.
     */
    private final String prefix = "pod-" + UUID.randomUUID() + "-";
    private final List<ObjectID> keys = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        originalPods = KubernetesPods.INSTANCE;
        originalServices = KubernetesServices.INSTANCE;
        pods = Mockito.mock(KubernetesPods.class);
        services = Mockito.mock(KubernetesServices.class);
        ReflectUtil.setInternalState(KubernetesPods.class, "INSTANCE", pods);
        ReflectUtil.setInternalState(KubernetesServices.class, "INSTANCE", services);
        when(pods.findByObjectID(any())).thenAnswer(invocation -> {
            TimeUnit.MILLISECONDS.sleep(READ_MILLIS);
            final ObjectID id = invocation.getArgument(0);
            return Optional.of(webPod(id.name()));
        });
        when(services.list()).thenReturn(Collections.singletonList(webService()));
    }

    @AfterEach
    public void tearDown() throws Exception {
        // The reads run on the shared background lanes: they must use the mocks, so they are waited for first.
        for (final ObjectID key : keys) {
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (K8sInfoRegistry.getInstance().findService(key.namespace(), key.name()) == ObjectID.EMPTY
                && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(50);
            }
        }
        ReflectUtil.setInternalState(KubernetesPods.class, "INSTANCE", originalPods);
        ReflectUtil.setInternalState(KubernetesServices.class, "INSTANCE", originalServices);
    }

    @Test
    public void aStepWaitsForAllItsPodsWithinOneTimeout() {
        for (int i = 0; i < 5; i++) {
            keys.add(ObjectID.builder().namespace("default").name(prefix + i).build());
        }

        final long start = System.nanoTime();
        K8sInfoRegistry.getInstance().readServicesOfPods(keys);
        final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // Five reads of 700 ms one after another take 3.5 s; the step waits the 2 s timeout at most.
        final long timeout = K8sMetadataReadSettings.getMalReadTimeoutMillis();
        assertTrue(elapsedMillis >= timeout - 100 && elapsedMillis < timeout + 500, "waited " + elapsedMillis + " ms");
        assertEquals("web.default", K8sInfoRegistry.getInstance().findServiceName("default", prefix + 0));
    }

    @Test
    public void aStepDoesNotWaitAgainForTheReadsAnEarlierStepWaitedForTheTimeout() {
        for (int i = 0; i < 5; i++) {
            keys.add(ObjectID.builder().namespace("default").name(prefix + i).build());
        }
        K8sInfoRegistry.getInstance().readServicesOfPods(keys);

        // Such as the next retagByK8sMeta of the same Export, over the same pods whose reads are still running.
        assertTimeoutPreemptively(
            Duration.ofMillis(READ_MILLIS / 2), () -> K8sInfoRegistry.getInstance().readServicesOfPods(keys));
    }

    @Test
    public void stepsOverTheSamePodsWaitTheTimeoutAtMostInAllWhileALimitedQueueDrains() {
        K8sMetadataReadSettings.set(2000, "mal=1", "als=21,mal=21", 3, 1);
        try {
            for (int i = 0; i < 6; i++) {
                keys.add(ObjectID.builder().namespace("default").name(prefix + i).build());
            }
            final long start = System.nanoTime();
            for (int step = 0; step < 4; step++) {
                // Such as the four retagByK8sMeta of one Export, each admitting reads refused before as the lane drains.
                K8sInfoRegistry.getInstance().readServicesOfPods(keys);
            }
            final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertTrue(elapsedMillis < 2000 + 500, "waited " + elapsedMillis + " ms");
        } finally {
            K8sMetadataReadSettings.set(2000, "", "als=21,mal=21", 3, 1);
        }
    }

    @Test
    public void theLookupsDoNotWait() {
        keys.add(ObjectID.builder().namespace("default").name(prefix + 0).build());

        assertTimeoutPreemptively(Duration.ofMillis(READ_MILLIS / 2), () -> assertEquals(
            ObjectID.EMPTY, K8sInfoRegistry.getInstance().findService("default", prefix + 0)));
    }

    @Test
    public void aPodNoServiceSelectsInTheServicesReadIsMatchedInTheServicesReadAgain() {
        when(services.list()).thenReturn(Collections.emptyList());
        when(services.list(K8sMetadataReadSettings.getNotFoundRetryPeriod()))
            .thenReturn(Collections.singletonList(webService()));
        keys.add(ObjectID.builder().namespace("default").name(prefix + 0).build());

        K8sInfoRegistry.getInstance().readServicesOfPods(keys);

        assertEquals("web.default", K8sInfoRegistry.getInstance().findServiceName("default", prefix + 0));
    }

    private static Pod webPod(final String name) {
        return new PodBuilder()
            .withNewMetadata().withNamespace("default").withName(name).addToLabels("app", "web").endMetadata()
            .build();
    }

    private static Service webService() {
        return new ServiceBuilder()
            .withNewMetadata().withNamespace("default").withName("web").endMetadata()
            .withNewSpec().addToSelector("app", "web").endSpec()
            .build();
    }
}
