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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        // LHS is `as Integer`, RHS literal `403` is int — comparison stays in int.
        assertTrue(source.contains("!= 403") && !source.contains("!= 403L"),
            "Expected int-form `!= 403` but got: " + source);
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
        assertTrue(source.contains("== 200") && !source.contains("== 200L"),
            "Expected int-form `== 200` but got: " + source);
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
        assertTrue(source.contains("!= 401") && !source.contains("!= 401L"),
            "Expected int-form `!= 401` but got: " + source);
        assertTrue(source.contains("!= 403") && !source.contains("!= 403L"),
            "Expected int-form `!= 403` but got: " + source);
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

    // ==================== Binary arithmetic in conditions ====================

    @Test
    void compileArithmeticSumOfIntegerTagsEmitsIntArithmetic() throws Exception {
        // envoy-ai-gateway token-sum check.  Both operands are Integer, so
        // the arithmetic stays in int space (respecting the user's declared
        // type) and the comparison literal `10000` stays an int literal.
        final String dsl = "filter {\n"
            + "  if ((tag(\"a\") as Integer) + (tag(\"b\") as Integer) < 10000) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.toInt(h.tagValue(\"a\")) + h.toInt(h.tagValue(\"b\"))"),
            "Expected unboxed int + int arithmetic but got: " + source);
        assertFalse(source.contains("(long)"),
            "Did not expect (long) widening for Integer + Integer but got: " + source);
        assertFalse(source.contains("\"\" +"),
            "Expected arithmetic addition, not string concat, but got: " + source);
        assertTrue(source.contains("< 10000"),
            "Expected '< 10000' (no L suffix) but got: " + source);
        assertFalse(source.contains("< 10000L"),
            "Expected no long-suffixed literal for int comparison but got: " + source);
    }

    @Test
    void compileArithmeticSumOfLongAndIntegerTagsWidensIntegerToLong() throws Exception {
        // Mixed Long + Integer — JLS-style promotion widens the Integer side
        // to long (via an explicit `(long)` cast on the int operand).
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
        assertTrue(source.contains("(long) h.toInt("),
            "Expected (long) widening for Integer operand but got: " + source);
        assertTrue(source.contains("< 10000L"),
            "Expected RHS literal widened to 10000L when LHS is long but got: " + source);
    }

    @Test
    void compileSubtractMultiplyDivideOnIntegerTagsStaysInInt() throws Exception {
        final String dsl = "filter {\n"
            + "  if ((tag(\"a\") as Integer) - (tag(\"b\") as Integer) > 0) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.toInt(h.tagValue(\"a\")) - h.toInt(h.tagValue(\"b\"))"),
            "Expected int subtraction but got: " + source);
    }

    @Test
    void compileMultiplyOnIntegerTagsEmitsInteger() throws Exception {
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'product': (tag(\"a\") as Integer) * (tag(\"b\") as Integer)\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("Integer.valueOf("),
            "Expected boxed Integer result for Integer * Integer but got: " + source);
        assertTrue(source.contains("h.toInt(h.tagValue(\"a\")) * h.toInt(h.tagValue(\"b\"))"),
            "Expected int multiplication but got: " + source);
    }

    @Test
    void compileDivideOnLongTagsEmitsLong() throws Exception {
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'q': (tag(\"a\") as Long) / (tag(\"b\") as Long)\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("Long.valueOf("),
            "Expected boxed Long result for Long / Long but got: " + source);
        assertTrue(source.contains("h.toLong(h.tagValue(\"a\")) / h.toLong(h.tagValue(\"b\"))"),
            "Expected long division but got: " + source);
    }

    @Test
    void compileDoubleArithmeticEmitsDouble() throws Exception {
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'r': (tag(\"a\") as Double) - (tag(\"b\") as Double)\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("Double.valueOf("),
            "Expected boxed Double result but got: " + source);
        assertTrue(source.contains("h.toDouble(h.tagValue(\"a\")) - h.toDouble(h.tagValue(\"b\"))"),
            "Expected double subtraction but got: " + source);
    }

    @Test
    void compileMixedDoubleAndIntPromotesToDouble() throws Exception {
        // Integer * 1.5 → Double (JLS binary promotion).  The Integer side is
        // widened with `(double)` and the literal stays a plain double.
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'x': (tag(\"a\") as Integer) * 1.5\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("Double.valueOf("),
            "Expected boxed Double result but got: " + source);
        assertTrue(source.contains("(double) h.toInt("),
            "Expected (double) widening for Integer operand but got: " + source);
        assertTrue(source.contains(" * 1.5"),
            "Expected the 1.5 literal to appear as-is but got: " + source);
    }

    @Test
    void compileLongLiteralSuffixDrivesArithmeticType() throws Exception {
        // Bare `1000L` literal is LONG; it widens an Integer operand to long.
        final String dsl = "filter {\n"
            + "  if ((tag(\"a\") as Integer) + 1000L > 0) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("(long) h.toInt("),
            "Expected Integer widened to long when added to 1000L but got: " + source);
        assertTrue(source.contains(" + 1000L"),
            "Expected long literal preserved but got: " + source);
    }

    @Test
    void compileFloatCastEmitsFloatArithmetic() throws Exception {
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'r': (tag(\"a\") as Float) + (tag(\"b\") as Float)\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("Float.valueOf("),
            "Expected boxed Float result but got: " + source);
        assertTrue(source.contains("h.toFloat("),
            "Expected h.toFloat() for Float cast but got: " + source);
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

    @Test
    void compileMixedStringPlusIntegerStaysStringConcat() throws Exception {
        // String + Integer — `+` falls back to string concatenation (Java
        // semantics: when one operand is String, the other gets stringified).
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'msg': \"count=\" + (tag(\"n\") as Integer)\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("\"\" +"),
            "Expected string concatenation for String + Integer but got: " + source);
    }

    @Test
    void compileMinusOnStringTagsIsCompileError() {
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'x': tag(\"a\") - tag(\"b\")\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, () -> compileAndAssert(dsl));
        assertTrue(ex.getMessage().contains("requires numeric operands"),
            "Expected numeric-operand error but got: " + ex.getMessage());
    }

    @Test
    void compileMultiplyOnStringTagsIsCompileError() {
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'x': \"hi\" * (tag(\"n\") as Integer)\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        assertThrows(IllegalArgumentException.class, () -> compileAndAssert(dsl));
    }

    @Test
    void compilePrecedenceMultiplyBindsTighterThanPlus() throws Exception {
        // 1 + 2 * 3 — verify `*` binds tighter than `+` in the AST/codegen.
        final String dsl = "filter {\n"
            + "  if ((tag(\"a\") as Integer) + (tag(\"b\") as Integer) * (tag(\"c\") as Integer) > 0) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        // Inner mul wraps b * c, then outer + adds a.  The exact rendering
        // depends on parenthesisation but the key marker is `b)) * h.toInt(\"c"`.
        assertTrue(source.contains("h.toInt(h.tagValue(\"b\")) * h.toInt(h.tagValue(\"c\"))"),
            "Expected b * c to bind tighter than + a but got: " + source);
    }

    @Test
    void compileEnvoyAiGatewayTokenSumPattern() throws Exception {
        // Regression: the production envoy-ai-gateway rule must continue to
        // compile to int arithmetic (not long, not string concat) so the
        // 10000-token sampling threshold behaves as written.
        final String dsl = "filter {\n"
            + "  if (tag(\"in\") != \"\" && tag(\"out\") != \"\") {\n"
            + "    if ((tag(\"in\") as Integer) + (tag(\"out\") as Integer) < 10000) {\n"
            + "      abort {}\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains(
                "(h.toInt(h.tagValue(\"in\")) + h.toInt(h.tagValue(\"out\"))) < 10000"),
            "Expected int + int < 10000 (no L, no long widening) but got: " + source);
    }

    // ==================== P1 regression: comparison casts respect declared type ====================

    @Test
    void compileDoubleCastComparisonAgainstLiteralStaysInDouble() throws Exception {
        // `tag("a") as Double < 1.5` must compare in double, not pass the
        // double through h.toLong() (which would truncate to 0/1).
        final String dsl = "filter {\n"
            + "  if (tag(\"a\") as Double < 1.5) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.toDouble(h.tagValue(\"a\")) < 1.5"),
            "Expected double comparison but got: " + source);
        assertFalse(source.contains("h.toLong("),
            "Did not expect h.toLong() on a Double comparison but got: " + source);
    }

    @Test
    void compileDoubleCastComparisonAgainstDoubleCastValueAccess() throws Exception {
        // RHS is also a Double-cast value — the comparison must be Double on
        // both sides, never going through h.toLong(h.toDouble(...)).
        final String dsl = "filter {\n"
            + "  if ((tag(\"a\") as Double) < (tag(\"b\") as Double)) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.toDouble(h.tagValue(\"a\")) < h.toDouble(h.tagValue(\"b\"))"),
            "Expected double-vs-double comparison but got: " + source);
        assertFalse(source.contains("h.toLong(h.toDouble"),
            "Did not expect h.toLong() wrapping h.toDouble but got: " + source);
    }

    @Test
    void compileFloatCastComparisonStaysInFloat() throws Exception {
        final String dsl = "filter {\n"
            + "  if (tag(\"a\") as Float < 1.5f) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.toFloat(h.tagValue(\"a\"))"),
            "Expected h.toFloat() on a Float comparison but got: " + source);
        assertFalse(source.contains("h.toLong("),
            "Did not expect h.toLong() on a Float comparison but got: " + source);
    }

    @Test
    void compileMixedDoubleAndIntegerComparisonPromotesToDouble() throws Exception {
        // Double LHS, Integer RHS → JLS promotes to double; Integer side
        // gets widened.
        final String dsl = "filter {\n"
            + "  if ((tag(\"a\") as Double) > (tag(\"b\") as Integer)) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.toDouble(h.tagValue(\"a\"))"),
            "Expected double LHS but got: " + source);
        assertTrue(source.contains("(double) h.toInt(h.tagValue(\"b\"))"),
            "Expected Integer side widened to double but got: " + source);
    }

    @Test
    void compileTopLevelIntegerCastVsDoubleLiteralWidensInteger() throws Exception {
        // `tag("a") as Integer < 1.5` — the user declared Integer; the
        // codegen must call h.toInt(...) and then widen to double to
        // compare against 1.5, NOT call h.toDouble(...) directly which
        // would let LalRuntimeHelper parse a decimal string.
        final String dsl = "filter {\n"
            + "  if (tag(\"a\") as Integer < 1.5) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("(double) h.toInt(h.tagValue(\"a\")) < 1.5"),
            "Expected (double) h.toInt(...) < 1.5 but got: " + source);
        assertFalse(source.contains("h.toDouble(h.tagValue("),
            "Did not expect h.toDouble() to swallow the declared Integer cast but got: " + source);
    }

    @Test
    void compileTopLevelLongCastVsIntLiteralStaysInLong() throws Exception {
        final String dsl = "filter {\n"
            + "  if (tag(\"a\") as Long > 100) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.toLong(h.tagValue(\"a\")) > 100L"),
            "Expected h.toLong(...) > 100L but got: " + source);
    }

    @Test
    void compileTopLevelIntegerCastBothSidesStaysInInt() throws Exception {
        // RHS with a top-level numeric cast must also be honoured — the cast
        // must not be silently dropped on its way through generateValueAccessObj.
        final String dsl = "filter {\n"
            + "  if (tag(\"a\") as Integer > tag(\"b\") as Integer) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.toInt(h.tagValue(\"a\")) > h.toInt(h.tagValue(\"b\"))"),
            "Expected int > int with both sides honouring `as Integer` but got: " + source);
        assertFalse(source.contains("h.toLong("),
            "Did not expect h.toLong() fallback when both sides declare Integer but got: " + source);
    }

    @Test
    void compileTopLevelDoubleCastLhsIntegerCastRhsPromotesToDouble() throws Exception {
        // LHS declared Double, RHS declared Integer — JLS promotes to double;
        // the Integer side must be widened, not silently re-coerced via
        // h.toDouble() (which would change the runtime semantics).
        final String dsl = "filter {\n"
            + "  if (tag(\"a\") as Double > tag(\"b\") as Integer) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("h.toDouble(h.tagValue(\"a\"))"),
            "Expected double LHS but got: " + source);
        assertTrue(source.contains("(double) h.toInt(h.tagValue(\"b\"))"),
            "Expected Integer RHS widened to double but got: " + source);
    }

    // ==================== P2 regression: paren grouping without cast ====================

    @Test
    void compileParenGroupingWithoutCastInArithmetic() throws Exception {
        // `(1 + 2) * 3` — the inner paren has no cast; the codegen must
        // recurse into it and resolve the operand as int rather than UNKNOWN.
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'x': (1 + 2) * 3\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("Integer.valueOf("),
            "Expected boxed Integer result for (1+2)*3 but got: " + source);
        assertTrue(source.contains("(1 + 2) * 3"),
            "Expected the literal arithmetic to round-trip but got: " + source);
    }

    @Test
    void compileParenGroupingPropagatesNumericTypeForCondition() throws Exception {
        // Paren grouping inside a comparison must also resolve as int, not
        // fall back to h.toLong on the boxed Integer.
        final String dsl = "filter {\n"
            + "  if (((tag(\"a\") as Integer) + (tag(\"b\") as Integer)) * 2 < 10000) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("(h.toInt(h.tagValue(\"a\")) + h.toInt(h.tagValue(\"b\"))) * 2"),
            "Expected (a + b) * 2 as int but got: " + source);
        assertTrue(source.contains("< 10000") && !source.contains("< 10000L"),
            "Expected int RHS in comparison but got: " + source);
    }

    // ==================== P2 regression: def cast supports Double / Float ====================

    @Test
    void compileDefVariableWithDoubleCast() throws Exception {
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    def ratio = tag(\"a\") as Double\n"
            + "    tag 'x': ratio\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
    }

    @Test
    void compileDefVariableWithFloatCast() throws Exception {
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    def ratio = tag(\"a\") as Float\n"
            + "    tag 'x': ratio\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
    }

    // ==================== P2 regression: lexer rejects invalid suffix combos ====================

    @Test
    void compileFractionalLongSuffixIsLexerError() {
        // `1.5L` is not a valid Java literal — the lexer must reject it.
        final String dsl = "filter {\n"
            + "  if (tag(\"a\") as Double < 1.5L) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        assertThrows(IllegalArgumentException.class, () -> compileAndAssert(dsl));
    }

    @Test
    void compileExponentLongSuffixIsLexerError() {
        // `1e3L` is also not a valid Java literal.
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'x': (tag(\"a\") as Long) + 1e3L\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        assertThrows(IllegalArgumentException.class, () -> compileAndAssert(dsl));
    }

    @Test
    void compileFloatLiteralSuffixedAcceptedInArithmetic() throws Exception {
        // `1.5f + 2.5f` round-trips through the lexer and emits float arithmetic.
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'x': 1.5f + 2.5f\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("Float.valueOf("),
            "Expected boxed Float result for 1.5f + 2.5f but got: " + source);
    }

    @Test
    void compileExponentLiteralEmitsDoubleArithmetic() throws Exception {
        // `1e6 + (... as Double)` — the literal is double, no L allowed.
        final String dsl = "filter {\n"
            + "  extractor {\n"
            + "    tag 'x': (tag(\"a\") as Double) + 1e6\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";
        compileAndAssert(dsl);
        final String source = generator.generateSource(dsl);
        assertTrue(source.contains("Double.valueOf("),
            "Expected boxed Double result but got: " + source);
        assertTrue(source.contains("h.toDouble(h.tagValue(\"a\")) + 1e6"),
            "Expected exponent literal preserved but got: " + source);
    }

    @Test
    void compileShippedEnvoyAiGatewayLlmRule() throws Exception {
        // End-to-end regression: load the actual production rule YAML and
        // compile it through the v2 pipeline. Asserts the generated Java for
        // the token-sum check uses int arithmetic against an int literal,
        // matching the integer semantics intended by the rule author.
        final Path yamlPath = locateProjectFile(
            "oap-server/server-starter/src/main/resources/lal/envoy-ai-gateway.yaml");
        final String yaml = new String(Files.readAllBytes(yamlPath), StandardCharsets.UTF_8);
        final String llmDsl = extractDslBlock(yaml, "envoy-ai-gateway-llm-access-log");
        compileAndAssert(llmDsl);
        final String source = generator.generateSource(llmDsl);
        // Dump the full generated source for human inspection on failure or
        // when running with `-Dlal.debug.dump=true`.
        if (Boolean.getBoolean("lal.debug.dump")) {
            final Path dump = Paths.get("target", "envoy-ai-gateway-llm.generated.java");
            Files.createDirectories(dump.getParent());
            Files.write(dump, source.getBytes(StandardCharsets.UTF_8));
        }
        // The exact rendering of the token-sum check (with quoted tag keys).
        final String expected =
            "(h.toInt(h.tagValue(\"gen_ai.usage.input_tokens\")) "
                + "+ h.toInt(h.tagValue(\"gen_ai.usage.output_tokens\"))) < 10000";
        assertTrue(source.contains(expected),
            "Expected production envoy-ai-gateway rule to compile to int arithmetic; "
                + "looking for:\n  " + expected + "\nbut got:\n" + source);
        // And the obsolete long-arithmetic rendering must not reappear.
        assertFalse(source.contains("(long) h.toInt(h.tagValue(\"gen_ai.usage.input_tokens\"))"),
            "Did not expect (long) widening on int+int but got: " + source);
        assertFalse(source.contains("< 10000L"),
            "Did not expect long-suffixed comparison literal but got: " + source);
    }

    /**
     * Walks up from CWD until it finds the SkyWalking project root (containing
     * the given relative path). Lets this test work regardless of which
     * directory Maven invokes it from (module dir vs. project root).
     */
    private static Path locateProjectFile(final String relative) {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 10 && dir != null; i++) {
            final Path candidate = dir.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Cannot locate " + relative + " from " + System.getProperty("user.dir"));
    }

    /**
     * Extract a single rule's {@code dsl: |} block from a multi-rule LAL YAML.
     * Stops at the next {@code - name:} sibling.
     */
    private static String extractDslBlock(final String yaml, final String ruleName) {
        final int nameIdx = yaml.indexOf("name: " + ruleName);
        if (nameIdx < 0) {
            throw new IllegalArgumentException("Rule '" + ruleName + "' not found in YAML");
        }
        final int dslIdx = yaml.indexOf("dsl: |", nameIdx);
        if (dslIdx < 0) {
            throw new IllegalArgumentException("dsl: | not found for rule " + ruleName);
        }
        final int bodyStart = yaml.indexOf('\n', dslIdx) + 1;
        final int nextRule = yaml.indexOf("- name:", bodyStart);
        final String body = nextRule >= 0 ? yaml.substring(bodyStart, nextRule)
            : yaml.substring(bodyStart);
        // Strip the leading 6-space block indent from each line.
        final StringBuilder sb = new StringBuilder();
        for (final String line : body.split("\n", -1)) {
            sb.append(line.length() >= 6 && line.startsWith("      ") ? line.substring(6) : line)
              .append('\n');
        }
        return sb.toString().trim();
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
