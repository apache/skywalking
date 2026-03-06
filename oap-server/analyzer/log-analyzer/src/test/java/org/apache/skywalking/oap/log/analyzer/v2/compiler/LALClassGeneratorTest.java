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

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import javassist.ClassPool;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LALClassGeneratorTest {

    private LALClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new LALClassGenerator(new ClassPool(true));
    }

    @Test
    void compileMinimalFilter() throws Exception {
        final LalExpression expr = generator.compile(
            "filter { sink {} }");
        assertNotNull(expr);
    }

    @Test
    void compileJsonParserFilter() throws Exception {
        final LalExpression expr = generator.compile(
            "filter { json {} sink {} }");
        assertNotNull(expr);
    }

    @Test
    void compileJsonWithExtractor() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    service parsed.service as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileTextWithRegexp() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  text {\n"
            + "    regexp '(?<timestamp>\\\\d+) (?<level>\\\\w+) (?<msg>.*)'\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileSinkWithEnforcer() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  sink {\n"
            + "    enforcer {}\n"
            + "  }\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void generateSourceReturnsJavaCode() {
        final String source = generator.generateSource(
            "filter { json {} sink {} }");
        assertNotNull(source);
        org.junit.jupiter.api.Assertions.assertTrue(
            source.contains("filterSpec.json(ctx)"));
        org.junit.jupiter.api.Assertions.assertTrue(
            source.contains("filterSpec.sink(ctx)"));
    }

    // ==================== Error handling tests ====================

    @Test
    void emptyScriptThrows() {
        // Demo error: LAL script parsing failed: 1:0 mismatched input '<EOF>'
        //   expecting 'filter'
        assertThrows(Exception.class, () -> generator.compile(""));
    }

    @Test
    void missingFilterKeywordThrows() {
        // Demo error: LAL script parsing failed: 1:0 extraneous input 'json'
        //   expecting 'filter'
        assertThrows(Exception.class, () -> generator.compile("json {}"));
    }

    @Test
    void unclosedBraceThrows() {
        // Demo error: LAL script parsing failed: 1:15 mismatched input '<EOF>'
        //   expecting '}'
        assertThrows(Exception.class,
            () -> generator.compile("filter { json {"));
    }

    @Test
    void invalidStatementInFilterThrows() {
        // Demo error: LAL script parsing failed: 1:9 extraneous input 'invalid'
        //   expecting {'text', 'json', 'yaml', 'extractor', 'sink', 'abort', 'if', '}'}
        assertThrows(Exception.class,
            () -> generator.compile("filter { invalid {} }"));
    }

    // ==================== tag() function in conditions ====================

    @Test
    void compileTagFunctionInCondition() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  if (tag(\"LOG_KIND\") == \"SLOW_SQL\") {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void generateSourceTagFunctionEmitsTagValue() {
        final String source = generator.generateSource(
            "filter {\n"
            + "  if (tag(\"LOG_KIND\") == \"SLOW_SQL\") {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}");
        // Should use tagValue helper, not emit null
        assertTrue(source.contains("h.tagValue(\"LOG_KIND\")"),
            "Expected tagValue call but got: " + source);
        assertTrue(source.contains("SLOW_SQL"));
    }

    @Test
    void compileTagFunctionNestedInExtractor() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    if (tag(\"LOG_KIND\") == \"NET_PROFILING_SAMPLED_TRACE\") {\n"
            + "      service parsed.service as String\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    // ==================== Safe navigation ====================

    @Test
    void compileSafeNavigationFieldAccess() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    service parsed?.response?.service as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileSafeNavigationMethodCalls() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    service parsed?.flags?.toString()?.trim() as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void generateSourceSafeNavMethodEmitsSpecificHelper() {
        final String source = generator.generateSource(
            "filter {\n"
            + "  json {}\n"
            + "  if (parsed?.flags?.toString()) {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}");
        // Safe method calls should emit specific helpers, not generic safeCall
        assertTrue(source.contains("h.toString("),
            "Expected toString helper for safe nav method but got: " + source);
        assertTrue(source.contains("h.isNotEmpty("),
            "Expected isNotEmpty for ExprCondition but got: " + source);
    }

    // ==================== ProcessRegistry static calls ====================

    @Test
    void compileProcessRegistryCall() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    service ProcessRegistry.generateVirtualLocalProcess("
            + "parsed.service as String, parsed.serviceInstance as String"
            + ") as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileProcessRegistryWithThreeArgs() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    service ProcessRegistry.generateVirtualRemoteProcess("
            + "parsed.service as String, parsed.serviceInstance as String, "
            + "parsed.address as String) as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    // ==================== Metrics block ====================

    @Test
    void compileMetricsBlock() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    metrics {\n"
            + "      timestamp log.timestamp as Long\n"
            + "      labels level: parsed.level, service: log.service\n"
            + "      name \"nginx_error_log_count\"\n"
            + "      value 1\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    // ==================== SlowSql block ====================

    @Test
    void compileSlowSqlBlock() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    slowSql {\n"
            + "      id parsed.id as String\n"
            + "      statement parsed.statement as String\n"
            + "      latency parsed.query_time as Long\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    // ==================== SampledTrace block ====================

    @Test
    void compileSampledTraceBlock() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    sampledTrace {\n"
            + "      latency parsed.latency as Long\n"
            + "      uri parsed.uri as String\n"
            + "      reason parsed.reason as String\n"
            + "      detectPoint parsed.detect_point as String\n"
            + "      componentId 49\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileSampledTraceWithIfBlocks() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    sampledTrace {\n"
            + "      latency parsed.latency as Long\n"
            + "      if (parsed.client_process.process_id as String != \"\") {\n"
            + "        processId parsed.client_process.process_id as String\n"
            + "      } else {\n"
            + "        processId parsed.fallback as String\n"
            + "      }\n"
            + "      detectPoint parsed.detect_point as String\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    // ==================== Sampler / rateLimit ====================

    @Test
    void compileSamplerWithRateLimit() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  sink {\n"
            + "    sampler {\n"
            + "      rateLimit('service:error') {\n"
            + "        rpm 6000\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileSamplerWithInterpolatedId() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  sink {\n"
            + "    sampler {\n"
            + "      rateLimit(\"${log.service}:${parsed.code}\") {\n"
            + "        rpm 6000\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void parseInterpolatedIdParts() {
        // Verify the parser correctly splits interpolated strings
        final java.util.List<LALScriptModel.InterpolationPart> parts =
            LALScriptParser.parseInterpolation(
                "${log.service}:${parsed.code}");
        assertNotNull(parts);
        // expr, literal ":", expr
        assertEquals(3, parts.size());
        assertFalse(parts.get(0).isLiteral());
        assertTrue(parts.get(0).getExpression().isLogRef());
        assertTrue(parts.get(1).isLiteral());
        assertEquals(":", parts.get(1).getLiteral());
        assertFalse(parts.get(2).isLiteral());
        assertTrue(parts.get(2).getExpression().isParsedRef());
    }

    @Test
    void compileSamplerWithSafeNavInterpolatedId() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  sink {\n"
            + "    sampler {\n"
            + "      rateLimit(\"${log.service}:${parsed?.commonProperties?.responseFlags?.toString()}\") {\n"
            + "        rpm 6000\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileSamplerWithIfAndRateLimit() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  sink {\n"
            + "    sampler {\n"
            + "      if (parsed?.error) {\n"
            + "        rateLimit('svc:err') {\n"
            + "          rpm 6000\n"
            + "        }\n"
            + "      } else {\n"
            + "        rateLimit('svc:ok') {\n"
            + "          rpm 3000\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}");
        assertNotNull(expr);
    }

    // ==================== If blocks in extractor/sink ====================

    @Test
    void compileIfInsideExtractor() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    if (parsed?.status) {\n"
            + "      tag 'http.status_code': parsed.status\n"
            + "    }\n"
            + "    tag 'response.flag': parsed.flags\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileIfInsideExtractorWithTagCondition() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    if (tag(\"LOG_KIND\") == \"NET_PROFILING\") {\n"
            + "      service parsed.service as String\n"
            + "      layer parsed.layer as String\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    // ==================== Complex production-like rules ====================

    @Test
    void compileNginxAccessLogRule() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  if (tag(\"LOG_KIND\") == \"NGINX_ACCESS_LOG\") {\n"
            + "    text {\n"
            + "      regexp '.+\"(?<request>.+)\"(?<status>\\\\d{3}).+'\n"
            + "    }\n"
            + "    extractor {\n"
            + "      if (parsed.status) {\n"
            + "        tag 'http.status_code': parsed.status\n"
            + "      }\n"
            + "    }\n"
            + "    sink {}\n"
            + "  }\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileSlowSqlProductionRule() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    if (tag(\"LOG_KIND\") == \"SLOW_SQL\") {\n"
            + "      layer parsed.layer as String\n"
            + "      service parsed.service as String\n"
            + "      timestamp parsed.time as String\n"
            + "      slowSql {\n"
            + "        id parsed.id as String\n"
            + "        statement parsed.statement as String\n"
            + "        latency parsed.query_time as Long\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileEnvoyAlsAbortRuleFailsWithoutExtraLogType() {
        // envoy-als pattern has no parser (json/yaml/text) — falls back to LogData
        // but LogData.Builder doesn't have getResponse(), so compile fails
        assertThrows(IllegalArgumentException.class, () -> generator.compile(
            "filter {\n"
            + "  if (parsed?.response?.responseCode?.value as Integer < 400"
            + " && !parsed?.commonProperties?.responseFlags?.toString()?.trim()) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  extractor {\n"
            + "    if (parsed?.response?.responseCode) {\n"
            + "      tag 'status.code': parsed?.response?.responseCode?.value\n"
            + "    }\n"
            + "    tag 'response.flag': parsed?.commonProperties?.responseFlags\n"
            + "  }\n"
            + "  sink {}\n"
            + "}"));
    }

    @Test
    void compileNoParserFallsBackToLogDataProto() throws Exception {
        // No parser (json/yaml/text) and no extraLogType — should use LogData.Builder
        final String dsl =
            "filter {\n"
            + "  extractor {\n"
            + "    service parsed.service as String\n"
            + "    instance parsed.serviceInstance as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        final String source = generator.generateSource(dsl);
        // Should generate getter chains on h.ctx().log()
        assertTrue(source.contains("h.ctx().log().getService()"),
            "Expected h.ctx().log().getService() but got: " + source);
        assertTrue(source.contains("h.ctx().log().getServiceInstance()"),
            "Expected h.ctx().log().getServiceInstance() but got: " + source);
        // No _p variable (LogData doesn't need it)
        assertFalse(source.contains("_p"),
            "Should NOT have _p variable for LogData fallback but got: " + source);
        // Verify it compiles
        final LalExpression expr = generator.compile(dsl);
        assertNotNull(expr);
    }

    @Test
    void compileExtraLogTypeGeneratesDirectGetterCalls() throws Exception {
        generator.setExtraLogType(
            io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry.class);
        final String dsl =
            "filter {\n"
            + "  if (parsed?.response?.responseCode?.value as Integer < 400"
            + " && !parsed?.commonProperties?.responseFlags?.toString()?.trim()) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  extractor {\n"
            + "    if (parsed?.response?.responseCode) {\n"
            + "      tag 'status.code': parsed?.response?.responseCode?.value\n"
            + "    }\n"
            + "    tag 'response.flag': parsed?.commonProperties?.responseFlags\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        final String source = generator.generateSource(dsl);
        final String fqcn =
            "io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry";
        // Proto field access uses _p local variable, not inline cast
        assertTrue(source.contains(
            fqcn + " _p = (" + fqcn + ") h.ctx().extraLog()"),
            "Expected _p local variable for extraLog cast but got: " + source);
        // Intermediate fields cached in _tN local variables
        assertTrue(source.contains("_p.getResponse()"),
            "Expected _p.getResponse() via cached variable but got: " + source);
        assertTrue(source.contains("_p.getCommonProperties()"),
            "Expected _p.getCommonProperties() via cached variable but got: " + source);
        assertFalse(source.contains("getAt"),
            "Should NOT contain getAt calls but got: " + source);
        // Safe navigation: null checks with == null on local variables
        assertTrue(source.contains("_p == null ? null :"),
            "Expected null checks for ?. safe navigation but got: " + source);
        // Dedup: _tN variables declared once and reused
        assertTrue(source.contains("_t0") && source.contains("_t1"),
            "Expected _tN local variables for chain dedup but got: " + source);
        // Numeric comparison: direct primitive via _tN variable, no h.toLong()
        assertTrue(source.contains(".getValue() < 400"),
            "Expected direct primitive comparison without boxing but got: " + source);
        assertFalse(source.contains("h.toLong"),
            "Should NOT use h.toLong for primitive int comparison but got: " + source);
        // Single-tag: uses tag(ctx, String, String), not singletonMap
        assertTrue(source.contains("_e.tag(h.ctx(), \"status.code\""),
            "Expected tag(ctx, String, String) overload but got: " + source);
        assertFalse(source.contains("singletonMap"),
            "Should NOT use singletonMap for single tags but got: " + source);

        // Verify it compiles
        final LalExpression expr = generator.compile(dsl);
        assertNotNull(expr);
    }

    // ==================== Else-if chain ====================

    @Test
    void compileElseIfChain() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  if (parsed.a) {\n"
            + "    sink {}\n"
            + "  } else if (parsed.b) {\n"
            + "    sink {}\n"
            + "  } else if (parsed.c) {\n"
            + "    sink {}\n"
            + "  } else {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void compileElseIfInSampledTrace() throws Exception {
        final LalExpression expr = generator.compile(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    sampledTrace {\n"
            + "      latency parsed.latency as Long\n"
            + "      if (parsed.client_process.process_id as String != \"\") {\n"
            + "        processId parsed.client_process.process_id as String\n"
            + "      } else if (parsed.client_process.local as Boolean) {\n"
            + "        processId ProcessRegistry.generateVirtualLocalProcess("
            + "parsed.service as String, parsed.serviceInstance as String) as String\n"
            + "      } else {\n"
            + "        processId ProcessRegistry.generateVirtualRemoteProcess("
            + "parsed.service as String, parsed.serviceInstance as String, "
            + "parsed.client_process.address as String) as String\n"
            + "      }\n"
            + "      detectPoint parsed.detect_point as String\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
        assertNotNull(expr);
    }

    @Test
    void generateSourceElseIfEmitsNestedBranches() {
        final String source = generator.generateSource(
            "filter {\n"
            + "  json {}\n"
            + "  if (parsed.a) {\n"
            + "    sink {}\n"
            + "  } else if (parsed.b) {\n"
            + "    sink {}\n"
            + "  } else {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}");
        // The else-if should produce a nested if inside else
        assertTrue(source.contains("else"),
            "Expected else branch but got: " + source);
        // Both condition branches should appear
        int ifCount = 0;
        for (int i = 0; i < source.length() - 2; i++) {
            if (source.substring(i, i + 3).equals("if ")) {
                ifCount++;
            }
        }
        assertTrue(ifCount >= 2,
            "Expected at least 2 if-conditions for else-if chain but got "
            + ifCount + " in: " + source);
    }
}
