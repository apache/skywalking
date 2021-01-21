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

package org.apache.skywalking.e2e.topo;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.assertor.VariableExpressParser;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.fail;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TopoMatcher extends AbstractMatcher<Topology> {

    private List<NodeMatcher> nodes;
    private List<CallMatcher> calls;

    @Override
    public void verify(final Topology topology) {
        if (nonNull(getNodes())) {
            verifyNodes(topology);
        }

        if (nonNull(getCalls())) {
            convertNodeId(getCalls(), topology.getNodes());
            verifyCalls(topology);
        }
    }

    private void verifyNodes(Topology topology) {
        for (int i = 0; i < getNodes().size(); i++) {
            boolean matched = false;
            for (int j = 0; j < topology.getNodes().size(); j++) {
                try {
                    getNodes().get(i).verify(topology.getNodes().get(j));
                    matched = true;
                } catch (Throwable ignore) {
                }
            }
            if (!matched) {
                fail("\nExpected: %s\nActual: %s", getNodes(), topology.getNodes());
            }
        }
    }

    private void verifyCalls(Topology topology) {
        for (int i = 0; i < getCalls().size(); i++) {
            boolean matched = false;
            for (int j = 0; j < topology.getCalls().size(); j++) {
                try {
                    getCalls().get(i).verify(topology.getCalls().get(j));
                    matched = true;
                } catch (Throwable ignore) {
                }
            }
            if (!matched) {
                fail("\nExpected: %s\nActual: %s", getCalls(), topology.getCalls());
            }
        }
    }

    private static void convertNodeId(List<CallMatcher> callMatchers, List<Node> nodes) {
        for (CallMatcher callMatcher : callMatchers) {
            Node sourceNode = VariableExpressParser.parse(callMatcher.getSource(), nodes, Node::getName);
            Node targetNode = VariableExpressParser.parse(callMatcher.getTarget(), nodes, Node::getName);

            boolean convert = false;
            if (nonNull(sourceNode)) {
                callMatcher.setSource(sourceNode.getId());
                convert = true;
            }
            if (nonNull(targetNode)) {
                callMatcher.setTarget(targetNode.getId());
                convert = true;
            }

            if (convert) {
                callMatcher.setId(String.join("-", callMatcher.getSource(), callMatcher.getTarget()));
            }
        }
    }
}
