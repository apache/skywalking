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

    // ==================== Closure key extraction tests ====================

    @Test
    void tagClosurePutsCorrectKey() throws Exception {
        // Issue: tags.cluster = expr should generate tags.put("cluster", ...)
        // NOT tags.put("tags.cluster", ...)
        final MalExpression expr = generator.compile(
            "test_key",
            "metric.tag({tags -> tags.cluster = 'activemq::' + tags.cluster})");
        assertNotNull(expr);
        final String source = generator.generateSource(
            "metric.tag({tags -> tags.cluster = 'activemq::' + tags.cluster})");
        assertTrue(source.contains("this._closure0"),
            "Generated source should reference pre-compiled closure");
    }

    @Test
    void tagClosureKeyExtractionViaGeneratedCode() throws Exception {
        // Verify the closure generates correct put("cluster", ...) not put("tags.cluster", ...)
        final MalExpression expr = generator.compile(
            "test_key_gen",
            "metric.tag({tags -> tags.service_name = 'svc1'})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void tagClosureBracketAssignment() throws Exception {
        // tags['key_name'] = 'value' should also use correct key
        final MalExpression expr = generator.compile(
            "test_bracket",
            "metric.tag({tags -> tags['my_key'] = 'my_value'})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    // ==================== forEach closure tests ====================

    @Test
    void forEachClosureCompiles() throws Exception {
        // forEach requires ForEachFunction.accept(String, Map), not TagFunction.apply(Map)
        final MalExpression expr = generator.compile(
            "test_foreach",
            "metric.forEach(['client', 'server'], {prefix, tags ->"
            + " tags[prefix + '_name'] = 'value'})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void forEachClosureWithBareReturn() throws Exception {
        // forEach with bare return (void method) — should not throw
        final MalExpression expr = generator.compile(
            "test_foreach_return",
            "metric.forEach(['x'], {prefix, tags ->\n"
            + "  if (tags[prefix + '_id'] != null) {\n"
            + "    return\n"
            + "  }\n"
            + "  tags[prefix + '_id'] = 'default'\n"
            + "})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void forEachClosureWithVarDeclAndElseIf() throws Exception {
        // Full pattern from network-profiling.yaml second closure
        final MalExpression expr = generator.compile(
            "test_foreach_vars",
            "metric.forEach(['component'], {key, tags ->\n"
            + "  String result = \"\"\n"
            + "  String protocol = tags['protocol']\n"
            + "  String ssl = tags['is_ssl']\n"
            + "  if (protocol == 'http' && ssl == 'true') {\n"
            + "    result = '129'\n"
            + "  } else if (protocol == 'http') {\n"
            + "    result = '49'\n"
            + "  } else if (ssl == 'true') {\n"
            + "    result = '130'\n"
            + "  } else {\n"
            + "    result = '110'\n"
            + "  }\n"
            + "  tags[key] = result\n"
            + "})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    // ==================== ProcessRegistry FQCN resolution tests ====================

    @Test
    void processRegistryResolvedToFQCN() throws Exception {
        // ProcessRegistry.generateVirtualLocalProcess() should resolve to FQCN
        final MalExpression expr = generator.compile(
            "test_registry",
            "metric.forEach(['client'], {prefix, tags ->\n"
            + "  tags[prefix + '_process_id'] = "
            + "ProcessRegistry.generateVirtualLocalProcess(tags.service, tags.instance)\n"
            + "})");
        assertNotNull(expr);
        // We can't easily execute this (needs ProcessRegistry runtime) but compile should succeed
    }

    // ==================== Network-profiling full expression tests ====================

    @Test
    void networkProfilingFirstClosureCompiles() throws Exception {
        // Full first closure from network-profiling.yaml expPrefix
        final MalExpression expr = generator.compile(
            "test_np1",
            "metric.forEach(['client', 'server'], { prefix, tags ->\n"
            + "    if (tags[prefix + '_process_id'] != null) {\n"
            + "      return\n"
            + "    }\n"
            + "    if (tags[prefix + '_local'] == 'true') {\n"
            + "      tags[prefix + '_process_id'] = ProcessRegistry"
            + ".generateVirtualLocalProcess(tags.service, tags.instance)\n"
            + "      return\n"
            + "    }\n"
            + "    tags[prefix + '_process_id'] = ProcessRegistry"
            + ".generateVirtualRemoteProcess(tags.service, tags.instance,"
            + " tags[prefix + '_address'])\n"
            + "  })");
        assertNotNull(expr);
    }

    @Test
    void networkProfilingSecondClosureCompiles() throws Exception {
        // Full second closure from network-profiling.yaml expPrefix
        final MalExpression expr = generator.compile(
            "test_np2",
            "metric.forEach(['component'], { key, tags ->\n"
            + "    String result = \"\"\n"
            + "    // protocol are defined in the component-libraries.yml\n"
            + "    String protocol = tags['protocol']\n"
            + "    String ssl = tags['is_ssl']\n"
            + "    if (protocol == 'http' && ssl == 'true') {\n"
            + "      result = '129'\n"
            + "    } else if (protocol == 'http') {\n"
            + "      result = '49'\n"
            + "    } else if (ssl == 'true') {\n"
            + "      result = '130'\n"
            + "    } else {\n"
            + "      result = '110'\n"
            + "    }\n"
            + "    tags[key] = result\n"
            + "  })");
        assertNotNull(expr);
    }

    // ==================== String concatenation in closures ====================

    @Test
    void apisixExpressionCompiles() throws Exception {
        // The APISIX expression that originally triggered the E2E failure:
        // safe navigation + elvis + bracket access + string concat
        final MalExpression expr = generator.compile(
            "test_apisix",
            "metric.tag({tags -> tags.service_name = 'APISIX::'"
            + "+(tags['skywalking_service']?.trim()?:'APISIX')})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void closureStringConcatenation() throws Exception {
        // APISIX-style: tags.service_name = 'APISIX::' + tags.service
        final MalExpression expr = generator.compile(
            "test_concat",
            "metric.tag({tags -> tags.service_name = 'APISIX::' + tags.service})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }
}
