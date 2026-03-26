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
 */

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import com.google.common.collect.ImmutableMap;
import javassist.ClassPool;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalExtensionRegistry;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the MAL extension function SPI (namespace::method syntax).
 */
class MALExtensionFunctionTest {

    private MALClassGenerator generator;

    @BeforeAll
    static void initRegistry() {
        MalExtensionRegistry.init();
    }

    @BeforeEach
    void setUp() {
        generator = new MALClassGenerator(new ClassPool(true));
    }

    @Test
    void testRegistryDiscovery() {
        assertNotNull(MalExtensionRegistry.lookup("test", "scale"));
        assertNotNull(MalExtensionRegistry.lookup("test", "noop"));
    }

    @Test
    void testParseExtensionCall() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse("metric.test::scale(2.0)");
        assertTrue(ast instanceof MALExpressionModel.MetricExpr);
        final MALExpressionModel.MetricExpr me = (MALExpressionModel.MetricExpr) ast;
        assertEquals(1, me.getMethodChain().size());
        final MALExpressionModel.MethodCall mc = me.getMethodChain().get(0);
        assertEquals("test", mc.getNamespace());
        assertEquals("scale", mc.getName());
        assertTrue(mc.isExtension());
        assertEquals(1, mc.getArguments().size());
    }

    @Test
    void testParseNoArgExtensionCall() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse("metric.test::noop()");
        assertTrue(ast instanceof MALExpressionModel.MetricExpr);
        final MALExpressionModel.MetricExpr me = (MALExpressionModel.MetricExpr) ast;
        final MALExpressionModel.MethodCall mc = me.getMethodChain().get(0);
        assertEquals("test", mc.getNamespace());
        assertEquals("noop", mc.getName());
        assertTrue(mc.getArguments().isEmpty());
    }

    @Test
    void testCompileAndRunScale() throws Exception {
        final MalExpression expr = generator.compile(
            "test_ext_scale", "metric.test::scale(3.0)");
        assertNotNull(expr);

        final SampleFamily input = SampleFamilyBuilder.newBuilder(
            Sample.builder().labels(ImmutableMap.of("k", "v")).value(10.0).build()).build();
        final SampleFamily result = expr.run(Map.of("metric", input));

        assertNotNull(result);
        assertEquals(1, result.samples.length);
        assertEquals(30.0, result.samples[0].getValue(), 0.001);
    }

    @Test
    void testCompileAndRunNoop() throws Exception {
        final MalExpression expr = generator.compile(
            "test_ext_noop", "metric.test::noop()");
        assertNotNull(expr);

        final SampleFamily input = SampleFamilyBuilder.newBuilder(
            Sample.builder().labels(ImmutableMap.of("k", "v")).value(5.0).build()).build();
        final SampleFamily result = expr.run(Map.of("metric", input));

        assertNotNull(result);
        assertEquals(5.0, result.samples[0].getValue(), 0.001);
    }

    @Test
    void testExtensionInChain() throws Exception {
        final MalExpression expr = generator.compile(
            "test_ext_chain",
            "metric.sum(['k']).test::scale(2.0)");
        assertNotNull(expr);

        final SampleFamily input = SampleFamilyBuilder.newBuilder(
            Sample.builder().labels(ImmutableMap.of("k", "v")).value(7.0).build()).build();
        final SampleFamily result = expr.run(Map.of("metric", input));

        assertNotNull(result);
        assertEquals(14.0, result.samples[0].getValue(), 0.001);
    }

    @Test
    void testUnknownNamespace() {
        assertThrows(IllegalArgumentException.class, () ->
            generator.compile("test_unknown_ns", "metric.unknown::foo()"));
    }

    @Test
    void testUnknownMethod() {
        assertThrows(IllegalArgumentException.class, () ->
            generator.compile("test_unknown_method", "metric.test::nonexistent()"));
    }

    @Test
    void testWrongArgCount() {
        assertThrows(IllegalArgumentException.class, () ->
            generator.compile("test_wrong_args", "metric.test::scale()"));
    }
}
