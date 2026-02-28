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

package org.apache.skywalking.oap.server.transpiler.lal;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LalToJavaTranspilerTest {

    private LalToJavaTranspiler transpiler;

    @BeforeEach
    void setUp() {
        transpiler = new LalToJavaTranspiler();
    }

    // ---- Class Structure ----

    @Test
    void classStructure() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter { json {} }");
        assertNotNull(java);

        assertTrue(java.contains("package " + LalToJavaTranspiler.GENERATED_PACKAGE),
            "Should have correct package");
        assertTrue(java.contains("public class LalExpr_test implements LalExpression"),
            "Should implement LalExpression");
        assertTrue(java.contains("public void execute(FilterSpec filterSpec, Binding binding)"),
            "Should have execute method");
    }

    @Test
    void helperMethodsPresent() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter { json {} }");
        assertNotNull(java);

        assertTrue(java.contains("private static Object getAt(Object obj, String key)"),
            "Should have getAt helper");
        assertTrue(java.contains("private static long toLong(Object obj)"),
            "Should have toLong helper");
        assertTrue(java.contains("private static int toInt(Object obj)"),
            "Should have toInt helper");
        assertTrue(java.contains("private static boolean toBoolean(Object obj)"),
            "Should have toBoolean helper");
        assertTrue(java.contains("private static boolean isTruthy(Object obj)"),
            "Should have isTruthy helper");
        assertTrue(java.contains("private static boolean isNonEmptyString(Object obj)"),
            "Should have isNonEmptyString helper");
    }

    @Test
    void importsPresent() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter { json {} }");
        assertNotNull(java);

        assertTrue(java.contains("import org.apache.skywalking.oap.log.analyzer.dsl.Binding;"),
            "Should import Binding");
        assertTrue(java.contains("import org.apache.skywalking.oap.log.analyzer.dsl.LalExpression;"),
            "Should import LalExpression");
        assertTrue(java.contains("import org.apache.skywalking.oap.log.analyzer.dsl.spec.filter.FilterSpec;"),
            "Should import FilterSpec");
    }

    // ---- filter {} Unwrapping ----

    @Test
    void filterUnwrap() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter { json {} }");
        assertNotNull(java);

        assertTrue(java.contains("filterSpec.json()"),
            "Should unwrap filter block and call json() on filterSpec");
        assertTrue(!java.contains("filterSpec.filter("),
            "Should NOT emit filterSpec.filter() call");
    }

    // ---- json {} ----

    @Test
    void jsonEmptyBlock() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter { json {} }");
        assertNotNull(java);

        assertTrue(java.contains("filterSpec.json();"),
            "Should emit no-arg json() call");
    }

    // ---- text { regexp $/pattern/$ } ----

    @Test
    void textWithRegexp() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter { text { regexp $/(?<timestamp>\\d+)\\s+(?<service>.+)/$ } }");
        assertNotNull(java);

        assertTrue(java.contains("filterSpec.text(tp -> {"),
            "Should emit text with Consumer lambda");
        assertTrue(java.contains("tp.regexp("),
            "Should call regexp on text parser spec");
    }

    // ---- extractor { ... } ----

    @Test
    void extractorWithService() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor {\n" +
            "    service parsed.service as String\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("filterSpec.extractor(ext -> {"),
            "Should emit extractor with Consumer lambda");
        assertTrue(java.contains("ext.service("),
            "Should call service on extractor spec");
        assertTrue(java.contains("String.valueOf("),
            "Should use String.valueOf for 'as String' cast");
        assertTrue(java.contains("getAt(binding.parsed(), \"service\")"),
            "Should use getAt for parsed.service");
    }

    @Test
    void extractorWithMultipleFields() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor {\n" +
            "    service parsed.service as String\n" +
            "    instance parsed.instance as String\n" +
            "    endpoint parsed.endpoint as String\n" +
            "    layer parsed.layer as String\n" +
            "    timestamp parsed.time as String\n" +
            "    traceId parsed.traceId as String\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("ext.service(String.valueOf(getAt(binding.parsed(), \"service\")))"),
            "Should extract service");
        assertTrue(java.contains("ext.instance(String.valueOf(getAt(binding.parsed(), \"instance\")))"),
            "Should extract instance");
        assertTrue(java.contains("ext.endpoint(String.valueOf(getAt(binding.parsed(), \"endpoint\")))"),
            "Should extract endpoint");
        assertTrue(java.contains("ext.layer(String.valueOf(getAt(binding.parsed(), \"layer\")))"),
            "Should extract layer");
        assertTrue(java.contains("ext.timestamp(String.valueOf(getAt(binding.parsed(), \"time\")))"),
            "Should extract timestamp");
        assertTrue(java.contains("ext.traceId(String.valueOf(getAt(binding.parsed(), \"traceId\")))"),
            "Should extract traceId");
    }

    // ---- sink { ... } ----

    @Test
    void sinkWithEnforcer() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  sink {\n" +
            "    enforcer {}\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("filterSpec.sink(s -> {"),
            "Should emit sink with Consumer lambda");
        assertTrue(java.contains("s.enforcer();"),
            "Should emit no-arg enforcer() on sink spec");
    }

    @Test
    void sinkWithDropper() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  sink {\n" +
            "    dropper {}\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("s.dropper();"),
            "Should emit no-arg dropper() on sink spec");
    }

    @Test
    void sinkWithSampler() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  sink {\n" +
            "    sampler {\n" +
            "      rateLimit('abc')\n" +
            "    }\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("s.sampler(sp -> {"),
            "Should emit sampler with Consumer lambda");
        assertTrue(java.contains("sp.rateLimit(\"abc\")"),
            "Should call rateLimit on sampler spec");
    }

    // ---- abort {} ----

    @Test
    void abortBlock() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  abort {}\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("filterSpec.abort();"),
            "Should emit no-arg abort() call");
    }

    // ---- parsed access ----

    @Test
    void parsedPropertyAccess() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor { service parsed.service as String }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("binding.parsed()"),
            "Should translate 'parsed' to binding.parsed()");
        assertTrue(java.contains("getAt(binding.parsed(), \"service\")"),
            "Should use getAt for property access");
    }

    @Test
    void parsedNestedAccess() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor { service parsed.data.serviceName as String }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("getAt(getAt(binding.parsed(), \"data\"), \"serviceName\")"),
            "Should translate nested parsed access to nested getAt()");
    }

    // ---- Safe navigation (?.) ----

    @Test
    void safeNavigationOnParsed() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor { service parsed?.service as String }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("== null ? null : getAt("),
            "Should translate ?. to null-safe ternary with getAt");
    }

    // ---- as Cast ----

    @Test
    void castAsString() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor { service parsed.service as String }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("String.valueOf("),
            "Should translate 'as String' to String.valueOf()");
    }

    @Test
    void castAsLong() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor { timestamp parsed.time as Long }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("toLong("),
            "Should translate 'as Long' to toLong()");
    }

    // ---- log access ----

    @Test
    void logPropertyAccess() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor { service log.service }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("binding.log().getService()"),
            "Should translate log.service to binding.log().getService()");
    }

    // ---- if/else ----

    @Test
    void ifStatement() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  if (parsed.level == 'ERROR') {\n" +
            "    abort {}\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("if (\"ERROR\".equals(getAt(binding.parsed(), \"level\"))"),
            "Should translate == with constant on left for null-safety");
        assertTrue(java.contains("filterSpec.abort()"),
            "Should emit abort in if body");
    }

    @Test
    void ifElseStatement() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  if (parsed.type == 'access') {\n" +
            "    extractor { layer 'HTTP' }\n" +
            "  } else {\n" +
            "    extractor { layer 'GENERAL' }\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("if (\"access\".equals(getAt(binding.parsed(), \"type\"))"),
            "Should have if condition");
        assertTrue(java.contains("} else {"),
            "Should have else block");
    }

    @Test
    void ifElseIfStatement() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  if (parsed.type == 'a') {\n" +
            "    extractor { layer 'A' }\n" +
            "  } else if (parsed.type == 'b') {\n" +
            "    extractor { layer 'B' }\n" +
            "  } else {\n" +
            "    extractor { layer 'C' }\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("} else if ("),
            "Should produce else-if chain");
    }

    // ---- Condition operators ----

    @Test
    void notEqualCondition() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  if (parsed.status != 'ok') { abort {} }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("!\"ok\".equals(getAt(binding.parsed(), \"status\"))"),
            "Should translate != with negated .equals()");
    }

    @Test
    void logicalAndCondition() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  if (parsed.a == 'x' && parsed.b == 'y') { abort {} }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("\"x\".equals(getAt(binding.parsed(), \"a\")) && \"y\".equals(getAt(binding.parsed(), \"b\"))"),
            "Should translate && with .equals() on both sides");
    }

    @Test
    void logicalOrCondition() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  if (parsed.a == 'x' || parsed.a == 'y') { abort {} }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("\"x\".equals(") && java.contains("|| \"y\".equals("),
            "Should translate || correctly");
    }

    @Test
    void truthinessCondition() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  if (parsed.value) { abort {} }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("isTruthy(getAt(binding.parsed(), \"value\"))"),
            "Should translate bare expression to isTruthy()");
    }

    @Test
    void negationCondition() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  if (!parsed.value) { abort {} }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("!isTruthy(getAt(binding.parsed(), \"value\"))"),
            "Should translate !expr to negated isTruthy()");
    }

    @Test
    void lessThanCondition() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  if (parsed.code < 400) { abort {} }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("toInt(getAt(binding.parsed(), \"code\")) < 400"),
            "Should translate < with toInt on left");
    }

    @Test
    void greaterThanEqualCondition() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  if (parsed.code >= 500) { abort {} }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("toInt(getAt(binding.parsed(), \"code\")) >= 500"),
            "Should translate >= with toInt on left");
    }

    // ---- GString interpolation ----

    @Test
    void gstringInterpolation() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor { service \"svc::${parsed.name}\" as String }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("\"svc::\""),
            "Should have string prefix");
        assertTrue(java.contains("getAt(binding.parsed(), \"name\")"),
            "Should have parsed access in interpolation");
    }

    // ---- tag(Map) ----

    @Test
    void tagWithMapLiteral() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor {\n" +
            "    tag(status: parsed.status as String)\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("ext.tag("),
            "Should call tag on extractor spec");
    }

    // ---- slowSql / sampledTrace / metrics ----

    @Test
    void slowSqlBlock() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor {\n" +
            "    layer 'MYSQL'\n" +
            "    service parsed.service as String\n" +
            "    slowSql {\n" +
            "      id parsed.id as String\n" +
            "      statement parsed.statement as String\n" +
            "      latency parsed.latency as Long\n" +
            "    }\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("ext.slowSql(sql -> {"),
            "Should emit slowSql with Consumer lambda, var 'sql'");
        assertTrue(java.contains("sql.id("),
            "Should call id on slowSql spec");
        assertTrue(java.contains("sql.statement("),
            "Should call statement on slowSql spec");
        assertTrue(java.contains("sql.latency(toLong("),
            "Should call latency with toLong");
    }

    @Test
    void sampledTraceBlock() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor {\n" +
            "    sampledTrace {\n" +
            "      uri parsed.uri as String\n" +
            "      latency parsed.latency as Long\n" +
            "    }\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("ext.sampledTrace(st -> {"),
            "Should emit sampledTrace with Consumer lambda, var 'st'");
        assertTrue(java.contains("st.uri("),
            "Should call uri on sampledTrace spec");
    }

    @Test
    void metricsBlock() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor {\n" +
            "    metrics {\n" +
            "      name 'log_count'\n" +
            "      value 1\n" +
            "    }\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("ext.metrics(m -> {"),
            "Should emit metrics with Consumer lambda, var 'm'");
        assertTrue(java.contains("m.name(\"log_count\")"),
            "Should call name on metrics spec");
        assertTrue(java.contains("m.value(1)"),
            "Should call value on metrics spec");
    }

    // ---- Complete LAL Script ----

    @Test
    void completeScript() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  extractor {\n" +
            "    service parsed.service as String\n" +
            "    instance parsed.instance as String\n" +
            "    layer parsed.layer as String\n" +
            "    timestamp parsed.time as String\n" +
            "  }\n" +
            "  sink {\n" +
            "    enforcer {}\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("filterSpec.json();"),
            "Should have json() call");
        assertTrue(java.contains("filterSpec.extractor(ext -> {"),
            "Should have extractor block");
        assertTrue(java.contains("filterSpec.sink(s -> {"),
            "Should have sink block");
        assertTrue(java.contains("s.enforcer();"),
            "Should have enforcer in sink");
    }

    // ---- tag("KEY") as value ----

    @Test
    void tagAsValue() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  if (tag('status') == 'error') { abort {} }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("filterSpec.tag(\"status\")"),
            "Should call tag on filterSpec");
    }

    // ---- rateLimit ----

    @Test
    void rateLimitWithClosureArg() {
        final String java = transpiler.transpile("LalExpr_test",
            "filter {\n" +
            "  json {}\n" +
            "  sink {\n" +
            "    sampler {\n" +
            "      rateLimit('myId') { rpm 5 }\n" +
            "    }\n" +
            "  }\n" +
            "}");
        assertNotNull(java);

        assertTrue(java.contains("sp.rateLimit(\"myId\", rls -> {"),
            "Should emit rateLimit with id and closure lambda");
        assertTrue(java.contains("rls.rpm(5)"),
            "Should call rpm on rate limit spec");
    }

    // ---- Manifest ----

    @Test
    void manifest(@TempDir Path tempDir) throws Exception {
        final String source = transpiler.transpile("LalExpr_a", "filter { json {} }");
        transpiler.register("LalExpr_a", "abc123hash", source);

        final File outputDir = tempDir.toFile();
        transpiler.writeManifest(outputDir);

        final File manifest = new File(outputDir, "META-INF/lal-expressions.txt");
        assertTrue(manifest.exists(), "Manifest file should exist");

        final List<String> lines = Files.readAllLines(manifest.toPath());
        assertTrue(lines.stream().anyMatch(l -> l.contains("abc123hash") &&
            l.contains(LalToJavaTranspiler.GENERATED_PACKAGE + ".LalExpr_a")),
            "Should contain hash=FQCN mapping");
    }
}
