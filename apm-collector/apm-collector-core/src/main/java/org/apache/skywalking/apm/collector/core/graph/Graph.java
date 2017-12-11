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


package org.apache.skywalking.apm.collector.core.graph;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peng-yongsheng, wu-sheng
 */
public final class Graph<INPUT> {
    private int id;
    private WayToNode entryWay;
    private ConcurrentHashMap<Integer, Node> nodeIndex = new ConcurrentHashMap<>();

    Graph(int id) {
        this.id = id;
    }

    public void start(INPUT input) {
        entryWay.in(input);
    }

    public <OUTPUT> Node<INPUT, OUTPUT> addNode(NodeProcessor<INPUT, OUTPUT> nodeProcessor) {
        return addNode(new DirectWay(nodeProcessor));
    }

    public <OUTPUT> Node<INPUT, OUTPUT> addNode(WayToNode<INPUT, OUTPUT> entryWay) {
        synchronized (this) {
            this.entryWay = entryWay;
            this.entryWay.buildDestination(this);
            return entryWay.getDestination();
        }
    }

    void checkForNewNode(Node node) {
        int nodeId = node.getHandler().id();
        if (nodeIndex.containsKey(nodeId)) {
            throw new PotentialCyclicGraphException("handler="
                + node.getHandler().getClass().getName()
                + " already exists in graph[" + id + "]");
        }
        nodeIndex.put(nodeId, node);
    }

    public GraphNodeFinder toFinder() {
        return new GraphNodeFinder(this);
    }

    ConcurrentHashMap<Integer, Node> getNodeIndex() {
        return nodeIndex;
    }

    int getId() {
        return id;
    }
}
