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
public class ServiceInstanceTopologyMatcher extends AbstractMatcher<ServiceInstanceTopology> {

    private List<ServiceInstanceNodeMatcher> nodes;
    private List<CallMatcher> calls;

    @Override
    public void verify(ServiceInstanceTopology topoData) {
        if (nonNull(getNodes())) {
            verifyNodes(topoData);
        }

        if (nonNull(getCalls())) {
            convertNodeId(getCalls(), topoData.getNodes());
            verifyCalls(topoData);
        }
    }

    private void verifyNodes(ServiceInstanceTopology topoData) {
        for (int i = 0; i < getNodes().size(); i++) {
            boolean matched = false;
            for (int j = 0; j < topoData.getNodes().size(); j++) {
                try {
                    getNodes().get(i).verify(topoData.getNodes().get(j));
                    matched = true;
                } catch (Throwable ignored) {
                }
            }
            if (!matched) {
                fail("\nExpected: %s\nActual: %s", getNodes(), topoData.getNodes());
            }
        }
    }

    private void verifyCalls(ServiceInstanceTopology topoData) {
        for (int i = 0; i < getCalls().size(); i++) {
            boolean matched = false;
            for (int j = 0; j < topoData.getCalls().size(); j++) {
                try {
                    getCalls().get(i).verify(topoData.getCalls().get(j));
                    matched = true;
                } catch (Throwable ignored) {
                }
            }
            if (!matched) {
                fail("\nExpected: %s\nActual: %s", getCalls(), topoData.getCalls());
            }
        }
    }

    private static void convertNodeId(List<CallMatcher> callMatchers, List<ServiceInstanceNode> nodes) {
        for (CallMatcher callMatcher : callMatchers) {
            ServiceInstanceNode sourceNode = VariableExpressParser.parse(
                callMatcher.getSource(), nodes, ServiceInstanceNode::getName);
            ServiceInstanceNode targetNode = VariableExpressParser.parse(
                callMatcher.getTarget(), nodes, ServiceInstanceNode::getName);

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
