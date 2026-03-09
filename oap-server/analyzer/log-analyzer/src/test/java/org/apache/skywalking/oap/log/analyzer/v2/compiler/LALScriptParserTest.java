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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LALScriptParserTest {

    @Test
    void parseMinimalFilter() {
        final LALScriptModel model = LALScriptParser.parse("filter { sink {} }");
        assertNotNull(model);
        assertEquals(1, model.getStatements().size());
        assertInstanceOf(LALScriptModel.SinkBlock.class, model.getStatements().get(0));
    }

    @Test
    void parseJsonParserWithExtractorAndSink() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    service parsed.service as String\n"
                + "    layer parsed.layer as String\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");

        assertEquals(3, model.getStatements().size());
        assertInstanceOf(LALScriptModel.JsonParser.class, model.getStatements().get(0));

        final LALScriptModel.ExtractorBlock extractor =
            (LALScriptModel.ExtractorBlock) model.getStatements().get(1);
        assertEquals(2, extractor.getStatements().size());

        final LALScriptModel.FieldAssignment serviceField =
            (LALScriptModel.FieldAssignment) extractor.getStatements().get(0);
        assertEquals(LALScriptModel.FieldType.SERVICE, serviceField.getFieldType());
        assertTrue(serviceField.getValue().isParsedRef());
        assertEquals("String", serviceField.getCastType());
    }

    @Test
    void parseTextParserWithRegexp() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  text {\n"
                + "    regexp '.+\"(?<request>.+)\"(?<status>\\d{3}).+'\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");

        assertEquals(2, model.getStatements().size());
        final LALScriptModel.TextParser textParser =
            (LALScriptModel.TextParser) model.getStatements().get(0);
        assertNotNull(textParser.getRegexpPattern());
    }

    @Test
    void parseMetricsBlock() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
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

        final LALScriptModel.ExtractorBlock extractor =
            (LALScriptModel.ExtractorBlock) model.getStatements().get(0);
        final LALScriptModel.MetricsBlock metrics =
            (LALScriptModel.MetricsBlock) extractor.getStatements().get(0);

        assertEquals("nginx_error_log_count", metrics.getName());
        assertEquals(2, metrics.getLabels().size());
        assertTrue(metrics.getLabels().containsKey("level"));
        assertTrue(metrics.getLabels().containsKey("service"));
        assertNotNull(metrics.getValue());
    }

    @Test
    void parseSinkWithSampler() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  sink {\n"
                + "    sampler {\n"
                + "      rateLimit('service:error') {\n"
                + "        rpm 6000\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}");

        final LALScriptModel.SinkBlock sink =
            (LALScriptModel.SinkBlock) model.getStatements().get(0);
        assertEquals(1, sink.getStatements().size());
        final LALScriptModel.SamplerBlock sampler =
            (LALScriptModel.SamplerBlock) sink.getStatements().get(0);
        assertEquals(1, sampler.getContents().size());
        final LALScriptModel.RateLimitBlock rateLimit =
            (LALScriptModel.RateLimitBlock) sampler.getContents().get(0);
        assertEquals("service:error", rateLimit.getId());
        assertEquals(6000, rateLimit.getRpm());
    }

    @Test
    void parseInterpolatedRateLimitId() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  sink {\n"
                + "    sampler {\n"
                + "      rateLimit(\"${log.service}:${parsed.code}\") {\n"
                + "        rpm 3000\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}");

        final LALScriptModel.SinkBlock sink =
            (LALScriptModel.SinkBlock) model.getStatements().get(0);
        final LALScriptModel.SamplerBlock sampler =
            (LALScriptModel.SamplerBlock) sink.getStatements().get(0);
        final LALScriptModel.RateLimitBlock rl =
            (LALScriptModel.RateLimitBlock) sampler.getContents().get(0);

        assertTrue(rl.isIdInterpolated());
        assertEquals(3, rl.getIdParts().size());

        // Part 0: expression ${log.service}
        assertFalse(rl.getIdParts().get(0).isLiteral());
        assertTrue(rl.getIdParts().get(0).getExpression().isLogRef());

        // Part 1: literal ":"
        assertTrue(rl.getIdParts().get(1).isLiteral());
        assertEquals(":", rl.getIdParts().get(1).getLiteral());

        // Part 2: expression ${parsed.code}
        assertFalse(rl.getIdParts().get(2).isLiteral());
        assertTrue(rl.getIdParts().get(2).getExpression().isParsedRef());
    }

    @Test
    void parsePlainRateLimitIdNotInterpolated() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  sink {\n"
                + "    sampler {\n"
                + "      rateLimit('service:error') {\n"
                + "        rpm 6000\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}");

        final LALScriptModel.SinkBlock sink =
            (LALScriptModel.SinkBlock) model.getStatements().get(0);
        final LALScriptModel.SamplerBlock sampler =
            (LALScriptModel.SamplerBlock) sink.getStatements().get(0);
        final LALScriptModel.RateLimitBlock rl =
            (LALScriptModel.RateLimitBlock) sampler.getContents().get(0);

        assertFalse(rl.isIdInterpolated());
    }

    @Test
    void parseIfCondition() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  if (parsed.status) {\n"
                + "    extractor {\n"
                + "      layer parsed.layer as String\n"
                + "    }\n"
                + "    sink {}\n"
                + "  }\n"
                + "}");

        assertEquals(1, model.getStatements().size());
        final LALScriptModel.IfBlock ifBlock =
            (LALScriptModel.IfBlock) model.getStatements().get(0);
        assertNotNull(ifBlock.getCondition());
        assertEquals(2, ifBlock.getThenBranch().size());
    }

    @Test
    void parseElseIfChain() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
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

        assertEquals(1, model.getStatements().size());
        final LALScriptModel.IfBlock top =
            (LALScriptModel.IfBlock) model.getStatements().get(0);
        assertNotNull(top.getCondition());
        assertEquals(1, top.getThenBranch().size());

        // else branch contains a nested IfBlock for "else if (parsed.b)"
        assertEquals(1, top.getElseBranch().size());
        final LALScriptModel.IfBlock elseIf1 =
            (LALScriptModel.IfBlock) top.getElseBranch().get(0);
        assertNotNull(elseIf1.getCondition());
        assertEquals(1, elseIf1.getThenBranch().size());

        // nested else branch contains another IfBlock for "else if (parsed.c)"
        assertEquals(1, elseIf1.getElseBranch().size());
        final LALScriptModel.IfBlock elseIf2 =
            (LALScriptModel.IfBlock) elseIf1.getElseBranch().get(0);
        assertNotNull(elseIf2.getCondition());
        assertEquals(1, elseIf2.getThenBranch().size());

        // innermost else branch is the final else body
        assertEquals(1, elseIf2.getElseBranch().size());
        assertInstanceOf(LALScriptModel.SinkBlock.class, elseIf2.getElseBranch().get(0));
    }

    @Test
    void parseElseIfWithoutFinalElse() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  if (parsed.a) {\n"
                + "    sink {}\n"
                + "  } else if (parsed.b) {\n"
                + "    sink {}\n"
                + "  }\n"
                + "}");

        final LALScriptModel.IfBlock top =
            (LALScriptModel.IfBlock) model.getStatements().get(0);
        assertEquals(1, top.getElseBranch().size());
        final LALScriptModel.IfBlock elseIf =
            (LALScriptModel.IfBlock) top.getElseBranch().get(0);
        assertNotNull(elseIf.getCondition());
        assertTrue(elseIf.getElseBranch().isEmpty());
    }

    @Test
    void parseSyntaxErrorThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> LALScriptParser.parse("filter {"));
    }

    // ==================== Function call parsing ====================

    @Test
    void parseTagFunctionCallInCondition() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  if (tag(\"LOG_KIND\") == \"SLOW_SQL\") {\n"
                + "    sink {}\n"
                + "  }\n"
                + "}");

        final LALScriptModel.IfBlock ifBlock =
            (LALScriptModel.IfBlock) model.getStatements().get(0);
        final LALScriptModel.ComparisonCondition cond =
            (LALScriptModel.ComparisonCondition) ifBlock.getCondition();

        // Left side should be a function call
        final LALScriptModel.ValueAccess left = cond.getLeft();
        assertEquals("tag", left.getFunctionCallName());
        assertEquals(1, left.getFunctionCallArgs().size());
        assertEquals("LOG_KIND",
            left.getFunctionCallArgs().get(0).getValue().getSegments().get(0));

        // Right side should be a string value (parsed as ValueAccess with stringLiteral flag)
        assertInstanceOf(LALScriptModel.ValueAccessConditionValue.class, cond.getRight());
        final LALScriptModel.ValueAccessConditionValue rightVal =
            (LALScriptModel.ValueAccessConditionValue) cond.getRight();
        assertTrue(rightVal.getValue().isStringLiteral());
        assertEquals("SLOW_SQL", rightVal.getValue().getSegments().get(0));
    }

    @Test
    void parseTagFunctionCallAsSingleCondition() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  if (tag(\"LOG_KIND\")) {\n"
                + "    sink {}\n"
                + "  }\n"
                + "}");

        final LALScriptModel.IfBlock ifBlock =
            (LALScriptModel.IfBlock) model.getStatements().get(0);
        final LALScriptModel.ExprCondition cond =
            (LALScriptModel.ExprCondition) ifBlock.getCondition();
        assertEquals("tag", cond.getExpr().getFunctionCallName());
        assertEquals(1, cond.getExpr().getFunctionCallArgs().size());
    }

    // ==================== Safe navigation parsing ====================

    @Test
    void parseSafeNavigationFields() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  extractor {\n"
                + "    service parsed?.response?.service as String\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");

        final LALScriptModel.ExtractorBlock extractor =
            (LALScriptModel.ExtractorBlock) model.getStatements().get(0);
        final LALScriptModel.FieldAssignment field =
            (LALScriptModel.FieldAssignment) extractor.getStatements().get(0);

        assertTrue(field.getValue().isParsedRef());
        assertEquals(2, field.getValue().getChain().size());
        assertTrue(((LALScriptModel.FieldSegment) field.getValue().getChain().get(0))
            .isSafeNav());
        assertTrue(((LALScriptModel.FieldSegment) field.getValue().getChain().get(1))
            .isSafeNav());
    }

    @Test
    void parseSafeNavigationMethods() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  extractor {\n"
                + "    service parsed?.flags?.toString()?.trim() as String\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");

        final LALScriptModel.ExtractorBlock extractor =
            (LALScriptModel.ExtractorBlock) model.getStatements().get(0);
        final LALScriptModel.FieldAssignment field =
            (LALScriptModel.FieldAssignment) extractor.getStatements().get(0);

        assertEquals(3, field.getValue().getChain().size());
        // flags is a safe field
        assertInstanceOf(LALScriptModel.FieldSegment.class,
            field.getValue().getChain().get(0));
        assertTrue(((LALScriptModel.FieldSegment) field.getValue().getChain().get(0))
            .isSafeNav());
        // toString() is a safe method
        assertInstanceOf(LALScriptModel.MethodSegment.class,
            field.getValue().getChain().get(1));
        assertTrue(((LALScriptModel.MethodSegment) field.getValue().getChain().get(1))
            .isSafeNav());
        assertEquals("toString",
            ((LALScriptModel.MethodSegment) field.getValue().getChain().get(1)).getName());
        // trim() is a safe method
        assertTrue(((LALScriptModel.MethodSegment) field.getValue().getChain().get(2))
            .isSafeNav());
        assertEquals("trim",
            ((LALScriptModel.MethodSegment) field.getValue().getChain().get(2)).getName());
    }

    // ==================== Method argument parsing ====================

    @Test
    void parseMethodWithArguments() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    service ProcessRegistry.generateVirtualLocalProcess("
                + "parsed.service as String, parsed.instance as String) as String\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");

        final LALScriptModel.ExtractorBlock extractor =
            (LALScriptModel.ExtractorBlock) model.getStatements().get(1);
        final LALScriptModel.FieldAssignment field =
            (LALScriptModel.FieldAssignment) extractor.getStatements().get(0);

        assertTrue(field.getValue().isProcessRegistryRef());
        assertEquals(1, field.getValue().getChain().size());

        final LALScriptModel.MethodSegment method =
            (LALScriptModel.MethodSegment) field.getValue().getChain().get(0);
        assertEquals("generateVirtualLocalProcess", method.getName());
        assertEquals(2, method.getArguments().size());
        assertTrue(method.getArguments().get(0).getValue().isParsedRef());
        assertEquals("String", method.getArguments().get(0).getCastType());
        assertTrue(method.getArguments().get(1).getValue().isParsedRef());
        assertEquals("String", method.getArguments().get(1).getCastType());
    }

    // ==================== If in extractor/sink parsing ====================

    @Test
    void parseIfInsideExtractor() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    if (parsed.status) {\n"
                + "      tag 'http.status_code': parsed.status\n"
                + "    }\n"
                + "    tag 'response.flag': parsed.flags\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");

        final LALScriptModel.ExtractorBlock extractor =
            (LALScriptModel.ExtractorBlock) model.getStatements().get(1);
        assertEquals(2, extractor.getStatements().size());
        assertInstanceOf(LALScriptModel.IfBlock.class, extractor.getStatements().get(0));
        assertInstanceOf(LALScriptModel.TagAssignment.class, extractor.getStatements().get(1));
    }

    @Test
    void parseIfInsideSink() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  sink {\n"
                + "    sampler {\n"
                + "      if (parsed.error) {\n"
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

        final LALScriptModel.SinkBlock sink =
            (LALScriptModel.SinkBlock) model.getStatements().get(0);
        final LALScriptModel.SamplerBlock sampler =
            (LALScriptModel.SamplerBlock) sink.getStatements().get(0);
        // The sampler has one if-block as content
        assertEquals(1, sampler.getContents().size());
        assertInstanceOf(LALScriptModel.IfBlock.class, sampler.getContents().get(0));
    }

    @Test
    void parseOutputFieldAssignment() {
        final LALScriptModel model = LALScriptParser.parse(
            "filter {\n"
                + "  json {}\n"
                + "  extractor {\n"
                + "    service parsed.service as String\n"
                + "    statement parsed.statement as String\n"
                + "    latency parsed.latency as Long\n"
                + "  }\n"
                + "  sink {}\n"
                + "}");
        final LALScriptModel.ExtractorBlock extractor =
            (LALScriptModel.ExtractorBlock) model.getStatements().get(1);
        assertEquals(3, extractor.getStatements().size());

        // 'service' is a known field → FieldAssignment
        assertInstanceOf(LALScriptModel.FieldAssignment.class,
            extractor.getStatements().get(0));

        // 'statement' is not a known field → OutputFieldAssignment
        final LALScriptModel.OutputFieldAssignment stmt =
            (LALScriptModel.OutputFieldAssignment) extractor.getStatements().get(1);
        assertEquals("statement", stmt.getFieldName());
        assertEquals("String", stmt.getCastType());

        // 'latency' is not a known field → OutputFieldAssignment
        final LALScriptModel.OutputFieldAssignment latency =
            (LALScriptModel.OutputFieldAssignment) extractor.getStatements().get(2);
        assertEquals("latency", latency.getFieldName());
        assertEquals("Long", latency.getCastType());
    }
}
