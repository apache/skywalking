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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Condition, safe navigation, tag function, if-block, and else-if chain tests.
 */
class LALClassGeneratorConditionTest extends LALClassGeneratorTestBase {

    // ==================== tag() function in conditions ====================

    @Test
    void compileTagFunctionInCondition() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  if (tag(\"LOG_KIND\") == \"SLOW_SQL\") {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}");
    }

    @Test
    void compileAndVerifyTagFunctionEmitsTagValue() throws Exception {
        final String dsl = "filter {\n"
            + "  if (tag(\"LOG_KIND\") == \"SLOW_SQL\") {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.tagValue(\"LOG_KIND\")"),
            "Expected tagValue call but got: " + source);
        assertTrue(source.contains("SLOW_SQL"));
    }

    @Test
    void compileTagFunctionNestedInExtractor() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    if (tag(\"LOG_KIND\") == \"NET_PROFILING_SAMPLED_TRACE\") {\n"
            + "      service parsed.service as String\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
    }

    // ==================== Safe navigation ====================

    @Test
    void compileSafeNavigationFieldAccess() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    service parsed?.response?.service as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
    }

    @Test
    void compileSafeNavigationMethodCalls() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  json {}\n"
            + "  extractor {\n"
            + "    service parsed?.flags?.toString()?.trim() as String\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
    }

    @Test
    void compileAndVerifySafeNavMethodEmitsSpecificHelper() throws Exception {
        final String dsl = "filter {\n"
            + "  json {}\n"
            + "  if (parsed?.flags?.toString()) {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.toString("),
            "Expected toString helper for safe nav method but got: " + source);
        assertTrue(source.contains("h.isNotEmpty("),
            "Expected isNotEmpty for ExprCondition but got: " + source);
    }

    // ==================== If blocks in extractor/sink ====================

    @Test
    void compileIfInsideExtractor() throws Exception {
        compileAndAssert(
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
    }

    @Test
    void compileIfInsideExtractorWithTagCondition() throws Exception {
        compileAndAssert(
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
    }

    // ==================== Numeric equality/inequality ====================

    @Test
    void compileNeqNumericEmitsNumericOp() throws Exception {
        final String dsl = "filter {\n"
            + "  json {}\n"
            + "  if (parsed?.code as Integer != 403) {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("!= 403L"),
            "Expected numeric != but got: " + source);
    }

    @Test
    void compileEqNumericEmitsNumericOp() throws Exception {
        final String dsl = "filter {\n"
            + "  json {}\n"
            + "  if (parsed?.code as Integer == 200) {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("== 200L"),
            "Expected numeric == but got: " + source);
    }

    @Test
    void compileEqStringUsesObjectsEquals() throws Exception {
        final String dsl = "filter {\n"
            + "  json {}\n"
            + "  if (parsed?.status == \"OK\") {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("java.util.Objects.equals("),
            "Expected Objects.equals for string comparison but got: " + source);
    }

    @Test
    void compileNeqStringUsesObjectsEquals() throws Exception {
        final String dsl = "filter {\n"
            + "  json {}\n"
            + "  if (parsed?.status != \"ERROR\") {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("!java.util.Objects.equals("),
            "Expected !Objects.equals for string != but got: " + source);
    }

    @Test
    void compileNeqNumericWithLogicalAnd() throws Exception {
        // Matches the envoy-als pattern that triggered the bug
        final String dsl = "filter {\n"
            + "  json {}\n"
            + "  if (parsed?.code as Integer != 401"
            + " && parsed?.code as Integer != 403) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("!= 401L"),
            "Expected numeric != 401L but got: " + source);
        assertTrue(source.contains("!= 403L"),
            "Expected numeric != 403L but got: " + source);
    }

    @Test
    void compileEqNullUsesObjectsEquals() throws Exception {
        final String dsl = "filter {\n"
            + "  json {}\n"
            + "  if (parsed?.status == null) {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("java.util.Objects.equals("),
            "Expected Objects.equals for null comparison but got: " + source);
    }

    @Test
    void compileNeqNullUsesObjectsEquals() throws Exception {
        final String dsl = "filter {\n"
            + "  json {}\n"
            + "  if (parsed?.status != null) {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("!java.util.Objects.equals("),
            "Expected !Objects.equals for null != but got: " + source);
    }

    // ==================== Else-if chain ====================

    @Test
    void compileElseIfChain() throws Exception {
        compileAndAssert(
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
    }

    @Test
    void compileAndVerifyElseIfEmitsNestedBranches() throws Exception {
        final String dsl = "filter {\n"
            + "  json {}\n"
            + "  if (parsed.a) {\n"
            + "    sink {}\n"
            + "  } else if (parsed.b) {\n"
            + "    sink {}\n"
            + "  } else {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("else"),
            "Expected else branch but got: " + source);
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

    // ==================== Arithmetic addition in conditions ====================

    @Test
    void compileArithmeticSumOfIntegerTagsEmitsLongArithmetic() throws Exception {
        // The envoy-ai-gateway token check pattern:
        // (tag("input_tokens") as Integer) + (tag("output_tokens") as Integer) < 10000
        // must do numeric addition (3033 < 10000 = true → abort),
        // not string concat ("2872161" → 2872161 >= 10000 = false → no abort).
        final String dsl = "filter {\n"
            + "  if ((tag(\"a\") as Integer) + (tag(\"b\") as Integer) < 10000) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("(long)"),
            "Expected (long) promotion for Integer parts but got: " + source);
        assertTrue(source.contains("h.toInt("),
            "Expected h.toInt() for Integer cast but got: " + source);
        assertFalse(source.contains("\"\" +"),
            "Expected arithmetic addition, not string concat, but got: " + source);
        // In a comparison context generateNumericComparison uses lastRawChain directly,
        // so the comparison emits the raw long expression without Long.valueOf().
        assertTrue(source.contains("< 10000L"),
            "Expected '< 10000L' numeric comparison but got: " + source);
    }

    @Test
    void compileArithmeticSumOfLongAndIntegerTagsEmitsLongArithmetic() throws Exception {
        final String dsl = "filter {\n"
            + "  if ((tag(\"a\") as Long) + (tag(\"b\") as Integer) < 10000) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.toLong("),
            "Expected h.toLong() for Long cast but got: " + source);
        assertTrue(source.contains("(long)"),
            "Expected (long) promotion for Integer parts but got: " + source);
        assertFalse(source.contains("\"\" +"),
            "Expected arithmetic addition, not string concat, but got: " + source);
    }

    @Test
    void compileStringConcatWithPlusRemainsStringConcat() throws Exception {
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'key': tag(\"a\") + tag(\"b\")\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("\"\" +"),
            "Expected string concatenation for uncast tags but got: " + source);
    }

    // ==================== sourceAttribute() function ====================

    @Test
    void compileSourceAttributeInCondition() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  if (sourceAttribute(\"os.name\") == \"iOS\") {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}");
    }

    @Test
    void compileAndVerifySourceAttributeEmitsSourceAttributeValue() throws Exception {
        final String dsl = "filter {\n"
            + "  if (sourceAttribute(\"os.name\") == \"iOS\") {\n"
            + "    sink {}\n"
            + "  }\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.sourceAttributeValue(\"os.name\")"),
            "Expected sourceAttributeValue call but got: " + source);
    }

    @Test
    void compileSourceAttributeInExtractorTag() throws Exception {
        compileAndAssert(
            "filter {\n"
            + "  extractor {\n"
            + "    tag 'device.model': sourceAttribute(\"device.model.identifier\")\n"
            + "  }\n"
            + "  sink {}\n"
            + "}");
    }
}
