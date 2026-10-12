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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalExpression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiles rules with Javassist, which is what proves the generated Java is valid: a codegen mistake surfaces
 * here as a compile failure rather than as a runtime error on a production span.
 */
class TALClassGeneratorTest {

    @Test
    void compileAServiceMetric() throws Exception {
        final TALClassGenerator generator = generator("svc_metric");
        final TalExpression expression = generator.compile(
            "filter { extractor {\n"
                + "  layer \"OTLP_SERVICE\"\n"
                + "  def svc = service(meta.service, meta.layer)\n"
                + "  metrics { name \"service_cpm\"; scope svc; function cpm; value 1 }\n"
                + "} }");
        assertNotNull(expression);
        assertEquals(1, generator.getMetricDeclarations().size());
        final TALMetricDeclaration declaration = generator.getMetricDeclarations().get(0);
        assertEquals("otlp_service_cpm", declaration.fullName);
        assertEquals("sumPerMin", declaration.function);
        assertEquals("SERVICE", declaration.scopeType);
    }

    @Test
    void compileEveryFunctionShape() throws Exception {
        final TALClassGenerator generator = generator("all_shapes");
        generator.compile(
            "filter { extractor {\n"
                + "  def svc = service(meta.service, meta.layer)\n"
                + "  metrics { name \"a\"; scope svc; function avg; value meta.latency }\n"
                + "  metrics { name \"b\"; scope svc; function percent; match meta.statusCode != \"ERROR\" }\n"
                + "  metrics { name \"c\"; scope svc; function apdex; value meta.latency;"
                + "            status meta.statusCode != \"ERROR\" }\n"
                + "  metrics { name \"d\"; scope svc; function percentile; value meta.latency;"
                + "            buckets [10, 25, 50] }\n"
                + "  metrics { name \"e\"; scope svc; function histogram; value meta.latency;"
                + "            buckets [10, 25, 50] }\n"
                + "  metrics { name \"f\"; scope svc; function sumLabeled; value 1;"
                + "            labels status: attr(\"http.response.status_code\") }\n"
                + "} }");
        final List<String> functions = generator.getMetricDeclarations().stream()
                                                .map(d -> d.function)
                                                .collect(Collectors.toList());
        assertEquals(
            List.of("avg", "percent", "apdex", "sumHistogramPercentile", "sumHistogram", "sumLabeled"),
            functions);
    }

    @Test
    void compileEveryEntityScope() throws Exception {
        final TALClassGenerator generator = generator("all_scopes");
        generator.compile(
            "filter { extractor {\n"
                + "  def svc = service(meta.service, meta.layer)\n"
                + "  def inst = instance(meta.service, meta.serviceInstance, meta.layer)\n"
                + "  def ep = endpoint(meta.service, meta.endpoint, meta.layer)\n"
                + "  def rel = serviceRelation(CLIENT, meta.service, meta.layer, \"db\","
                + "            \"VIRTUAL_DATABASE\", component(\"postgresql\"))\n"
                + "  metrics { name \"a\"; scope svc; function cpm; value 1 }\n"
                + "  metrics { name \"b\"; scope inst; function cpm; value 1 }\n"
                + "  metrics { name \"c\"; scope ep; function cpm; value 1 }\n"
                + "  metrics { name \"d\"; scope rel; function cpm; value 1 }\n"
                + "} }");
        final List<String> scopes = generator.getMetricDeclarations().stream()
                                             .map(d -> d.scopeType)
                                             .collect(Collectors.toList());
        assertEquals(List.of("SERVICE", "SERVICE_INSTANCE", "ENDPOINT", "SERVICE_RELATION"), scopes);
    }

    @Test
    void compileSinkAndPersist() throws Exception {
        final TALClassGenerator generator = generator("votes");
        assertNotNull(generator.compile(
            "filter {\n"
                + "  extractor { layer \"OTLP_SERVICE\" }\n"
                + "  persist { enforcer {} }\n"
                + "}"));
    }

    @Test
    void compileTypedSpanChain() throws Exception {
        final TALClassGenerator generator = generator("typed_span");
        generator.setInputType(SpanMetadata.class);
        assertNotNull(generator.compile(
            "filter { extractor { endpoint span.endpoint; service span.service } }"));
    }

    @Test
    void metricsBlocksInBranchesShareOneMetric() throws Exception {
        final TALClassGenerator generator = generator("branches");
        generator.compile(
            "filter { extractor {\n"
                + "  def svc = service(meta.service, meta.layer)\n"
                + "  if (meta.kind == \"SERVER\") {\n"
                + "    metrics { name \"cpm\"; scope svc; function cpm; value 1 }\n"
                + "  } else {\n"
                + "    metrics { name \"cpm\"; scope svc; function cpm; value 2 }\n"
                + "  }\n"
                + "} }");
        assertEquals(1, generator.getMetricDeclarations().size());
    }

    @Test
    void rejectOneMetricWithTwoShapes() {
        final TALClassGenerator generator = generator("two_shapes");
        final Exception e = assertThrows(Exception.class, () -> generator.compile(
            "filter { extractor {\n"
                + "  def svc = service(meta.service, meta.layer)\n"
                + "  metrics { name \"x\"; scope svc; function cpm; value 1 }\n"
                + "  metrics { name \"x\"; scope svc; function avg; value meta.latency }\n"
                + "} }"));
        assertTrue(rootMessage(e).contains("declared twice with different shapes"), rootMessage(e));
    }

    @Test
    void rejectUnknownFunction() {
        assertCompileFails(
            "filter { extractor { def svc = service(meta.service, meta.layer)\n"
                + "metrics { name \"x\"; scope svc; function nope; value 1 } } }",
            "unknown metrics function");
    }

    @Test
    void rejectMatchOnANonPercentFunction() {
        assertCompileFails(
            "filter { extractor { def svc = service(meta.service, meta.layer)\n"
                + "metrics { name \"x\"; scope svc; function cpm; value 1;"
                + " match meta.statusCode != \"ERROR\" } } }",
            "match belongs to percent only");
    }

    @Test
    void rejectPercentileWithoutBuckets() {
        assertCompileFails(
            "filter { extractor { def svc = service(meta.service, meta.layer)\n"
                + "metrics { name \"x\"; scope svc; function percentile; value meta.latency } } }",
            "buckets belong to histogram and percentile only");
    }

    @Test
    void rejectBucketsThatAreNotAscending() {
        assertCompileFails(
            "filter { extractor { def svc = service(meta.service, meta.layer)\n"
                + "metrics { name \"x\"; scope svc; function percentile; value meta.latency;"
                + " buckets [10, 5] } } }",
            "not ascending");
    }

    @Test
    void rejectAScopeThatIsNotAnEntity() {
        assertCompileFails(
            "filter { extractor { def notAnEntity = meta.service\n"
                + "metrics { name \"x\"; scope notAnEntity; function cpm; value 1 } } }",
            "is not an entity");
    }

    @Test
    void rejectARelationWithoutADetectPoint() {
        assertCompileFails(
            "filter { extractor { def r = serviceRelation(meta.service, meta.layer, \"db\","
                + " \"VIRTUAL_DATABASE\", 1)\n"
                + "metrics { name \"x\"; scope r; function cpm; value 1 } } }",
            "takes 6 arguments");
    }

    @Test
    void rejectAnOutputFieldWithoutAnOutputType() {
        assertCompileFails("filter { extractor { statement attr(\"db.query.text\") } }",
            "needs the rule to declare an outputType");
    }

    private static void assertCompileFails(final String dsl, final String expectedMessage) {
        final Exception e = assertThrows(Exception.class, () -> generator("bad").compile(dsl));
        assertTrue(rootMessage(e).contains(expectedMessage),
            "expected a message containing '" + expectedMessage + "' but got: " + rootMessage(e));
    }

    private static String rootMessage(final Throwable e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }

    private static TALClassGenerator generator(final String ruleName) {
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint(ruleName);
        generator.setMetricPrefix("otlp");
        generator.setSourceRef(DslSourceRef.ofRule("tal/test.yaml", 1));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        return generator;
    }
}
