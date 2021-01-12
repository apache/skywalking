/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.e2e.assertor.exception.VariableNotFoundException;
import org.apache.skywalking.e2e.topo.Call;
import org.apache.skywalking.e2e.topo.ServiceInstanceNode;
import org.apache.skywalking.e2e.topo.ServiceInstanceTopology;
import org.apache.skywalking.e2e.topo.ServiceInstanceTopologyMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestServiceInstanceTopologyMatcher {

    private ServiceInstanceTopologyMatcher topoMatcher;

    @BeforeEach
    public void setUp() throws IOException {
        try (InputStream expectedInputStream = new ClassPathResource("serviceInstanceTopo.yml").getInputStream()) {
            topoMatcher = new Yaml().loadAs(expectedInputStream, ServiceInstanceTopologyMatcher.class);
        }
    }

    @Test
    public void shouldSuccess() {
        final List<ServiceInstanceNode> nodes = new ArrayList<>();
        nodes.add(new ServiceInstanceNode().setId("2")
                                           .setName("e2e-cluster-provider-pid:1582@2ffd0ee4eeb1")
                                           .setServiceId("2")
                                           .setServiceName("e2e-cluster-provider")
                                           .setType("Tomcat")
                                           .setIsReal("true"));
        nodes.add(new ServiceInstanceNode().setId("3")
                                           .setName("e2e-cluster-provider-pid:1583@2ffd0ee4eeb1")
                                           .setServiceId("2")
                                           .setServiceName("e2e-cluster-provider")
                                           .setType("Tomcat")
                                           .setIsReal("true"));
        nodes.add(new ServiceInstanceNode().setId("4")
                                           .setName("e2e-cluster-consumer-pid:1591@2ffd0ee4eeb1")
                                           .setServiceId("3")
                                           .setServiceName("e2e-cluster-consumer")
                                           .setIsReal("true"));

        final List<Call> calls = new ArrayList<>();
        calls.add(new Call().setId("4-3").setSource("4").setTarget("3"));
        calls.add(new Call().setId("4-2").setSource("4").setTarget("2"));

        final ServiceInstanceTopology topoData = new ServiceInstanceTopology().setNodes(nodes).setCalls(calls);

        topoMatcher.verify(topoData);
    }

    @Test
    public void shouldVariableNotFound() {
        assertThrows(VariableNotFoundException.class, () -> {
            final List<ServiceInstanceNode> nodes = new ArrayList<>();
            nodes.add(new ServiceInstanceNode().setId("2")
                                               .setName("e2e-cluster-provider-pid:1582@2ffd0ee4eeb1")
                                               .setServiceId("2")
                                               .setServiceName("e2e-cluster-provider")
                                               .setType("Tomcat")
                                               .setIsReal("true"));
            nodes.add(new ServiceInstanceNode().setId("3")
                                               .setName("e2e-cluster-Aprovider-pid:1583@2ffd0ee4eeb1")
                                               .setServiceId("2")
                                               .setServiceName("e2e-cluster-provider")
                                               .setType("Tomcat")
                                               .setIsReal("true"));
            nodes.add(new ServiceInstanceNode().setId("4")
                                               .setName("e2e-cluster-consumer-pid:1591@2ffd0ee4eeb1")
                                               .setServiceId("3")
                                               .setServiceName("e2e-cluster-consumer")
                                               .setIsReal("true"));

            final List<Call> calls = new ArrayList<>();
            calls.add(new Call().setId("4-3").setSource("4").setTarget("3"));
            calls.add(new Call().setId("4-2").setSource("4").setTarget("2"));

            final ServiceInstanceTopology topoData = new ServiceInstanceTopology().setNodes(nodes).setCalls(calls);

            topoMatcher.verify(topoData);
        });
    }
}
