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

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.ExtractorBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.FieldType;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.IfBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.MetricsBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.PersistBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.SinkBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.LogicalOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALExprCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALLogicalCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALNotCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TALScriptParserTest {

    @Test
    void parseMinimalFilter() {
        final TALScriptModel model = TALScriptParser.parse("filter { extractor {} }");
        assertNotNull(model);
        assertEquals(1, model.getStatements().size());
        assertInstanceOf(ExtractorBlock.class, model.getStatements().get(0));
    }

    @Test
    void rejectSyntaxError() {
        assertThrows(IllegalArgumentException.class, () -> TALScriptParser.parse("filter { extractor {"));
    }

    @Test
    void parseSpanAndMetaRoots() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { extractor { service meta.service; endpoint span.name } }");
        final List<TALScriptModel.ExtractorStatement> stmts =
            ((ExtractorBlock) model.getStatements().get(0)).getStatements();
        assertEquals(2, stmts.size());

        final TALScriptModel.FieldAssignment service = (TALScriptModel.FieldAssignment) stmts.get(0);
        assertEquals(FieldType.SERVICE, service.getFieldType());
        assertTrue(service.getValue().isMetaRef());
        assertFalse(service.getValue().isInputRef());

        final TALScriptModel.FieldAssignment endpoint = (TALScriptModel.FieldAssignment) stmts.get(1);
        assertEquals(FieldType.ENDPOINT, endpoint.getFieldType());
        assertTrue(endpoint.getValue().isInputRef());
        assertFalse(endpoint.getValue().isMetaRef());
    }

    @Test
    void talHasNoStaticRoot() {
        final TALScriptModel model = TALScriptParser.parse("filter { extractor { layer meta.layer } }");
        final TALScriptModel.FieldAssignment layer = (TALScriptModel.FieldAssignment)
            ((ExtractorBlock) model.getStatements().get(0)).getStatements().get(0);
        assertFalse(layer.getValue().isStaticRef());
    }

    @Test
    void parseSemicolonSeparatedMetricsBlock() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { extractor { def svc = service(meta.service, meta.layer)\n"
                + "metrics { name \"service_cpm\"; scope svc; function cpm; value 1 } } }");
        final MetricsBlock mb = firstMetrics(model);
        assertEquals("service_cpm", mb.getName());
        assertEquals("cpm", mb.getFunction());
        assertEquals("svc", mb.getScope().getSegments().get(0));
        assertTrue(mb.getValue().isNumberLiteral());
        assertNull(mb.getMatch());
        assertNull(mb.getStatus());
    }

    @Test
    void parsePercentAndApdexConditions() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { extractor { def svc = service(meta.service, meta.layer)\n"
                + "metrics { name \"sla\"; scope svc; function percent; match meta.statusCode != \"ERROR\" }\n"
                + "metrics { name \"apdex\"; scope svc; function apdex; value meta.latency;"
                + " status meta.statusCode != \"ERROR\" } } }");
        final List<MetricsBlock> blocks = allMetrics(model);
        assertEquals(2, blocks.size());

        assertInstanceOf(TALScriptModel.ComparisonCondition.class, blocks.get(0).getMatch());
        assertNull(blocks.get(0).getValue());
        assertNull(blocks.get(0).getStatus());

        assertInstanceOf(TALScriptModel.ComparisonCondition.class, blocks.get(1).getStatus());
        assertNotNull(blocks.get(1).getValue());
        assertNull(blocks.get(1).getMatch());
    }

    @Test
    void parseBucketsAndRanks() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { extractor { def svc = service(meta.service, meta.layer)\n"
                + "metrics { name \"p\"; scope svc; function percentile; value meta.latency\n"
                + "          buckets [10, 25, 50, 100] ranks [50, 90, 99] } } }");
        final MetricsBlock mb = firstMetrics(model);
        assertEquals(List.of("10", "25", "50", "100"), mb.getBuckets());
        assertEquals(List.of("50", "90", "99"), mb.getRanks());
    }

    @Test
    void parseLabels() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { extractor { def svc = service(meta.service, meta.layer)\n"
                + "metrics { name \"c\"; scope svc; function sumLabeled; value 1;"
                + " labels status: attr(\"http.response.status_code\") } } }");
        final MetricsBlock mb = firstMetrics(model);
        assertEquals(1, mb.getLabels().size());
        assertTrue(mb.getLabels().containsKey("status"));
        assertEquals("attr", mb.getLabels().get("status").getValue().getFunctionCallName());
    }

    @Test
    void parseRelationConstructorWithBareDetectPoint() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { extractor { def e = serviceRelation(CLIENT, meta.service, meta.layer,"
                + " \"db\", \"VIRTUAL_DATABASE\", component(\"postgresql\")) } }");
        final TALScriptModel.DefStatement def = (TALScriptModel.DefStatement)
            ((ExtractorBlock) model.getStatements().get(0)).getStatements().get(0);
        assertEquals("serviceRelation", def.getInitializer().getFunctionCallName());
        assertEquals(6, def.getInitializer().getFunctionCallArgs().size());
        assertEquals("CLIENT",
            def.getInitializer().getFunctionCallArgs().get(0).getValue().getSegments().get(0));
    }

    @Test
    void parseAbortInsideExtractor() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { extractor { if (meta.kind == \"INTERNAL\") { abort {} } } }");
        final IfBlock ifBlock = (IfBlock) ((ExtractorBlock) model.getStatements().get(0))
            .getStatements().get(0);
        assertInstanceOf(TALScriptModel.AbortStatement.class, ifBlock.getThenStatements().get(0));
    }

    @Test
    void notBindsTighterThanAndAndOr() {
        // `!peer && hasAttr("x")` is `(!peer) && hasAttr("x")`: the NOT wraps only its operand.
        final TALScriptModel model = TALScriptParser.parse(
            "filter { if (!peer && hasAttr(\"x\") || meta.root) { abort {} } }");
        final IfBlock ifBlock = (IfBlock) model.getStatements().get(0);
        final TALLogicalCondition or = (TALLogicalCondition) ifBlock.getCondition();
        assertEquals(LogicalOp.OR, or.getOp());
        final TALLogicalCondition and = (TALLogicalCondition) or.getLeft();
        assertEquals(LogicalOp.AND, and.getOp());
        assertInstanceOf(TALNotCondition.class, and.getLeft());
        assertInstanceOf(TALExprCondition.class, ((TALNotCondition) and.getLeft()).getInner());
    }

    @Test
    void parseAliasStatementInExtractorAndAtFilterLevel() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { alias attr(\"server.address\")\n"
                + "  extractor { alias attr(\"server.address\") + \":\" + attr(\"server.port\") } }");
        assertInstanceOf(TALScriptModel.AliasStatement.class, model.getStatements().get(0));
        final TALScriptModel.AliasStatement inExtractor = (TALScriptModel.AliasStatement)
            ((ExtractorBlock) model.getStatements().get(1)).getStatements().get(0);
        assertNotNull(inExtractor.getAddress());
        assertEquals(2, inExtractor.getSourceLine());
        assertTrue(inExtractor.getSourceText().startsWith("alias attr"));
    }

    @Test
    void parseSinkAndPersistTakeTheSameStatements() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { sink { sampler { rateLimit(\"id\") { rpm 6000 } } } persist { enforcer {} } }");
        assertEquals(2, model.getStatements().size());
        final SinkBlock sink = (SinkBlock) model.getStatements().get(0);
        assertInstanceOf(TALScriptModel.SamplerBlock.class, sink.getStatements().get(0));
        final PersistBlock persist = (PersistBlock) model.getStatements().get(1);
        assertInstanceOf(TALScriptModel.EnforcerStatement.class, persist.getStatements().get(0));
    }

    @Test
    void parseRateLimitIdInterpolation() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { persist { sampler { rateLimit(\"slow-${meta.service}\") { rpm 10 } } } }");
        final TALScriptModel.SamplerBlock sampler = (TALScriptModel.SamplerBlock)
            ((PersistBlock) model.getStatements().get(0)).getStatements().get(0);
        final TALScriptModel.RateLimitBlock rl =
            (TALScriptModel.RateLimitBlock) sampler.getContents().get(0);
        assertTrue(rl.isIdInterpolated());
        assertEquals(10, rl.getRpm());
        assertEquals(2, rl.getIdParts().size());
        assertTrue(rl.getIdParts().get(0).isLiteral());
        assertTrue(rl.getIdParts().get(1).getExpression().isMetaRef());
    }

    @Test
    void rejectNonIntegerRpm() {
        assertThrows(IllegalArgumentException.class, () -> TALScriptParser.parse(
            "filter { persist { sampler { rateLimit(\"id\") { rpm 1.5 } } } }"));
    }

    @Test
    void parseOutputFieldStatement() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { extractor { statement attr(\"db.query.text\"); latency meta.latency } }");
        final List<TALScriptModel.ExtractorStatement> stmts =
            ((ExtractorBlock) model.getStatements().get(0)).getStatements();
        assertEquals(2, stmts.size());
        assertEquals("statement", ((TALScriptModel.OutputFieldAssignment) stmts.get(0)).getFieldName());
        assertEquals("latency", ((TALScriptModel.OutputFieldAssignment) stmts.get(1)).getFieldName());
    }

    @Test
    void parseElseIfChainAsNestedBlocks() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { extractor { if (meta.kind == \"SERVER\") { layer \"A\" }"
                + " else if (meta.kind == \"CLIENT\") { layer \"B\" } else { layer \"C\" } } }");
        final IfBlock outer = (IfBlock) ((ExtractorBlock) model.getStatements().get(0))
            .getStatements().get(0);
        assertEquals(1, outer.getElseStatements().size());
        final IfBlock inner = (IfBlock) outer.getElseStatements().get(0);
        assertEquals(1, inner.getThenStatements().size());
        assertEquals(1, inner.getElseStatements().size());
        assertInstanceOf(TALScriptModel.FieldAssignment.class, inner.getElseStatements().get(0));
    }

    @Test
    void keywordsStayUsableAsIdentifiers() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { extractor { def svc = service(meta.service, meta.layer)\n"
                + "metrics { name \"c\"; scope svc; function sumLabeled; value 1;"
                + " labels service: meta.service, endpoint: meta.endpoint } } }");
        final MetricsBlock mb = firstMetrics(model);
        assertEquals(2, mb.getLabels().size());
        assertTrue(mb.getLabels().containsKey("service"));
        assertTrue(mb.getLabels().containsKey("endpoint"));
    }

    @Test
    void parseArithmeticAndConcat() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter { extractor { def peer = attr(\"server.address\") + \":\" + attr(\"server.port\") } }");
        final TALScriptModel.DefStatement def = (TALScriptModel.DefStatement)
            ((ExtractorBlock) model.getStatements().get(0)).getStatements().get(0);
        assertEquals(3, def.getInitializer().getConcatParts().size());
        assertEquals(2, def.getInitializer().getConcatOps().size());
    }

    @Test
    void statementsCarryTheirSourceLine() {
        final TALScriptModel model = TALScriptParser.parse(
            "filter {\n  extractor {\n    layer \"OTLP_SERVICE\"\n  }\n}");
        final TALScriptModel.FieldAssignment layer = (TALScriptModel.FieldAssignment)
            ((ExtractorBlock) model.getStatements().get(0)).getStatements().get(0);
        assertEquals(3, layer.getSourceLine());
        assertEquals("layer \"OTLP_SERVICE\"", layer.getSourceText());
    }

    private static MetricsBlock firstMetrics(final TALScriptModel model) {
        final List<MetricsBlock> blocks = allMetrics(model);
        assertFalse(blocks.isEmpty(), "expected at least one metrics block");
        return blocks.get(0);
    }

    private static List<MetricsBlock> allMetrics(final TALScriptModel model) {
        final List<MetricsBlock> out = new ArrayList<>();
        collectMetrics(model.getStatements(), out);
        return out;
    }

    private static void collectMetrics(final List<?> statements, final List<MetricsBlock> out) {
        for (final Object s : statements) {
            if (s instanceof MetricsBlock) {
                out.add((MetricsBlock) s);
            } else if (s instanceof ExtractorBlock) {
                collectMetrics(((ExtractorBlock) s).getStatements(), out);
            } else if (s instanceof SinkBlock) {
                collectMetrics(((SinkBlock) s).getStatements(), out);
            } else if (s instanceof PersistBlock) {
                collectMetrics(((PersistBlock) s).getStatements(), out);
            } else if (s instanceof IfBlock) {
                collectMetrics(((IfBlock) s).getThenStatements(), out);
                collectMetrics(((IfBlock) s).getElseStatements(), out);
            }
        }
    }
}
