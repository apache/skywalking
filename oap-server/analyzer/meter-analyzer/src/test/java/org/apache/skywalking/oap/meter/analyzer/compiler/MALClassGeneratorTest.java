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

package org.apache.skywalking.oap.meter.analyzer.compiler;

import javassist.ClassPool;
import org.apache.skywalking.oap.meter.analyzer.dsl.MalExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MALClassGeneratorTest {

    private MALClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MALClassGenerator(new ClassPool(true));
    }

    @Test
    void compileSimpleMetric() throws Exception {
        final MalExpression expr = generator.compile(
            "test_metric", "instance_jvm_cpu");
        assertNotNull(expr);
        // Run returns SampleFamily.EMPTY since no samples are provided
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void compileMethodChain() throws Exception {
        final MalExpression expr = generator.compile(
            "test_sum",
            "instance_jvm_cpu.sum(['service', 'instance'])");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void compileArithmeticAdd() throws Exception {
        final MalExpression expr = generator.compile(
            "test_add", "metric_a + metric_b");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void compileNumberTimesMetric() throws Exception {
        final MalExpression expr = generator.compile(
            "test_mul", "100 * process_cpu_seconds_total");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void compileParenChainExpr() throws Exception {
        final MalExpression expr = generator.compile(
            "test_paren",
            "(process_cpu_seconds_total * 100).sum(['service', 'instance']).rate('PT1M')");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void compileWithEnumRef() throws Exception {
        final MalExpression expr = generator.compile(
            "test_enum",
            "instance_jvm_cpu.sum(['service']).service(['service'], Layer.GENERAL)");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void compileWithDownsamplingType() throws Exception {
        final MalExpression expr = generator.compile(
            "test_ds",
            "instance_jvm_cpu.sum(['service']).downsampling(SUM)");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void compileWithClosureTag() throws Exception {
        final MalExpression expr = generator.compile(
            "test_closure",
            "instance_jvm_cpu.tag({tags -> tags.service_name = 'svc1'})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void generateSourceReturnsJavaCode() {
        final String source = generator.generateSource(
            "instance_jvm_cpu.sum(['service'])");
        assertNotNull(source);
        // Generated source should contain getOrDefault for the metric
        org.junit.jupiter.api.Assertions.assertTrue(
            source.contains("getOrDefault"));
    }

    @Test
    void filterSafeNavCompiles() throws Exception {
        final String source = generator.generateFilterSource(
            "{ tags -> tags.job_name == 'aws-cloud-eks-monitoring'"
            + " && tags.Service?.trim() }");
        assertNotNull(source);
        assertTrue(source.contains("trim"), "Generated source should contain trim()");
        assertNotNull(generator.compileFilter(
            "{ tags -> tags.job_name == 'aws-cloud-eks-monitoring'"
            + " && tags.Service?.trim() }"));
    }

    // ==================== Error handling tests ====================

    @Test
    void emptyExpressionThrows() {
        // Demo error: MAL expression parsing failed: 1:0 mismatched input '<EOF>'
        //   expecting {IDENTIFIER, NUMBER, '(', '-'}
        assertThrows(Exception.class, () -> generator.compile("test", ""));
    }

    @Test
    void malformedExpressionThrows() {
        // Demo error: MAL expression parsing failed: 1:7 token recognition error at: '@'
        assertThrows(Exception.class,
            () -> generator.compile("test", "metric.@invalid"));
    }

    @Test
    void unclosedParenthesisThrows() {
        // Demo error: MAL expression parsing failed: 1:8 mismatched input '<EOF>'
        //   expecting {')', '+', '-', '*', '/'}
        assertThrows(Exception.class,
            () -> generator.compile("test", "(metric1 "));
    }

    @Test
    void invalidFilterClosureThrows() {
        // Demo error: MAL filter parsing failed: 1:0 mismatched input 'invalid'
        //   expecting '{'
        assertThrows(Exception.class,
            () -> generator.compileFilter("invalid filter"));
    }

    @Test
    void emptyFilterBodyThrows() {
        // Demo error: MAL filter parsing failed: 1:1 mismatched input '}'
        //   expecting {IDENTIFIER, ...}
        assertThrows(Exception.class,
            () -> generator.compileFilter("{ }"));
    }
}
