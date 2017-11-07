/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.core.graph;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peng-yongsheng, wu-sheng
 */
public final class Graph<INPUT> {
    private int id;
    private Node startNode;
    private ConcurrentHashMap<Integer, Node> nodeIndex = new ConcurrentHashMap<>();

    Graph(int id) {
        this.id = id;
    }

    public void start(INPUT INPUT) {
        startNode.execute(INPUT);
    }

    public <OUTPUT> Node<INPUT, OUTPUT> addNode(NodeProcessor<INPUT, OUTPUT> nodeProcessor) {
        synchronized (this) {
            startNode = new Node(this, nodeProcessor);
            return startNode;
        }
    }

    public Next findNext(int handlerId) {
        Node node = nodeIndex.get(handlerId);
        if (node == null) {
            throw new NodeNotFoundException("Can't find node with handlerId="
                + handlerId
                + " in graph[" + id + "]");
        }
        return node.getNext();
    }

    void checkForNewNode(Node node) {
        int nodeId = node.getHandler().id();
        if (nodeIndex.containsKey(nodeId)) {
            throw new PotentialAcyclicGraphException("handler="
                + node.getHandler().getClass().getName()
                + " already exists in graph[" + id + "】");
        }
        nodeIndex.put(nodeId, node);
    }

    public GraphBuilder toBuilder(){
        return new GraphBuilder(this);
    }

    ConcurrentHashMap<Integer, Node> getNodeIndex() {
        return nodeIndex;
    }
}
