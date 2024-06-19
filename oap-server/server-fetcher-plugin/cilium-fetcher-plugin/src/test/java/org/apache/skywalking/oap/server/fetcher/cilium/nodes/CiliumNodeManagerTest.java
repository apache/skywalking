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

import lombok.Getter;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.fetcher.cilium.CiliumFetcherConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        Whitebox.setInternalState(ciliumNodeManager, "remoteInstances", allOAPInstances);
        Whitebox.setInternalState(ciliumNodeManager, "allNodes", allCiliumNodes);
        ciliumNodeManager.refreshUsingNodes();
        final List<CiliumNode> nodes = nodeUpdateListener.getNodes();
        nodes.sort(Comparator.comparing(CiliumNode::getAddress));
        assertThat(nodes).isEqualTo(shouldMonitorNodeBySelf);
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
