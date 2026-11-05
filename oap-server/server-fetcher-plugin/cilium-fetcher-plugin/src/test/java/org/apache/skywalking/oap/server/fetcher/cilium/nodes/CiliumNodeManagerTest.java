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

package org.apache.skywalking.oap.server.fetcher.cilium.nodes;

import io.cilium.api.observer.ObserverGrpc;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.fetcher.cilium.CiliumFetcherConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class CiliumNodeManagerTest {

    @Mock
    private ModuleManager moduleManager;
    private CiliumNodeManager ciliumNodeManager;
    private NodeUpdateListener nodeUpdateListener;

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "none-node-cluster-with-cilium-nodes",
                Collections.emptyList(),
                Arrays.asList(ciliumNode("c1")),
                Collections.emptyList(),
            },
            {
                "single-node-cluster-with-single-nodes",
                Arrays.asList(oapInstance("a1", true)),
                Arrays.asList(ciliumNode("c1")),
                Arrays.asList(ciliumNode("c1"))
            },
            {
                "single-node-cluster-with-multiple-nodes",
                Arrays.asList(oapInstance("a1", true)),
                Arrays.asList(ciliumNode("c1"), ciliumNode("c2")),
                Arrays.asList(ciliumNode("c1"), ciliumNode("c2"))
            },
            {
                "multiple-node-cluster-with-single-nodes",
                Arrays.asList(oapInstance("a1", true), oapInstance("a2", false)),
                Arrays.asList(ciliumNode("c1")),
                Arrays.asList(ciliumNode("c1"))
            },
            {
                "multiple-node-cluster-with-single-nodes-2",
                Arrays.asList(oapInstance("a1", false), oapInstance("a2", true)),
                Arrays.asList(ciliumNode("c1")),
                Collections.emptyList()
            },
            {
                "multiple-node-cluster-with-multiple-nodes",
                Arrays.asList(oapInstance("a1", true), oapInstance("a2", false)),
                Arrays.asList(ciliumNode("c1"), ciliumNode("c2")),
                Arrays.asList(ciliumNode("c1"))
            },
            {
                "multiple-node-cluster-with-multiple-nodes-2",
                Arrays.asList(oapInstance("a1", true), oapInstance("a2", false)),
                Arrays.asList(ciliumNode("c1"), ciliumNode("c2"), ciliumNode("c3")),
                Arrays.asList(ciliumNode("c1"))
            },
            {
                "multiple-node-cluster-with-multiple-nodes-3",
                Arrays.asList(oapInstance("a1", false), oapInstance("a2", true)),
                Arrays.asList(ciliumNode("c1"), ciliumNode("c2"), ciliumNode("c3")),
                Arrays.asList(ciliumNode("c2"), ciliumNode("c3"))
            },
        });
    }

    @BeforeEach
    public void prepare() {
        ciliumNodeManager = new CiliumNodeManager(moduleManager, new NoopClientBuilder(), new CiliumFetcherConfig());
        nodeUpdateListener = new NodeUpdateListener();
        ciliumNodeManager.addListener(nodeUpdateListener);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void test(String name,
                     List<RemoteInstance> allOAPInstances,
                     List<CiliumNode> allCiliumNodes,
                     List<CiliumNode> shouldMonitorNodeBySelf) {
        ReflectUtil.setInternalState(ciliumNodeManager, "remoteInstances", allOAPInstances);
        ReflectUtil.setInternalState(ciliumNodeManager, "allNodes", allCiliumNodes);
        ciliumNodeManager.refreshUsingNodes();
        final List<CiliumNode> nodes = nodeUpdateListener.getNodes();
        nodes.sort(Comparator.comparing(CiliumNode::getAddress));
        assertThat(nodes).isEqualTo(shouldMonitorNodeBySelf);
    }

    /**
     * Empty cluster updates must clear using-nodes without racing a mid-rebuild
     * {@code findFirst().get()} on self when {@code remoteInstances} was published unlocked.
     */
    @Test
    public void shouldSkipUsingNodesWhenClusterBecomesEmpty() {
        ReflectUtil.setInternalState(ciliumNodeManager, "remoteInstances",
            Arrays.asList(oapInstance("a1", true)));
        ReflectUtil.setInternalState(ciliumNodeManager, "allNodes",
            new ArrayList<>(Arrays.asList(ciliumNode("c1"), ciliumNode("c2"))));
        ciliumNodeManager.refreshUsingNodes();
        assertThat(nodeUpdateListener.getNodes()).isNotEmpty();

        assertDoesNotThrow(() -> ciliumNodeManager.onClusterNodesChanged(Collections.emptyList()));
        assertThat(nodeUpdateListener.getNodes()).isEmpty();
        assertThat(ReflectUtil.<List<CiliumNode>>getInternalState(ciliumNodeManager, "usingNodes"))
            .isEmpty();
    }

    /**
     * A non-empty backend list with no {@code isSelf} instance must not throw on
     * {@code findFirst().get()}; using-nodes stay empty.
     */
    @Test
    public void shouldSkipUsingNodesWhenRemoteInstancesHaveNoSelf() {
        ReflectUtil.setInternalState(ciliumNodeManager, "remoteInstances",
            Arrays.asList(oapInstance("a1", false), oapInstance("a2", false)));
        ReflectUtil.setInternalState(ciliumNodeManager, "allNodes",
            new ArrayList<>(Arrays.asList(ciliumNode("c1"), ciliumNode("c2"))));

        assertDoesNotThrow(() -> ciliumNodeManager.refreshUsingNodes());
        assertThat(nodeUpdateListener.getNodes()).isEmpty();
        assertThat(ReflectUtil.<List<CiliumNode>>getInternalState(ciliumNodeManager, "usingNodes"))
            .isEmpty();
    }

    /**
     * Closing using-nodes when self disappears must not leave closed instances in
     * {@code allNodes}; when self returns, reassignment uses a fresh open node that
     * can still build a non-null Observer stub.
     */
    @Test
    public void shouldAssignFreshNodesAfterSelfDisappearsAndReturns() {
        final ObserverGrpc.ObserverBlockingStub stub = mock(ObserverGrpc.ObserverBlockingStub.class);
        final ClientBuilder builder = new ClientBuilder() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T buildClient(String host, int port, Class<T> stubClass) {
                if (ObserverGrpc.ObserverBlockingStub.class.equals(stubClass)) {
                    return (T) stub;
                }
                return null;
            }
        };
        ciliumNodeManager = new CiliumNodeManager(moduleManager, builder, new CiliumFetcherConfig());
        nodeUpdateListener = new NodeUpdateListener();
        ciliumNodeManager.addListener(nodeUpdateListener);

        final CiliumNode original = new CiliumNode("c1", builder);
        ReflectUtil.setInternalState(ciliumNodeManager, "remoteInstances",
            Arrays.asList(oapInstance("a1", true)));
        ReflectUtil.setInternalState(ciliumNodeManager, "allNodes",
            new ArrayList<>(Collections.singletonList(original)));
        ciliumNodeManager.refreshUsingNodes();
        assertThat(nodeUpdateListener.getNodes()).containsExactly(original);

        ciliumNodeManager.onClusterNodesChanged(
            Arrays.asList(oapInstance("a1", false), oapInstance("a2", false)));
        assertThat(nodeUpdateListener.getNodes()).isEmpty();
        assertThat(original.isClosed()).isTrue();
        assertNull(original.getObserverStub());

        ciliumNodeManager.onClusterNodesChanged(Arrays.asList(oapInstance("a1", true)));
        final List<CiliumNode> usingNodes =
            ReflectUtil.getInternalState(ciliumNodeManager, "usingNodes");
        assertThat(usingNodes).hasSize(1);
        assertThat(usingNodes.get(0).getAddress()).isEqualTo("c1");
        assertThat(usingNodes.get(0).isClosed()).isFalse();
        assertThat(usingNodes.get(0)).isNotSameAs(original);
        assertThat(usingNodes.get(0).getObserverStub()).isSameAs(stub);
        assertThat(nodeUpdateListener.getNodes()).containsExactly(usingNodes.get(0));
    }

    /**
     * Scaling the OAP cluster out and back in moves a node away and back while self stays
     * listed; the node handed back must be a fresh, open instance.
     */
    @Test
    public void shouldReassignFreshNodeAfterScalingOutAndBackIn() {
        final CiliumNode c2 = ciliumNode("c2");
        ReflectUtil.setInternalState(ciliumNodeManager, "remoteInstances",
            Arrays.asList(oapInstance("a1", true)));
        ReflectUtil.setInternalState(ciliumNodeManager, "allNodes",
            new ArrayList<>(Arrays.asList(ciliumNode("c1"), c2)));
        ciliumNodeManager.refreshUsingNodes();
        assertEquals(Arrays.asList("c1", "c2"), usingAddresses());

        ciliumNodeManager.onClusterNodesChanged(Arrays.asList(oapInstance("a1", true), oapInstance("a2", false)));
        assertEquals(Collections.singletonList("c1"), usingAddresses());
        assertTrue(c2.isClosed());

        ciliumNodeManager.onClusterNodesChanged(Arrays.asList(oapInstance("a1", true)));
        assertEquals(Arrays.asList("c1", "c2"), usingAddresses());
        final CiliumNode reassigned = ReflectUtil.<List<CiliumNode>>getInternalState(
            ciliumNodeManager, "usingNodes").get(1);
        assertNotSame(c2, reassigned);
        assertFalse(reassigned.isClosed());
    }

    /**
     * A peer deleted while in use is closed and stays gone; the close path must not put a
     * fresh instance of it back into {@code allNodes}.
     */
    @Test
    public void shouldCloseDeletedPeerInUseWithoutRestoringIt() throws Exception {
        final CiliumNode c2 = ciliumNode("c2");
        ReflectUtil.setInternalState(ciliumNodeManager, "remoteInstances",
            Arrays.asList(oapInstance("a1", true)));
        ReflectUtil.setInternalState(ciliumNodeManager, "allNodes",
            new ArrayList<>(Arrays.asList(ciliumNode("c1"), c2)));
        ciliumNodeManager.refreshUsingNodes();

        ReflectUtil.invokeMethod(ciliumNodeManager, "removeNode", ciliumNode("c2"));

        assertEquals(Collections.singletonList("c1"), usingAddresses());
        assertTrue(c2.isClosed());
        assertEquals(Collections.singletonList("c1"),
            addresses(ReflectUtil.getInternalState(ciliumNodeManager, "allNodes")));
    }

    /**
     * A same-address peer update preserves the assigned open node and the listener's
     * final membership.
     */
    @Test
    public void shouldKeepOpenInstanceWhenPeerUpdatedInUse() throws Exception {
        final CiliumNode original = ciliumNode("c1");
        ReflectUtil.setInternalState(ciliumNodeManager, "remoteInstances",
            Arrays.asList(oapInstance("a1", true)));
        ReflectUtil.setInternalState(ciliumNodeManager, "allNodes",
            new ArrayList<>(Collections.singletonList(original)));
        ciliumNodeManager.refreshUsingNodes();

        ReflectUtil.invokeMethod(ciliumNodeManager, "addOrUpdateNode", ciliumNode("c1"));

        final List<CiliumNode> usingNodes = ReflectUtil.getInternalState(ciliumNodeManager, "usingNodes");
        assertEquals(1, usingNodes.size());
        assertSame(original, usingNodes.get(0));
        assertFalse(original.isClosed());
        assertEquals(1, nodeUpdateListener.getNodes().size());
        assertSame(original, nodeUpdateListener.getNodes().get(0));
    }

    private List<String> usingAddresses() {
        return addresses(ReflectUtil.getInternalState(ciliumNodeManager, "usingNodes"));
    }

    private static List<String> addresses(List<CiliumNode> nodes) {
        return nodes.stream().map(CiliumNode::getAddress).collect(Collectors.toList());
    }

    /**
     * With one monitor covering notify, a blocking Create holds {@code nodeLock} so a
     * concurrent Close waits and events stay ordered.
     */
    @Test
    public void shouldNotifyCloseAfterCreateUnderSingleLock() throws Exception {
        final List<String> order = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch createStarted = new CountDownLatch(1);
        final CountDownLatch allowCreateFinish = new CountDownLatch(1);
        ReflectUtil.setInternalState(ciliumNodeManager, "listeners", new CopyOnWriteArrayList<>(
            Collections.singletonList(new CiliumNodeUpdateListener() {
                @Override
                public void onNodeAdded(CiliumNode node) {
                    order.add("create:" + node.getAddress());
                    createStarted.countDown();
                    try {
                        assertThat(allowCreateFinish.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onNodeDelete(CiliumNode node) {
                    order.add("close:" + node.getAddress());
                }
            })));

        ReflectUtil.setInternalState(ciliumNodeManager, "remoteInstances",
            Arrays.asList(oapInstance("a1", true)));
        ReflectUtil.setInternalState(ciliumNodeManager, "allNodes",
            new ArrayList<>(Collections.singletonList(ciliumNode("c1"))));

        final Thread assigner = new Thread(ciliumNodeManager::refreshUsingNodes, "cilium-assign");
        final Thread closer = new Thread(
            () -> ciliumNodeManager.onClusterNodesChanged(Collections.emptyList()), "cilium-close");
        try {
            assigner.start();
            assertThat(createStarted.await(5, TimeUnit.SECONDS)).isTrue();
            closer.start();
            // Closer blocks on nodeLock until Create finishes notify under that lock.
            awaitBlocked(closer);
        } finally {
            allowCreateFinish.countDown();
        }
        assigner.join(5000);
        closer.join(5000);
        assertFalse(assigner.isAlive());
        assertFalse(closer.isAlive());

        assertThat(order).containsExactly("create:c1", "close:c1");
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED) {
            assertTrue(thread.isAlive(), () -> thread.getName() + " finished without waiting for the lock");
            assertTrue(System.nanoTime() - deadline < 0, () -> thread.getName() + " never blocked on the lock");
            Thread.sleep(1);
        }
    }

    /**
     * Peer add/remove, cluster watcher and refresh share {@code allNodes}; without one lock
     * this used to throw {@link java.util.ConcurrentModificationException} or mis-shard.
     */
    @Test
    public void shouldTolerateConcurrentNodeAndClusterUpdates() throws Exception {
        ReflectUtil.setInternalState(ciliumNodeManager, "remoteInstances",
            Arrays.asList(oapInstance("a1", true), oapInstance("a2", false)));
        ReflectUtil.setInternalState(ciliumNodeManager, "allNodes", new ArrayList<>());

        final int rounds = 200;
        final ExecutorService pool = Executors.newFixedThreadPool(3);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        try {
            pool.execute(() -> runUntilDone(start, failure, rounds, i -> {
                ReflectUtil.invokeMethod(ciliumNodeManager, "addOrUpdateNode", ciliumNode("c" + (i % 8)));
            }));
            pool.execute(() -> runUntilDone(start, failure, rounds, i -> {
                ReflectUtil.invokeMethod(ciliumNodeManager, "removeNode", ciliumNode("c" + ((i + 3) % 8)));
            }));
            pool.execute(() -> runUntilDone(start, failure, rounds, i -> {
                if (i % 2 == 0) {
                    ciliumNodeManager.onClusterNodesChanged(
                        Arrays.asList(oapInstance("a1", true), oapInstance("a2", false)));
                } else {
                    ciliumNodeManager.onClusterNodesChanged(Collections.emptyList());
                }
                ciliumNodeManager.refreshUsingNodes();
            }));

            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertNull(failure.get(), () -> "concurrent update failed: " + failure.get());

        final List<CiliumNode> usingNodes =
            ReflectUtil.getInternalState(ciliumNodeManager, "usingNodes");
        assertThat(usingNodes).allMatch(node -> !node.isClosed());
        assertThat(nodeUpdateListener.getNodes())
            .containsExactlyInAnyOrderElementsOf(usingNodes);
    }

    private static void runUntilDone(CountDownLatch start,
                                     AtomicReference<Throwable> failure,
                                     int rounds,
                                     RoundAction action) {
        try {
            start.await();
            for (int i = 0; i < rounds; i++) {
                if (failure.get() != null) {
                    return;
                }
                action.run(i);
            }
        } catch (Throwable t) {
            failure.compareAndSet(null, t);
        }
    }

    @FunctionalInterface
    private interface RoundAction {
        void run(int round) throws Exception;
    }

    private static CiliumNode ciliumNode(String address) {
        return new CiliumNode(address, null);
    }

    private static RemoteInstance oapInstance(String address, boolean self) {
        return new RemoteInstance(new Address(address, 0, self));
    }

    @Getter
    private static class NodeUpdateListener implements CiliumNodeUpdateListener {
        private List<CiliumNode> nodes;

        public NodeUpdateListener() {
            this.nodes = new ArrayList<>();
        }

        @Override
        public void onNodeAdded(CiliumNode node) {
            this.nodes.add(node);
        }

        @Override
        public void onNodeDelete(CiliumNode node) {
            this.nodes.remove(node);
        }
    }

    private static class NoopClientBuilder implements ClientBuilder {

        @Override
        public <T> T buildClient(String host, int port, Class<T> stubClass) {
            return null;
        }
    }
}
