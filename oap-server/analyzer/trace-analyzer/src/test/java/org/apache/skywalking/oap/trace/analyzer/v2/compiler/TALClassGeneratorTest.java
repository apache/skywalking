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
import lombok.Setter;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TALOutputBuilder;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
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
                + "  layer \"OTLP_GENERAL\"\n"
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
                + "  metrics { name \"f\"; scope svc; function sumLabeled; value 1;"
                + "            labels status: attr(\"http.response.status_code\") }\n"
                + "} }");
        final List<String> functions = generator.getMetricDeclarations().stream()
                                                .map(d -> d.function)
                                                .collect(Collectors.toList());
        assertEquals(
            List.of("avg", "percent", "apdex", "sumHistogramPercentile", "sumLabeled"),
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
                + "  extractor { layer \"OTLP_GENERAL\" }\n"
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
            "buckets belong to percentile only");
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

    @Test
    void rejectApdexOnARelation() {
        assertCompileFails("filter { extractor {\n"
                + "  def rel = serviceRelation(CLIENT, meta.service, meta.layer, \"backend\", meta.layer,"
                + "            component(\"http\"))\n"
                + "  metrics { name \"x\"; scope rel; function apdex; value meta.latency\n"
                + "            status meta.statusCode != \"ERROR\" }\n"
                + "} }",
            "apdex needs a service, instance or endpoint scope");
    }

    @Test
    void rejectWhatTheLexerCannotRead() {
        // A YAML comment inside the DSL is not a comment there; it used to vanish and leave the line live.
        assertCompileFails("filter {\n  # a note\n  abort {}\n}", "token recognition error");
    }

    @Test
    void rejectAMetricNameThatIsNotALiteral() {
        assertCompileFails("filter { extractor {\n"
                + "  def svc = service(meta.service, meta.layer)\n"
                + "  metrics { name meta.service; scope svc; function cpm; value 1 }\n"
                + "} }",
            "a metric's name is a string literal");
    }

    @Test
    void rejectAnUnknownRoot() {
        // A misspelled `meta` used to read the input span instead.
        assertCompileFails("filter { if (mta.kind == \"SERVER\") { abort {} } }", "Cannot resolve 'mta.kind'");
    }

    @Test
    void rejectASinkWithoutAnOutputType() {
        assertCompileFails("filter { sink {} }", "this rule declares none");
    }

    @Test
    void rejectAnOutputTypeWithoutASink() {
        final TALClassGenerator generator = generator("no_sink");
        generator.setOutputType(StubRecord.class);
        final Exception e = assertThrows(Exception.class, () -> generator.compile(
            "filter { extractor { statement \"SELECT 1\" } }"));
        assertTrue(rootMessage(e).contains("needs a sink {} block"), rootMessage(e));
    }

    @Test
    void rejectABuildNetworkAliasThatReadsMoreThanTheCallerHalf() {
        assertCompileFails("filter { extractor {\n"
                + "  buildNetworkAlias callerScopedAddress(meta.service, pairing.peer)\n"
                + "} }",
            "its address reads pairing.service and pairing.peer");
        assertCompileFails("filter { extractor {\n"
                + "  buildNetworkAlias attr(\"server.address\")\n"
                + "} }",
            "not attr(...)");
    }

    @Test
    void rejectABuildNetworkAliasThatReadsAPerCallField() {
        assertCompileFails("filter { extractor {\n"
                + "  buildNetworkAlias callerScopedAddress(pairing.service, pairing.spanId)\n"
                + "} }",
            "its address reads pairing.service and pairing.peer, not 'pairing.spanId'");
        assertCompileFails("filter { extractor {\n"
                + "  buildNetworkAlias pairing.peer.length\n"
                + "} }",
            "its address reads pairing.service and pairing.peer");
    }

    @Test
    void rejectASecondBuildNetworkAlias() {
        assertCompileFails("filter { extractor {\n"
                + "  buildNetworkAlias callerScopedAddress(pairing.service, pairing.peer)\n"
                + "  buildNetworkAlias pairing.peer\n"
                + "} }",
            "a rule declares buildNetworkAlias once");
    }

    @Test
    void rejectAStatementAfterAbort() {
        assertCompileFails("filter { extractor {\n  abort {}\n  layer \"OTLP_GENERAL\"\n} }",
            "abort {} ends its block");
    }

    @Test
    void rejectRanksAndBucketsThatCannotBeRead() {
        assertCompileFails(percentile("buckets [10, 20]; ranks [99, 50]"), "ranks that are not ascending");
        assertCompileFails(percentile("buckets [10, 20]; ranks [0, 50]"), "a rank outside 1..100");
        assertCompileFails(percentile("buckets [10, 20]; ranks [50, 150]"), "a rank outside 1..100");
        assertCompileFails(percentile("buckets [1.5, 20]"), "not a whole number");
    }

    @Test
    void rejectOneMetricWithTwoBucketLayouts() {
        final TALClassGenerator generator = generator("two_layouts");
        final Exception e = assertThrows(Exception.class, () -> generator.compile(
            "filter { extractor {\n"
                + "  def svc = service(meta.service, meta.layer)\n"
                + "  if (meta.root) {\n"
                + "    metrics { name \"h\"; scope svc; function percentile; value meta.latency; buckets [0, 10] }\n"
                + "  } else {\n"
                + "    metrics { name \"h\"; scope svc; function percentile; value meta.latency; buckets [0, 50] }\n"
                + "  }\n"
                + "} }"));
        assertTrue(rootMessage(e).contains("declared twice with different shapes"), rootMessage(e));
    }

    @Test
    void rejectADefReassignedAnotherType() {
        assertCompileFails("filter { extractor {\n  def x = \"a\"\n  def x = 1\n} }",
            "def x holds a String and is reassigned a Integer");
    }

    @Test
    void rejectALabelLimitOutsideALabeledMetric() {
        assertCompileFails("filter { extractor {\n"
                + "  def svc = service(meta.service, meta.layer)\n"
                + "  metrics { name \"c\"; scope svc; function cpm; value 1; labelLimit 10 }\n"
                + "} }",
            "labelLimit belongs to the labeled functions only");
    }

    @Test
    void aLabeledMetricIsLimitedByDefault() throws Exception {
        final TALClassGenerator generator = generator("label_limit");
        generator.compile("filter { extractor {\n"
            + "  def svc = service(meta.service, meta.layer)\n"
            + "  metrics { name \"by_status\"; scope svc; function sumLabeled; labels s: attr(\"s\"); value 1 }\n"
            + "  metrics { name \"by_route\"; scope svc; function sumLabeled; labels r: attr(\"r\"); value 1;"
            + " labelLimit 200 }\n"
            + "} }");
        assertEquals(50, generator.getMetricDeclarations().get(0).labelLimit, "OAL's labelCount default");
        assertEquals(200, generator.getMetricDeclarations().get(1).labelLimit);
    }

    @Test
    void rejectComparingValuesOfDifferentTypes() {
        assertCompileFails("filter { if (attr(\"code\") > 400) { abort {} } }",
            "cannot compare a String with a number");
        assertCompileFails("filter { if (meta.latency == \"5\") { abort {} } }",
            "cannot compare a number with a String");
        assertCompileFails("filter { if (meta.root == 1) { abort {} } }",
            "cannot compare a boolean with a number");
    }

    @Test
    void rejectOrderingValuesThatAreNotNumbers() {
        assertCompileFails("filter { if (attr(\"a\") > attr(\"b\")) { abort {} } }",
            "only numbers are ordered");
        assertCompileFails("filter { if (meta.root >= true) { abort {} } }",
            "only numbers are ordered");
    }

    @Test
    void rejectComparingAValueThatIsNeverAbsentWithNull() {
        assertCompileFails("filter { if (meta.latency == null) { abort {} } }",
            "only a value that can be absent compares with null");
    }

    @Test
    void rejectAVoteStatementOutsideASinkOrPersistBlock() {
        assertCompileFails("filter { if (meta.root) { enforcer {} } }",
            "enforcer {} belongs inside a sink {} or a persist {} block");
        assertCompileFails("filter { extractor { if (meta.root) { dropper {} } } }",
            "dropper {} belongs inside a sink {} or a persist {} block");
    }

    @Test
    void aMetricDeclaredWithAnotherShapeByAnotherRuleFails() throws Exception {
        final TALClassGenerator first = generator("shape_a");
        first.compile(percentile("buckets [10, 20]; ranks [50]"));
        final TALClassGenerator second = generator("shape_b");
        second.compile(percentile("buckets [10, 30]; ranks [50]"));
        final TALClassGenerator same = generator("shape_c");
        same.compile(percentile("buckets [10, 20]; ranks [50]"));

        final TALMetricShapes shapes = new TALMetricShapes();
        shapes.claim("a.yaml#a", second.getMetricDeclarations());
        // A rule loaded again replaces its own claim.
        shapes.claim("a.yaml#a", first.getMetricDeclarations());
        shapes.claim("c.yaml#c", same.getMetricDeclarations());
        final IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
            () -> shapes.claim("b.yaml#b", second.getMetricDeclarations()));
        assertTrue(conflict.getMessage().contains("otlp_p"), conflict.getMessage());
    }

    private static String percentile(final String clauses) {
        return "filter { extractor {\n"
            + "  def svc = service(meta.service, meta.layer)\n"
            + "  metrics { name \"p\"; scope svc; function percentile; value meta.latency; " + clauses + " }\n"
            + "} }";
    }

    /**
     * An output type with the one field the rules above write.
     */
    public static class StubRecord implements TALOutputBuilder {
        @Setter
        private String statement;

        @Override
        public String name() {
            return "Stub";
        }

        @Override
        public void init(final SpanMetadata meta, final Object input, final ModuleManager moduleManager) {
        }

        @Override
        public void complete(final SourceReceiver sourceReceiver) {
        }

        @Override
        public String outputToJson() {
            return "{}";
        }
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
