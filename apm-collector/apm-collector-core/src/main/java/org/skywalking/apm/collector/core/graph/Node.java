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

/**
 * The <code>Node</code> in the graph with explicit Input and Output types.
 *
 * @author peng-yongsheng, wu-sheng
 */
public final class Node<Input, Output> {
    private final NodeHandler nodeHandler;
    private final Next<Output> next;
    private final Graph graph;

    Node(Graph graph, NodeHandler<Input, Output> nodeHandler) {
        this.graph = graph;
        this.nodeHandler = nodeHandler;
        this.next = new Next<>();
        this.graph.checkForNewNode(this);
    }

    public final <NextOutput> Node<Output, NextOutput> addNext(NodeHandler<Output, NextOutput> nodeHandler) {
        synchronized (graph) {
            Node<Output, NextOutput> node = new Node<>(graph, nodeHandler);
            next.addNext(node);
            return node;
        }
    }

    final void execute(Input input) {
        nodeHandler.process(input, next);
    }

    NodeHandler getHandler() {
        return nodeHandler;
    }

    Next<Output> getNext() {
        return next;
    }
}
