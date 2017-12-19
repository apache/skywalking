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

import java.util.HashMap;
import java.util.Map;

/**
 * @author wusheng
 */
public enum GraphManager {
    INSTANCE;

    private Map<Integer, Graph> allGraphs = new HashMap<>();

    /**
     * Create a stream process graph.
     *
     * @param graphId represents a graph, which is used for finding it.
     * @return
     */
    public synchronized <INPUT> Graph<INPUT> createIfAbsent(int graphId, Class<INPUT> input) {
        if (!allGraphs.containsKey(graphId)) {
            Graph graph = new Graph(graphId);
            allGraphs.put(graphId, graph);
            return graph;
        } else {
            return allGraphs.get(graphId);
        }
    }

    public Graph findGraph(int graphId) {
        Graph graph = allGraphs.get(graphId);
        if (graph == null) {
            throw new GraphNotFoundException("Graph id=" + graphId + " not found in this GraphManager");
        }
        return graph;
    }

    public <INPUT> Graph<INPUT> findGraph(int graphId, Class<INPUT> input) {
        return findGraph(graphId);
    }

    public void reset() {
        allGraphs.clear();
    }
}
