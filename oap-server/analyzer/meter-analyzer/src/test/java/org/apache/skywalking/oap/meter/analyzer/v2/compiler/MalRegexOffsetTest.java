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
 *
 */

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code preprocessRegexLiterals} rewrites {@code /\|/} into {@code "\\|"}, which is LONGER, so
 * ANTLR's offsets are in a different coordinate space than the original expression. Since
 * {@link MalSourceMap}'s segments are built over the original, an untranslated offset would
 * resolve a stage against the wrong fragment — reporting a line that exists but is not its own.
 *
 * <p>No shipped rule uses a regex literal in argument position today, so this guards a latent
 * defect rather than a live one.
 */
class MalRegexOffsetTest {

    @Test
    void preprocessingChangesLengthWhichIsWhyTranslationIsNeeded() {
        final String original = "x.split(/\\|/, -1).sum(['h'])";
        final String preprocessed = MALScriptParser.preprocessRegexLiterals(original);

        assertTrue(preprocessed.length() > original.length(),
            "the rewrite must grow the text, else there would be nothing to translate");
        assertTrue(preprocessed.contains("\"\\\\|\""), "regex literal became a string literal");
    }

    @Test
    void offsetsAfterARegexLiteralTranslateBackToTheOriginal() {
        final String original = "x.split(/\\|/, -1).sum(['h'])";
        final java.util.Map.Entry<String, MALScriptParser.OffsetTranslator> pre =
            MALScriptParser.preprocessRegexLiteralsTracked(original);
        final String preprocessed = pre.getKey();
        final MALScriptParser.OffsetTranslator t = pre.getValue();

        // `sum` sits after the rewrite, so its offset differs between the two spaces.
        final int inPreprocessed = preprocessed.indexOf("sum");
        final int inOriginal = original.indexOf("sum");
        assertTrue(inPreprocessed != inOriginal, "offsets must genuinely differ for this to matter");

        assertEquals(inOriginal, t.toOriginal(inPreprocessed));
    }

    @Test
    void offsetsBeforeTheRewriteAreUnchanged() {
        final String original = "x.split(/\\|/, -1)";
        final MALScriptParser.OffsetTranslator t =
            MALScriptParser.preprocessRegexLiteralsTracked(original).getValue();

        assertEquals(0, t.toOriginal(0));
        assertEquals(original.indexOf("split"), t.toOriginal(original.indexOf("split")));
    }

    @Test
    void withoutARegexLiteralTranslationIsIdentity() {
        final String original = "node_cpu.sum(['host']).rate('PT1M')";
        final MALScriptParser.OffsetTranslator t =
            MALScriptParser.preprocessRegexLiteralsTracked(original).getValue();

        assertEquals(original, MALScriptParser.preprocessRegexLiterals(original));
        for (int i = 0; i < original.length(); i++) {
            assertEquals(i, t.toOriginal(i));
        }
    }

    @Test
    void aParsedChainAfterARegexLiteralReportsOriginalSpaceOffsets() {
        // End-to-end: the offset stored on MethodCall must index the ORIGINAL expression.
        final String original = "x.split(/\\|/, -1).sum(['h'])";
        final MALExpressionModel.Expr ast = MALScriptParser.parse(original);
        final MALExpressionModel.MetricExpr metric = (MALExpressionModel.MetricExpr) ast;

        for (final MALExpressionModel.MethodCall mc : metric.getMethodChain()) {
            if (!"sum".equals(mc.getName())) {
                continue;
            }
            assertEquals(original.indexOf("sum"), mc.getSourceStartIndex(),
                "sum's offset must index the original expression, not the preprocessed one");
            return;
        }
        throw new AssertionError("sum() stage not found in the parsed chain");
    }

    @Test
    void offsetsAreUtf16UnitsNotCodePointsWhenTheExpressionHasASupplementaryChar() {
        // ANTLR indexes its CharStream by code point; MalSourceMap uses String.length() /
        // substring, i.e. UTF-16 units. They diverge by one per supplementary character, so an
        // emoji in a string argument would shift every later stage and mis-resolve it.
        final String original = "x.tagEqual('n','\uD83D\uDE80').sum(['h'])";
        assertTrue(original.length() > original.codePointCount(0, original.length()),
            "fixture must actually contain a supplementary character");

        final MALExpressionModel.MetricExpr metric =
            (MALExpressionModel.MetricExpr) MALScriptParser.parse(original);

        for (final MALExpressionModel.MethodCall mc : metric.getMethodChain()) {
            if (!"sum".equals(mc.getName())) {
                continue;
            }
            assertEquals(original.indexOf("sum"), mc.getSourceStartIndex(),
                "offset must be a Java String index, not a code-point index");
            return;
        }
        throw new AssertionError("sum() stage not found in the parsed chain");
    }

    @Test
    void bmpOnlyExpressionsAreUnaffectedByTheConversion() {
        final String original = "node_cpu.sum(['host']).rate('PT1M')";
        final MALExpressionModel.MetricExpr metric =
            (MALExpressionModel.MetricExpr) MALScriptParser.parse(original);

        for (final MALExpressionModel.MethodCall mc : metric.getMethodChain()) {
            if ("rate".equals(mc.getName())) {
                assertEquals(original.indexOf("rate"), mc.getSourceStartIndex());
                return;
            }
        }
        throw new AssertionError("rate() stage not found");
    }
}
