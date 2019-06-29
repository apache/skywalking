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

import org.apache.skywalking.e2e.AbstractMatcher;
import org.apache.skywalking.e2e.service.Service;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A simple matcher to verify the given {@code Service} is expected
 *
 * @author kezhenxu94
 */
public class TopoMatcher extends AbstractMatcher {

    private List<NodeMatcher> nodes;
    private List<CallMatcher> calls;

    public void verify(final TopoData topoData) {
        if (Objects.nonNull(getNodes())) {
            verifyNodes(topoData);
        }

        if (Objects.nonNull(getCalls())) {
            verifyCalls(topoData);
        }
    }

    private void verifyNodes(TopoData topoData) {
        assertThat(topoData.getNodes()).hasSize(getNodes().size());

        int size = getNodes().size();

        for (int i = 0; i < size; i++) {
            getNodes().get(i).verify(topoData.getNodes().get(i));
        }
    }

    private void verifyCalls(TopoData topoData) {
        assertThat(topoData.getCalls()).hasSize(getCalls().size());

        int size = getCalls().size();

        for (int i = 0; i < size; i++) {
            getCalls().get(i).verify(topoData.getCalls().get(i));
        }
    }

    public List<NodeMatcher> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeMatcher> nodes) {
        this.nodes = nodes;
    }

    public List<CallMatcher> getCalls() {
        return calls;
    }

    public void setCalls(List<CallMatcher> calls) {
        this.calls = calls;
    }
}
