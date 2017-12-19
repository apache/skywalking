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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author wusheng
 */
public class GraphManagerTest {
    private static PrintStream OUT_REF;
    private ByteArrayOutputStream outputStream;
    private static String LINE_SEPARATE = System.lineSeparator();

    @Before
    public void initAndHoldOut() {
        OUT_REF = System.out;
        outputStream = new ByteArrayOutputStream();
        PrintStream testStream = new PrintStream(outputStream);
        System.setOut(testStream);
    }

    @After
    public void reset() {
        System.setOut(OUT_REF);
    }

    @Test
    public void testGraph() {
        Graph<String> testGraph = GraphManager.INSTANCE.createIfAbsent(1, String.class);
        Node<String, String> node = testGraph.addNode(new Node1Processor());
        Node<String, Integer> node1 = node.addNext(new Node2Processor());
        testGraph.start("Input String");

        String output = outputStream.toString();
        String expected = "Node1 process: s=Input String" + LINE_SEPARATE +
            "Node2 process: s=Input String" + LINE_SEPARATE;

        Assert.assertEquals(expected, output);
    }

    @Test
    public void testGraphWithChainStyle() {
        Graph<String> graph = GraphManager.INSTANCE.createIfAbsent(2, String.class);
        graph.addNode(new Node1Processor()).addNext(new Node2Processor()).addNext(new Node4Processor());

        graph.start("Input String");

        String output = outputStream.toString();
        String expected = "Node1 process: s=Input String" + LINE_SEPARATE +
            "Node2 process: s=Input String" + LINE_SEPARATE +
            "Node4 process: int=123" + LINE_SEPARATE;

        Assert.assertEquals(expected, output);
    }

    @Test(expected = PotentialCyclicGraphException.class)
    public void testPotentialAcyclicGraph() {
        Graph<String> testGraph = GraphManager.INSTANCE.createIfAbsent(3, String.class);
        Node<String, String> node = testGraph.addNode(new Node1Processor());
        node.addNext(new Node1Processor());
    }

    @Test
    public void testContinueStream() {
        Graph<String> graph = GraphManager.INSTANCE.createIfAbsent(4, String.class);
        graph.addNode(new Node1Processor()).addNext(new Node2Processor()).addNext(new Node4Processor());

        Next next = GraphManager.INSTANCE.findGraph(4, String.class).toFinder().findNext(2);

        next.execute(123);
        String output = outputStream.toString();
        String expected =
            "Node4 process: int=123" + LINE_SEPARATE;

        Assert.assertEquals(expected, output);
    }

    @Test(expected = NodeNotFoundException.class)
    public void handlerNotFound() {
        Graph<String> graph = GraphManager.INSTANCE.createIfAbsent(5, String.class);
        graph.addNode(new Node1Processor()).addNext(new Node2Processor()).addNext(new Node4Processor());

        Next next = GraphManager.INSTANCE.findGraph(5, String.class).toFinder().findNext(3);
    }

    @Test
    public void testDeadEndWay() {
        Graph<String> graph = GraphManager.INSTANCE.createIfAbsent(7, String.class);
        graph.addNode(new Node1Processor()).addNext(new WayToNode<String, Integer>(new Node2Processor()) {
            @Override protected void in(String input) {
                //don't call `out(intput)`;
            }
        });

        graph.start("Input String");
        String output = outputStream.toString();
        String expected = "Node1 process: s=Input String" + LINE_SEPARATE;

        Assert.assertEquals(expected, output);
    }

    @Test
    public void testEntryWay() {
        Graph<String> graph = GraphManager.INSTANCE.createIfAbsent(8, String.class);
        graph.addNode(new WayToNode<String, String>(new Node1Processor()) {
            @Override protected void in(String input) {
                //don't call `out(intput)`;
            }
        }).addNext(new Node2Processor());

        graph.start("Input String");

        Assert.assertEquals("", outputStream.toString());
    }

    @After
    public void tearDown() {
        GraphManager.INSTANCE.reset();
    }
}
