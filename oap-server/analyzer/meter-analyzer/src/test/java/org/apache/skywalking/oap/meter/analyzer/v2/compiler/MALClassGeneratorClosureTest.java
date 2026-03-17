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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closure-related tests for MALClassGenerator: tag closures, forEach closures,
 * ProcessRegistry FQCN resolution, network-profiling patterns, string concatenation,
 * regex match, and time() scalar.
 */
class MALClassGeneratorClosureTest {

    private MALClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MALClassGenerator(new ClassPool(true));
    }

    // ==================== Closure key extraction tests ====================

    @Test
    void tagClosurePutsCorrectKey() throws Exception {
        final MalExpression expr = generator.compile(
            "test_key",
            "metric.tag({tags -> tags.cluster = 'activemq::' + tags.cluster})");
        assertNotNull(expr);
        final String source = generator.generateSource(
            "metric.tag({tags -> tags.cluster = 'activemq::' + tags.cluster})");
        assertTrue(source.contains("this._tag"),
            "Generated source should reference pre-compiled closure");
    }

    @Test
    void tagClosureKeyExtractionViaGeneratedCode() throws Exception {
        final MalExpression expr = generator.compile(
            "test_key_gen",
            "metric.tag({tags -> tags.service_name = 'svc1'})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void tagClosureBracketAssignment() throws Exception {
        final MalExpression expr = generator.compile(
            "test_bracket",
            "metric.tag({tags -> tags['my_key'] = 'my_value'})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    // ==================== forEach closure tests ====================

    @Test
    void forEachClosureCompiles() throws Exception {
        final MalExpression expr = generator.compile(
            "test_foreach",
            "metric.forEach(['client', 'server'], {prefix, tags ->"
            + " tags[prefix + '_name'] = 'value'})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void forEachClosureWithBareReturn() throws Exception {
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
        final MalExpression expr = generator.compile(
            "test_registry",
            "metric.forEach(['client'], {prefix, tags ->\n"
            + "  tags[prefix + '_process_id'] = "
            + "ProcessRegistry.generateVirtualLocalProcess(tags.service, tags.instance)\n"
            + "})");
        assertNotNull(expr);
    }

    // ==================== Network-profiling full expression tests ====================

    @Test
    void networkProfilingFirstClosureCompiles() throws Exception {
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

    // ==================== String concatenation and regex in closures ====================

    @Test
    void apisixExpressionCompiles() throws Exception {
        final MalExpression expr = generator.compile(
            "test_apisix",
            "metric.tag({tags -> tags.service_name = 'APISIX::'"
            + "+(tags['skywalking_service']?.trim()?:'APISIX')})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void closureStringConcatenation() throws Exception {
        final MalExpression expr = generator.compile(
            "test_concat",
            "metric.tag({tags -> tags.service_name = 'APISIX::' + tags.service})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void regexMatchWithDefCompiles() throws Exception {
        final MalExpression expr = generator.compile(
            "test_regex",
            "metric.tag({tags ->\n"
            + "  def matcher = (tags.metrics_name =~ /\\.ssl\\.certificate\\.([^.]+)\\.expiration/)\n"
            + "  tags.secret_name = matcher ? matcher[0][1] : \"unknown\"\n"
            + "})");
        assertNotNull(expr);
        assertNotNull(expr.run(java.util.Map.of()));
    }

    @Test
    void envoyCAExpressionCompiles() throws Exception {
        final MalExpression expr = generator.compile(
            "test_envoy_ca",
            "(metric.tagMatch('metrics_name', '.*ssl.*expiration_unix_time_seconds')"
            + ".tag({tags ->\n"
            + "  def matcher = (tags.metrics_name =~ /\\.ssl\\.certificate\\.([^.]+)"
            + "\\.expiration_unix_time_seconds/)\n"
            + "  tags.secret_name = matcher ? matcher[0][1] : \"unknown\"\n"
            + "}).min(['app', 'secret_name']) - time())"
            + ".downsampling(MIN).service(['app'], Layer.MESH_DP)");
        assertNotNull(expr);
    }

    @Test
    void timeScalarFunctionHandledInMetadata() throws Exception {
        final MalExpression expr = generator.compile(
            "test_time",
            "(metric.sum(['app']) - time()).service(['app'], Layer.GENERAL)");
        assertNotNull(expr);
        assertNotNull(expr.metadata());
        assertTrue(expr.metadata().getSamples().contains("metric"));
        assertTrue(expr.metadata().getSamples().size() == 1);
    }
}
