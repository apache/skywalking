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

import javassist.ClassPool;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic compilation, error handling, source generation, and bytecode verification
 * tests for MALClassGenerator.
 */
class MALClassGeneratorTest {

    private MALClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MALClassGenerator(new ClassPool(true));
    }

    // ==================== Basic compilation tests ====================

    @Test
    void compileSimpleMetric() throws Exception {
        final MalExpression expr = generator.compile(
            "test_metric", "instance_jvm_cpu");
        assertNotNull(expr);
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
        assertTrue(source.contains("getOrDefault"));
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

    @Test
    void compileValueEqual() throws Exception {
        final MalExpression expr = generator.compile(
            "test_value_equal",
            "kube_node_status_condition.valueEqual(1).sum(['cluster','node','condition'])");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void compileMethodCallMultiply() throws Exception {
        final MalExpression expr = generator.compile(
            "test_multiply",
            "process_cpu_usage.multiply(100)");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    // ==================== Error handling tests ====================

    @Test
    void emptyExpressionThrows() {
        assertThrows(Exception.class, () -> generator.compile("test", ""));
    }

    @Test
    void malformedExpressionThrows() {
        assertThrows(Exception.class,
            () -> generator.compile("test", "metric.@invalid"));
    }

    @Test
    void unclosedParenthesisThrows() {
        assertThrows(Exception.class,
            () -> generator.compile("test", "(metric1 "));
    }

    @Test
    void invalidFilterClosureThrows() {
        assertThrows(Exception.class,
            () -> generator.compileFilter("invalid filter"));
    }

    @Test
    void emptyFilterBodyThrows() {
        assertThrows(Exception.class,
            () -> generator.compileFilter("{ }"));
    }

    // ==================== Bytecode verification ====================

    @Test
    void runMethodHasLocalVariableTable() throws Exception {
        final java.io.File tmpDir = java.nio.file.Files.createTempDirectory("mal-lvt").toFile();
        try {
            final ClassPool pool = new ClassPool(true);
            final MALClassGenerator gen = new MALClassGenerator(pool);
            gen.setClassOutputDir(tmpDir);
            final MalExpression expr = gen.compile(
                "test_lvt", "instance_jvm_cpu.sum(['service', 'instance'])");
            assertNotNull(expr);
            final java.io.File[] classFiles = tmpDir.listFiles((d, n) -> n.endsWith(".class"));
            assertNotNull(classFiles);
            assertTrue(classFiles.length > 0, "Should have generated .class file");
            final javassist.bytecode.ClassFile cf =
                new javassist.bytecode.ClassFile(
                    new java.io.DataInputStream(
                        new java.io.FileInputStream(classFiles[0])));
            final javassist.bytecode.MethodInfo runMi = cf.getMethod("run");
            assertNotNull(runMi, "Should have run() method");
            final javassist.bytecode.CodeAttribute code = runMi.getCodeAttribute();
            assertNotNull(code, "run() should have CodeAttribute");
            final javassist.bytecode.LocalVariableAttribute lva =
                (javassist.bytecode.LocalVariableAttribute)
                    code.getAttribute(javassist.bytecode.LocalVariableAttribute.tag);
            assertNotNull(lva, "run() should have LocalVariableTable attribute");
            boolean foundSamples = false;
            boolean foundSf = false;
            for (int i = 0; i < lva.tableLength(); i++) {
                final String name = lva.variableName(i);
                if ("samples".equals(name)) {
                    foundSamples = true;
                }
                if ("sf".equals(name)) {
                    foundSf = true;
                }
            }
            assertTrue(foundSamples, "LVT should contain 'samples'");
            assertTrue(foundSf, "LVT should contain 'sf'");
        } finally {
            for (final java.io.File f : tmpDir.listFiles()) {
                f.delete();
            }
            tmpDir.delete();
        }
    }
}
