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
 *
 */

package org.apache.skywalking.oap.server.cluster.plugin.kubernetes;

import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.endpoint.DynamicEndpointGroup;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Tests for {@link KubernetesCoordinator} listener behavior, focusing on the
 * self-endpoint race condition during K8s informer startup.
 *
 * <h3>Workflow: Self-endpoint race condition during OAP pod startup</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │                   OAP Pod Startup Sequence                              │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │                                                                         │
 * │  1. Constructor: .inform() starts K8s informer (async)                  │
 * │  2. Constructor: updateEndpoints() called immediately                   │
 * │                                                                         │
 * │  ┌──────────────── Informer Cache State ───────────────────┐            │
 * │  │  Only remote pod cached (self pod NOT synced yet)       │            │
 * │  └────────────────────────────────────────────────────────-┘            │
 * │                           │                                             │
 * │                           ▼                                             │
 * │  ┌──────────────── updateEndpoints() ──────────────────────┐            │
 * │  │  endpoints = [remote_pod_ip]                            │            │
 * │  │  selfEndpoint = null  (self not in informer yet)        │            │
 * │  └────────────────────────┬────────────────────────────────┘            │
 * │                           │                                             │
 * │                           ▼                                             │
 * │  ┌──────────────── Coordinator Listener ───────────────────┐            │
 * │  │  getSelfEndpoint() == null                              │            │
 * │  │  Fallback: add 127.0.0.1 as self                       │            │
 * │  │  result = [remote_pod(self=false), 127.0.0.1(self=true)]│            │
 * │  │                                                         │            │
 * │  │  *** Tested by: shouldFallbackTo127WhenSelfNotSynced ** │            │
 * │  └────────────────────────┬────────────────────────────────┘            │
 * │                           │                                             │
 * │  ═══════════ Time passes, informer syncs self pod ══════════            │
 * │                           │                                             │
 * │                           ▼                                             │
 * │  ┌──────────────── updateEndpoints() (2nd call) ───────────┐            │
 * │  │  selfEndpoint = real_pod_ip  (now set correctly)        │            │
 * │  │  endpoints = [remote_pod_ip, self_pod_ip]               │            │
 * │  │  List CHANGED (1 to 2 entries) → listener fires again   │            │
 * │  └────────────────────────┬────────────────────────────────┘            │
 * │                           │                                             │
 * │                           ▼                                             │
 * │  ┌──────────────── Coordinator Listener (2nd fire) ────────┐            │
 * │  │  getSelfEndpoint() == real_pod_ip                       │            │
 * │  │  Match self in endpoint list, mark isSelf=true          │            │
 * │  │  result = [remote_pod(self=false), self_pod(self=true)] │            │
 * │  │  127.0.0.1 fallback is gone                             │            │
 * │  │                                                         │            │
 * │  │  *** Tested by: shouldResolveSelfAfterInformerSync ***  │            │
 * │  └────────────────────────┬────────────────────────────────┘            │
 * │                           │                                             │
 * │                           ▼                                             │
 * │  ┌──────────────── TTL Leader Election ────────────────────┐            │
 * │  │  sort([10.x.x.x, 10.x.x.y]) → deterministic ordering  │            │
 * │  │  Exactly one node is self → stable leader selection     │            │
 * │  │                                                         │            │
 * │  │  *** Tested by: shouldElectTTLLeaderCorrectlyWithRealIPs│            │
 * │  └────────────────────────────────────────────────────────-┘            │
 * │                                                                         │
 * └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
public class KubernetesCoordinatorTest {

    private static final int GRPC_PORT = 11800;
    private static final String REMOTE_POD_IP = "10.116.2.203";
    private static final String SELF_POD_IP = "10.116.2.100";

    @Mock
    private HealthCheckMetrics healthChecker;

    private KubernetesCoordinator coordinator;
    private TestEndpointGroup testEndpointGroup;

    @BeforeEach
    public void setUp() throws Exception {
        final var config = new ClusterModuleKubernetesConfig();
        config.setLabelSelector("app=oap");

        testEndpointGroup = new TestEndpointGroup();

        coordinator = spy(new KubernetesCoordinator(null, config));
        ReflectUtil.setInternalState(coordinator, "port", GRPC_PORT);
        ReflectUtil.setInternalState(coordinator, "healthChecker", healthChecker);
        doReturn(testEndpointGroup).when(coordinator).createEndpointGroup();

        coordinator.start();
    }

    /**
     * Simulates the initial race window: informer has NOT synced the self pod yet.
     * Only the remote pod is in the endpoint list, and {@code getSelfEndpoint()} returns null.
     * The coordinator should fall back to 127.0.0.1 for self.
     *
     * <p>See "Coordinator Listener" (1st fire) in the class-level workflow diagram.
     */
    @Test
    public void shouldFallbackTo127WhenSelfNotSynced() {
        testEndpointGroup.fireEndpoints(
            List.of(Endpoint.of(REMOTE_POD_IP, GRPC_PORT)));

        final List<RemoteInstance> instances = coordinator.queryRemoteNodes();
        assertEquals(2, instances.size());

        final RemoteInstance remote = findByHost(instances, REMOTE_POD_IP);
        assertFalse(remote.getAddress().isSelf());

        final RemoteInstance self = findByHost(instances, "127.0.0.1");
        assertTrue(self.getAddress().isSelf());
    }

    /**
     * Simulates the full race-then-recovery sequence: after the informer syncs
     * the self pod, the endpoint list grows from 1 to 2 entries, so
     * {@link DynamicEndpointGroup} fires the listener again. This time
     * {@code getSelfEndpoint()} returns the real IP and self is correctly identified.
     *
     * <p>Previously, self was excluded from the endpoint list, so the list
     * didn't change when self appeared. {@link DynamicEndpointGroup} deduplicated
     * and never re-fired the listener, leaving self stuck at 127.0.0.1.
     *
     * <p>See "Coordinator Listener" (1st + 2nd fire) in the class-level workflow diagram.
     */
    @Test
    public void shouldResolveSelfAfterInformerSync() {
        // Phase 1: informer hasn't synced self yet
        testEndpointGroup.fireEndpoints(
            List.of(Endpoint.of(REMOTE_POD_IP, GRPC_PORT)));

        List<RemoteInstance> instances = coordinator.queryRemoteNodes();
        assertEquals(2, instances.size());
        assertTrue(findByHost(instances, "127.0.0.1").getAddress().isSelf(),
            "Before sync, self should be 127.0.0.1 fallback");

        // Phase 2: informer syncs self pod — endpoint list now includes both pods
        final Endpoint selfEndpoint = Endpoint.of(SELF_POD_IP, GRPC_PORT);
        testEndpointGroup.setSelfEndpoint(selfEndpoint);
        testEndpointGroup.fireEndpoints(List.of(
            Endpoint.of(REMOTE_POD_IP, GRPC_PORT),
            selfEndpoint
        ));

        instances = coordinator.queryRemoteNodes();
        assertEquals(2, instances.size());

        final RemoteInstance self = findByHost(instances, SELF_POD_IP);
        assertTrue(self.getAddress().isSelf(),
            "After sync, self should use real pod IP " + SELF_POD_IP);

        final RemoteInstance remote = findByHost(instances, REMOTE_POD_IP);
        assertFalse(remote.getAddress().isSelf());

        assertTrue(instances.stream().noneMatch(
            i -> i.getAddress().getHost().equals("127.0.0.1")),
            "127.0.0.1 fallback should be gone after self is resolved");
    }

    /**
     * Verifies that TTL leader election works correctly after self is resolved.
     * The sorted instance list should have deterministic ordering with real IPs,
     * and exactly one node should be self.
     *
     * <p>See "TTL Leader Election" in the class-level workflow diagram.
     */
    @Test
    public void shouldElectTTLLeaderCorrectlyWithRealIPs() {
        final Endpoint selfEndpoint = Endpoint.of(SELF_POD_IP, GRPC_PORT);
        testEndpointGroup.setSelfEndpoint(selfEndpoint);

        testEndpointGroup.fireEndpoints(List.of(
            Endpoint.of(REMOTE_POD_IP, GRPC_PORT),
            selfEndpoint
        ));

        final List<RemoteInstance> instances = coordinator.queryRemoteNodes();
        Collections.sort(instances);

        assertEquals(2, instances.size());

        final long selfCount = instances.stream()
            .filter(i -> i.getAddress().isSelf())
            .count();
        assertEquals(1, selfCount, "Exactly one instance should be self");

        final RemoteInstance selfInstance = instances.stream()
            .filter(i -> i.getAddress().isSelf())
            .findFirst()
            .orElseThrow();
        assertEquals(SELF_POD_IP, selfInstance.getAddress().getHost());
    }

    private RemoteInstance findByHost(List<RemoteInstance> instances, String host) {
        return instances.stream()
            .filter(i -> i.getAddress().getHost().equals(host))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "No instance with host: " + host + ", found: " + instances));
    }

    /**
     * A controllable endpoint group for testing. Implements {@link SelfEndpointAccessor}
     * so the coordinator can resolve self endpoint without requiring a real K8s client.
     *
     * <p>Simulates the K8s informer behavior:
     * <ul>
     *   <li>Initially selfEndpoint is null (informer hasn't synced self pod yet)</li>
     *   <li>After calling {@link #setSelfEndpoint}, the next {@link #fireEndpoints} call
     *       simulates the informer adding the self pod to its cache</li>
     * </ul>
     */
    static class TestEndpointGroup extends DynamicEndpointGroup implements SelfEndpointAccessor {
        private volatile Endpoint selfEndpoint;

        @Override
        public Endpoint getSelfEndpoint() {
            return selfEndpoint;
        }

        void setSelfEndpoint(Endpoint selfEndpoint) {
            this.selfEndpoint = selfEndpoint;
        }

        void fireEndpoints(List<Endpoint> endpoints) {
            setEndpoints(endpoints);
        }

        @Override
        protected void doCloseAsync(CompletableFuture<?> future) {
            future.complete(null);
        }
    }
}
