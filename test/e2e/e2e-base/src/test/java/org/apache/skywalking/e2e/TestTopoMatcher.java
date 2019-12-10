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

import org.apache.skywalking.e2e.assertor.exception.VariableNotFoundException;
import org.apache.skywalking.e2e.topo.Call;
import org.apache.skywalking.e2e.topo.Node;
import org.apache.skywalking.e2e.topo.TopoData;
import org.apache.skywalking.e2e.topo.TopoMatcher;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhangwei
 */
public class TestTopoMatcher {

    private TopoMatcher topoMatcher;

    @Before
    public void setUp() throws IOException {
        try (InputStream expectedInputStream = new ClassPathResource("topo.yml").getInputStream()) {
            topoMatcher = new Yaml().loadAs(expectedInputStream, TopoMatcher.class);
        }
    }

    @Test
    public void shouldSuccess() {
        final List<Node> nodes = new ArrayList<>();
        nodes.add(new Node().setId("1").setName("User").setType("USER").setIsReal("false"));
        nodes.add(new Node().setId("2").setName("projectB-pid:27960@skywalking-server-0001").setType("Tomcat").setIsReal("true"));
        nodes.add(new Node().setId("3").setName("projectB-pid:27961@skywalking-server-0001").setType("Tomcat").setIsReal("true"));


        final List<Call> calls = new ArrayList<>();
        calls.add(new Call().setId("1_2").setSource("1").setTarget("2"));
        calls.add(new Call().setId("1_3").setSource("1").setTarget("3"));

        final TopoData topoData = new TopoData().setNodes(nodes).setCalls(calls);

        topoMatcher.verify(topoData);
    }

    @Test(expected = VariableNotFoundException.class)
    public void shouldVariableNotFound() {
        final List<Node> nodes = new ArrayList<>();
        nodes.add(new Node().setId("1").setName("User").setType("USER").setIsReal("false"));
        nodes.add(new Node().setId("2").setName("projectA-pid:27960@skywalking-server-0001").setType("Tomcat").setIsReal("true"));
        nodes.add(new Node().setId("3").setName("projectB-pid:27961@skywalking-server-0001").setType("Tomcat").setIsReal("true"));


        final List<Call> calls = new ArrayList<>();
        calls.add(new Call().setId("1_2").setSource("1").setTarget("2"));
        calls.add(new Call().setId("1_3").setSource("1").setTarget("3"));

        final TopoData topoData = new TopoData().setNodes(nodes).setCalls(calls);

        topoMatcher.verify(topoData);
    }
}
