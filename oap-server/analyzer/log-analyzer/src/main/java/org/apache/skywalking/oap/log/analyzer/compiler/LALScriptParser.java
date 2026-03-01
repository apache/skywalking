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

package org.apache.skywalking.oap.log.analyzer.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.apache.skywalking.lal.rt.grammar.LALLexer;
import org.apache.skywalking.lal.rt.grammar.LALParser;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.AbortStatement;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.InterpolationPart;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.CompareOp;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.ComparisonCondition;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.Condition;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.DropperStatement;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.EnforcerStatement;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.ExprCondition;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.ExtractorBlock;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.ExtractorStatement;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.FieldAssignment;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.FieldSegment;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.FieldType;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.FilterStatement;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.IfBlock;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.JsonParser;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.LogicalCondition;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.LogicalOp;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.MetricsBlock;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.NotCondition;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.NullConditionValue;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.NumberConditionValue;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.RateLimitBlock;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.SamplerBlock;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.SamplerContent;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.SinkBlock;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.SinkStatement;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.SlowSqlBlock;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.StringConditionValue;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.TagAssignment;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.TagValue;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.TextParser;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.ValueAccess;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.ValueAccessConditionValue;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.ValueAccessSegment;
import org.apache.skywalking.oap.log.analyzer.compiler.LALScriptModel.YamlParser;

/**
 * Facade: parses LAL DSL script strings into {@link LALScriptModel}.
 *
 * <pre>
 *   LALScriptModel model = LALScriptParser.parse(
 *       "filter { json {} extractor { service parsed.service as String } sink {} }");
 * </pre>
 */
public final class LALScriptParser {

    private LALScriptParser() {
    }

    public static LALScriptModel parse(final String dsl) {
        final LALLexer lexer = new LALLexer(CharStreams.fromString(dsl));
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final LALParser parser = new LALParser(tokens);

        final List<String> errors = new ArrayList<>();
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer,
                                    final Object offendingSymbol,
                                    final int line,
                                    final int charPositionInLine,
                                    final String msg,
                                    final RecognitionException e) {
                errors.add(line + ":" + charPositionInLine + " " + msg);
            }
        });

        final LALParser.RootContext tree = parser.root();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                "LAL script parsing failed: " + String.join("; ", errors)
                    + " in script: " + truncate(dsl, 200));
        }

        final List<FilterStatement> stmts = visitFilterContent(
            tree.filterBlock().filterContent());
        return new LALScriptModel(stmts);
    }

    // ==================== Filter content ====================

    private static List<FilterStatement> visitFilterContent(
            final LALParser.FilterContentContext ctx) {
        final List<FilterStatement> stmts = new ArrayList<>();
        for (final LALParser.FilterStatementContext fsc : ctx.filterStatement()) {
            stmts.add(visitFilterStatement(fsc));
        }
        return stmts;
    }

    private static FilterStatement visitFilterStatement(
            final LALParser.FilterStatementContext ctx) {
        if (ctx.parserBlock() != null) {
            return visitParserBlock(ctx.parserBlock());
        }
        if (ctx.extractorBlock() != null) {
            return visitExtractorBlock(ctx.extractorBlock());
        }
        if (ctx.sinkBlock() != null) {
            return visitSinkBlock(ctx.sinkBlock());
        }
        if (ctx.abortBlock() != null) {
            return new AbortStatement();
        }
        // ifStatement
        return visitIfStatement(ctx.ifStatement());
    }

    // ==================== Parser blocks ====================

    private static FilterStatement visitParserBlock(final LALParser.ParserBlockContext ctx) {
        if (ctx.textBlock() != null) {
            String pattern = null;
            boolean abortOnFail = false;
            if (ctx.textBlock().textContent() != null) {
                for (final LALParser.RegexpStatementContext regCtx :
                        ctx.textBlock().textContent().regexpStatement()) {
                    pattern = stripQuotes(regCtx.regexpPattern().getText());
                }
                for (final LALParser.AbortOnFailureStatementContext abfCtx :
                        ctx.textBlock().textContent().abortOnFailureStatement()) {
                    abortOnFail = "true".equals(abfCtx.boolValue().getText());
                }
            }
            return new TextParser(pattern, abortOnFail);
        }
        if (ctx.jsonBlock() != null) {
            boolean abortOnFail = false;
            if (ctx.jsonBlock().jsonContent() != null
                    && ctx.jsonBlock().jsonContent().abortOnFailureStatement() != null) {
                abortOnFail = "true".equals(
                    ctx.jsonBlock().jsonContent().abortOnFailureStatement()
                        .boolValue().getText());
            }
            return new JsonParser(abortOnFail);
        }
        // yaml
        boolean abortOnFail = false;
        if (ctx.yamlBlock().yamlContent() != null
                && ctx.yamlBlock().yamlContent().abortOnFailureStatement() != null) {
            abortOnFail = "true".equals(
                ctx.yamlBlock().yamlContent().abortOnFailureStatement()
                    .boolValue().getText());
        }
        return new YamlParser(abortOnFail);
    }

    // ==================== Extractor block ====================

    private static ExtractorBlock visitExtractorBlock(
            final LALParser.ExtractorBlockContext ctx) {
        final List<ExtractorStatement> stmts = new ArrayList<>();
        for (final LALParser.ExtractorStatementContext esc : ctx.extractorContent().extractorStatement()) {
            stmts.add(visitExtractorStatement(esc));
        }
        return new ExtractorBlock(stmts);
    }

    private static ExtractorStatement visitExtractorStatement(
            final LALParser.ExtractorStatementContext ctx) {
        if (ctx.serviceStatement() != null) {
            return visitFieldAssignment(FieldType.SERVICE, ctx.serviceStatement().valueAccess(),
                ctx.serviceStatement().typeCast());
        }
        if (ctx.instanceStatement() != null) {
            return visitFieldAssignment(FieldType.INSTANCE, ctx.instanceStatement().valueAccess(),
                ctx.instanceStatement().typeCast());
        }
        if (ctx.endpointStatement() != null) {
            return visitFieldAssignment(FieldType.ENDPOINT, ctx.endpointStatement().valueAccess(),
                ctx.endpointStatement().typeCast());
        }
        if (ctx.layerStatement() != null) {
            return visitFieldAssignment(FieldType.LAYER, ctx.layerStatement().valueAccess(),
                ctx.layerStatement().typeCast());
        }
        if (ctx.traceIdStatement() != null) {
            return visitFieldAssignment(FieldType.TRACE_ID, ctx.traceIdStatement().valueAccess(),
                ctx.traceIdStatement().typeCast());
        }
        if (ctx.timestampStatement() != null) {
            final ValueAccess va = visitValueAccess(ctx.timestampStatement().valueAccess());
            final String cast = ctx.timestampStatement().typeCast() != null
                ? extractCastType(ctx.timestampStatement().typeCast()) : null;
            String format = null;
            if (ctx.timestampStatement().STRING() != null) {
                format = stripQuotes(ctx.timestampStatement().STRING().getText());
            }
            return new FieldAssignment(FieldType.TIMESTAMP, va, cast, format);
        }
        if (ctx.tagStatement() != null) {
            return visitTagStatement(ctx.tagStatement());
        }
        if (ctx.metricsBlock() != null) {
            return visitMetricsBlock(ctx.metricsBlock());
        }
        if (ctx.slowSqlBlock() != null) {
            return visitSlowSqlBlock(ctx.slowSqlBlock());
        }
        if (ctx.sampledTraceBlock() != null) {
            return visitSampledTraceBlock(ctx.sampledTraceBlock());
        }
        // ifStatement
        return (ExtractorStatement) visitIfStatement(ctx.ifStatement());
    }

    private static FieldAssignment visitFieldAssignment(
            final FieldType type,
            final LALParser.ValueAccessContext vaCtx,
            final LALParser.TypeCastContext tcCtx) {
        final ValueAccess va = visitValueAccess(vaCtx);
        final String cast = tcCtx != null ? extractCastType(tcCtx) : null;
        return new FieldAssignment(type, va, cast, null);
    }

    // ==================== Tag statement ====================

    private static TagAssignment visitTagStatement(final LALParser.TagStatementContext ctx) {
        final Map<String, TagValue> tags = new LinkedHashMap<>();
        if (ctx.tagMap() != null) {
            for (int i = 0; i < ctx.tagMap().anyIdentifier().size(); i++) {
                final String key = ctx.tagMap().anyIdentifier(i).getText();
                final ValueAccess va = visitValueAccess(ctx.tagMap().valueAccess(i));
                final String cast = ctx.tagMap().typeCast(i) != null
                    ? extractCastType(ctx.tagMap().typeCast(i)) : null;
                tags.put(key, new TagValue(va, cast));
            }
        } else if (ctx.STRING() != null) {
            final String key = stripQuotes(ctx.STRING().getText());
            final ValueAccess va = visitValueAccess(ctx.valueAccess());
            final String cast = ctx.typeCast() != null ? extractCastType(ctx.typeCast()) : null;
            tags.put(key, new TagValue(va, cast));
        }
        return new TagAssignment(tags);
    }

    // ==================== Metrics block ====================

    private static MetricsBlock visitMetricsBlock(final LALParser.MetricsBlockContext ctx) {
        String name = null;
        ValueAccess timestampValue = null;
        String timestampCast = null;
        final Map<String, TagValue> labels = new LinkedHashMap<>();
        ValueAccess value = null;
        String valueCast = null;

        for (final LALParser.MetricsStatementContext msc : ctx.metricsContent().metricsStatement()) {
            if (msc.metricsNameStatement() != null) {
                name = resolveValueAsString(msc.metricsNameStatement().valueAccess());
            }
            if (msc.metricsTimestampStatement() != null) {
                timestampValue = visitValueAccess(msc.metricsTimestampStatement().valueAccess());
                timestampCast = msc.metricsTimestampStatement().typeCast() != null
                    ? extractCastType(msc.metricsTimestampStatement().typeCast()) : null;
            }
            if (msc.metricsLabelsStatement() != null) {
                for (final LALParser.LabelEntryContext lec :
                        msc.metricsLabelsStatement().labelMap().labelEntry()) {
                    final String key = lec.anyIdentifier().getText();
                    final ValueAccess va = visitValueAccess(lec.valueAccess());
                    final String cast = lec.typeCast() != null
                        ? extractCastType(lec.typeCast()) : null;
                    labels.put(key, new TagValue(va, cast));
                }
            }
            if (msc.metricsValueStatement() != null) {
                value = visitValueAccess(msc.metricsValueStatement().valueAccess());
                valueCast = msc.metricsValueStatement().typeCast() != null
                    ? extractCastType(msc.metricsValueStatement().typeCast()) : null;
            }
        }

        return new MetricsBlock(name, timestampValue, timestampCast, labels, value, valueCast);
    }

    // ==================== Slow SQL block ====================

    private static SlowSqlBlock visitSlowSqlBlock(final LALParser.SlowSqlBlockContext ctx) {
        ValueAccess id = null;
        String idCast = null;
        ValueAccess statement = null;
        String statementCast = null;
        ValueAccess latency = null;
        String latencyCast = null;

        for (final LALParser.SlowSqlStatementContext ssc :
                ctx.slowSqlContent().slowSqlStatement()) {
            if (ssc.slowSqlIdStatement() != null) {
                id = visitValueAccess(ssc.slowSqlIdStatement().valueAccess());
                idCast = ssc.slowSqlIdStatement().typeCast() != null
                    ? extractCastType(ssc.slowSqlIdStatement().typeCast()) : null;
            }
            if (ssc.slowSqlStatementStatement() != null) {
                statement = visitValueAccess(ssc.slowSqlStatementStatement().valueAccess());
                statementCast = ssc.slowSqlStatementStatement().typeCast() != null
                    ? extractCastType(ssc.slowSqlStatementStatement().typeCast()) : null;
            }
            if (ssc.slowSqlLatencyStatement() != null) {
                latency = visitValueAccess(ssc.slowSqlLatencyStatement().valueAccess());
                latencyCast = ssc.slowSqlLatencyStatement().typeCast() != null
                    ? extractCastType(ssc.slowSqlLatencyStatement().typeCast()) : null;
            }
        }

        return new SlowSqlBlock(id, idCast, statement, statementCast, latency, latencyCast);
    }

    // ==================== Sampled trace block ====================

    private static LALScriptModel.SampledTraceBlock visitSampledTraceBlock(
            final LALParser.SampledTraceBlockContext ctx) {
        final List<LALScriptModel.SampledTraceStatement> stmts = new ArrayList<>();
        for (final LALParser.SampledTraceStatementContext stc :
                ctx.sampledTraceContent().sampledTraceStatement()) {
            if (stc.ifStatement() != null) {
                stmts.add((LALScriptModel.SampledTraceStatement) visitIfStatement(
                    stc.ifStatement()));
            } else {
                stmts.add(visitSampledTraceField(stc));
            }
        }
        return new LALScriptModel.SampledTraceBlock(stmts);
    }

    private static LALScriptModel.SampledTraceField visitSampledTraceField(
            final LALParser.SampledTraceStatementContext ctx) {
        if (ctx.sampledTraceLatencyStatement() != null) {
            return makeSampledField(LALScriptModel.SampledTraceFieldType.LATENCY,
                ctx.sampledTraceLatencyStatement().valueAccess(),
                ctx.sampledTraceLatencyStatement().typeCast());
        }
        if (ctx.sampledTraceUriStatement() != null) {
            return makeSampledField(LALScriptModel.SampledTraceFieldType.URI,
                ctx.sampledTraceUriStatement().valueAccess(),
                ctx.sampledTraceUriStatement().typeCast());
        }
        if (ctx.sampledTraceReasonStatement() != null) {
            return makeSampledField(LALScriptModel.SampledTraceFieldType.REASON,
                ctx.sampledTraceReasonStatement().valueAccess(),
                ctx.sampledTraceReasonStatement().typeCast());
        }
        if (ctx.sampledTraceProcessIdStatement() != null) {
            return makeSampledField(LALScriptModel.SampledTraceFieldType.PROCESS_ID,
                ctx.sampledTraceProcessIdStatement().valueAccess(),
                ctx.sampledTraceProcessIdStatement().typeCast());
        }
        if (ctx.sampledTraceDestProcessIdStatement() != null) {
            return makeSampledField(LALScriptModel.SampledTraceFieldType.DEST_PROCESS_ID,
                ctx.sampledTraceDestProcessIdStatement().valueAccess(),
                ctx.sampledTraceDestProcessIdStatement().typeCast());
        }
        if (ctx.sampledTraceDetectPointStatement() != null) {
            return makeSampledField(LALScriptModel.SampledTraceFieldType.DETECT_POINT,
                ctx.sampledTraceDetectPointStatement().valueAccess(),
                ctx.sampledTraceDetectPointStatement().typeCast());
        }
        if (ctx.sampledTraceComponentIdStatement() != null) {
            return makeSampledField(LALScriptModel.SampledTraceFieldType.COMPONENT_ID,
                ctx.sampledTraceComponentIdStatement().valueAccess(),
                ctx.sampledTraceComponentIdStatement().typeCast());
        }
        // reportService
        return makeSampledField(LALScriptModel.SampledTraceFieldType.REPORT_SERVICE,
            ctx.reportServiceStatement().valueAccess(),
            ctx.reportServiceStatement().typeCast());
    }

    private static LALScriptModel.SampledTraceField makeSampledField(
            final LALScriptModel.SampledTraceFieldType type,
            final LALParser.ValueAccessContext vaCtx,
            final LALParser.TypeCastContext tcCtx) {
        return new LALScriptModel.SampledTraceField(
            type, visitValueAccess(vaCtx),
            tcCtx != null ? extractCastType(tcCtx) : null);
    }

    // ==================== Sink block ====================

    private static SinkBlock visitSinkBlock(final LALParser.SinkBlockContext ctx) {
        final List<SinkStatement> stmts = new ArrayList<>();
        for (final LALParser.SinkStatementContext ssc : ctx.sinkContent().sinkStatement()) {
            if (ssc.samplerBlock() != null) {
                stmts.add(visitSamplerBlock(ssc.samplerBlock()));
            } else if (ssc.enforcerStatement() != null) {
                stmts.add(new EnforcerStatement());
            } else if (ssc.dropperStatement() != null) {
                stmts.add(new DropperStatement());
            } else {
                stmts.add((SinkStatement) visitIfStatement(ssc.ifStatement()));
            }
        }
        return new SinkBlock(stmts);
    }

    private static SamplerBlock visitSamplerBlock(final LALParser.SamplerBlockContext ctx) {
        final List<SamplerContent> contents = new ArrayList<>();
        for (final LALParser.RateLimitBlockContext rlc : ctx.samplerContent().rateLimitBlock()) {
            final String id = stripQuotes(rlc.rateLimitId().getText());
            final long rpm = Long.parseLong(rlc.rateLimitContent().NUMBER().getText());
            final List<InterpolationPart> idParts = parseInterpolation(id);
            contents.add(new RateLimitBlock(id, idParts, rpm));
        }
        for (final LALParser.IfStatementContext isc : ctx.samplerContent().ifStatement()) {
            contents.add((SamplerContent) visitIfStatement(isc));
        }
        return new SamplerBlock(contents);
    }

    // ==================== If statement ====================

    private static IfBlock visitIfStatement(final LALParser.IfStatementContext ctx) {
        final int condCount = ctx.condition().size();
        final int bodyCount = ctx.ifBody().size();
        // Whether there is a trailing else (no condition) block
        final boolean hasElse = bodyCount > condCount;

        // Build the chain from the last else-if backwards.
        // For: if(A){b0} else if(B){b1} else if(C){b2} else{b3}
        //   condCount=3, bodyCount=4, hasElse=true
        //   Result: IfBlock(A, b0, IfBlock(B, b1, IfBlock(C, b2, b3)))

        // Start from the innermost else-if (last condition)
        List<FilterStatement> trailingElse = hasElse
            ? visitIfBody(ctx.ifBody(bodyCount - 1)) : null;

        // Build from the last condition backwards to index 1
        IfBlock nested = null;
        for (int i = condCount - 1; i >= 1; i--) {
            final Condition cond = visitCondition(ctx.condition(i));
            final List<FilterStatement> body = visitIfBody(ctx.ifBody(i));
            final List<FilterStatement> elsePart;
            if (nested != null) {
                elsePart = List.of(nested);
            } else {
                elsePart = trailingElse;
            }
            nested = new IfBlock(cond, body, elsePart);
        }

        // Build the outermost if block (index 0)
        final Condition topCond = visitCondition(ctx.condition(0));
        final List<FilterStatement> topBody = visitIfBody(ctx.ifBody(0));
        final List<FilterStatement> topElse;
        if (nested != null) {
            topElse = List.of(nested);
        } else {
            topElse = trailingElse;
        }

        return new IfBlock(topCond, topBody, topElse);
    }

    private static List<FilterStatement> visitIfBody(final LALParser.IfBodyContext ctx) {
        final List<FilterStatement> stmts = new ArrayList<>();
        for (final LALParser.FilterStatementContext fsc : ctx.filterStatement()) {
            stmts.add(visitFilterStatement(fsc));
        }
        for (final LALParser.ExtractorStatementContext esc : ctx.extractorStatement()) {
            stmts.add((FilterStatement) visitExtractorStatement(esc));
        }
        for (final LALParser.SinkStatementContext ssc : ctx.sinkStatement()) {
            if (ssc.samplerBlock() != null) {
                stmts.add((FilterStatement) visitSamplerBlock(ssc.samplerBlock()));
            } else if (ssc.enforcerStatement() != null) {
                stmts.add((FilterStatement) new EnforcerStatement());
            } else if (ssc.dropperStatement() != null) {
                stmts.add((FilterStatement) new DropperStatement());
            }
        }
        for (final LALParser.SampledTraceStatementContext stc :
                ctx.sampledTraceStatement()) {
            if (stc.ifStatement() != null) {
                stmts.add((FilterStatement) visitIfStatement(stc.ifStatement()));
            } else {
                stmts.add((FilterStatement) visitSampledTraceField(stc));
            }
        }
        return stmts;
    }

    // ==================== Conditions ====================

    private static Condition visitCondition(final LALParser.ConditionContext ctx) {
        if (ctx instanceof LALParser.CondAndContext) {
            final LALParser.CondAndContext and = (LALParser.CondAndContext) ctx;
            return new LogicalCondition(
                visitCondition(and.condition(0)),
                LogicalOp.AND,
                visitCondition(and.condition(1)));
        }
        if (ctx instanceof LALParser.CondOrContext) {
            final LALParser.CondOrContext or = (LALParser.CondOrContext) ctx;
            return new LogicalCondition(
                visitCondition(or.condition(0)),
                LogicalOp.OR,
                visitCondition(or.condition(1)));
        }
        if (ctx instanceof LALParser.CondNotContext) {
            return new NotCondition(
                visitCondition(((LALParser.CondNotContext) ctx).condition()));
        }
        if (ctx instanceof LALParser.CondParenContext) {
            return visitCondition(((LALParser.CondParenContext) ctx).condition());
        }
        if (ctx instanceof LALParser.CondEqContext) {
            final LALParser.CondEqContext eq = (LALParser.CondEqContext) ctx;
            return makeComparison(eq.conditionExpr(0), CompareOp.EQ, eq.conditionExpr(1));
        }
        if (ctx instanceof LALParser.CondNeqContext) {
            final LALParser.CondNeqContext neq = (LALParser.CondNeqContext) ctx;
            return makeComparison(neq.conditionExpr(0), CompareOp.NEQ, neq.conditionExpr(1));
        }
        if (ctx instanceof LALParser.CondGtContext) {
            final LALParser.CondGtContext gt = (LALParser.CondGtContext) ctx;
            return makeComparison(gt.conditionExpr(0), CompareOp.GT, gt.conditionExpr(1));
        }
        if (ctx instanceof LALParser.CondLtContext) {
            final LALParser.CondLtContext lt = (LALParser.CondLtContext) ctx;
            return makeComparison(lt.conditionExpr(0), CompareOp.LT, lt.conditionExpr(1));
        }
        if (ctx instanceof LALParser.CondGteContext) {
            final LALParser.CondGteContext gte = (LALParser.CondGteContext) ctx;
            return makeComparison(gte.conditionExpr(0), CompareOp.GTE, gte.conditionExpr(1));
        }
        if (ctx instanceof LALParser.CondLteContext) {
            final LALParser.CondLteContext lte = (LALParser.CondLteContext) ctx;
            return makeComparison(lte.conditionExpr(0), CompareOp.LTE, lte.conditionExpr(1));
        }
        // condSingle
        final LALParser.CondSingleContext single = (LALParser.CondSingleContext) ctx;
        return visitConditionExprAsCondition(single.conditionExpr());
    }

    private static Condition makeComparison(
            final LALParser.ConditionExprContext leftCtx,
            final CompareOp op,
            final LALParser.ConditionExprContext rightCtx) {
        if (leftCtx instanceof LALParser.CondValueAccessContext) {
            final LALParser.CondValueAccessContext lva =
                (LALParser.CondValueAccessContext) leftCtx;
            final ValueAccess left = visitValueAccess(lva.valueAccess());
            final String leftCast = lva.typeCast() != null
                ? extractCastType(lva.typeCast()) : null;
            return new ComparisonCondition(left, leftCast, op,
                visitConditionExprAsValue(rightCtx));
        }
        if (leftCtx instanceof LALParser.CondFunctionCallContext) {
            final LALParser.FunctionInvocationContext fi =
                ((LALParser.CondFunctionCallContext) leftCtx).functionInvocation();
            final String funcName = fi.functionName().getText();
            final List<LALScriptModel.FunctionArg> funcArgs = visitFunctionArgs(fi);
            final ValueAccess left = new ValueAccess(
                List.of(fi.getText()), false, false, false, false, false,
                List.of(), funcName, funcArgs);
            return new ComparisonCondition(left, null, op,
                visitConditionExprAsValue(rightCtx));
        }
        // For other forms, wrap as expression condition
        return new ExprCondition(
            new ValueAccess(List.of(leftCtx.getText()), false, false, List.of()), null);
    }

    private static LALScriptModel.ConditionValue visitConditionExprAsValue(
            final LALParser.ConditionExprContext ctx) {
        if (ctx instanceof LALParser.CondStringContext) {
            return new StringConditionValue(
                stripQuotes(((LALParser.CondStringContext) ctx).STRING().getText()));
        }
        if (ctx instanceof LALParser.CondNumberContext) {
            return new NumberConditionValue(
                Double.parseDouble(((LALParser.CondNumberContext) ctx).NUMBER().getText()));
        }
        if (ctx instanceof LALParser.CondNullContext) {
            return new NullConditionValue();
        }
        if (ctx instanceof LALParser.CondValueAccessContext) {
            final LALParser.CondValueAccessContext va =
                (LALParser.CondValueAccessContext) ctx;
            final String cast = va.typeCast() != null ? extractCastType(va.typeCast()) : null;
            return new ValueAccessConditionValue(visitValueAccess(va.valueAccess()), cast);
        }
        // condBool, condFunctionCall
        return new StringConditionValue(ctx.getText());
    }

    private static Condition visitConditionExprAsCondition(
            final LALParser.ConditionExprContext ctx) {
        if (ctx instanceof LALParser.CondValueAccessContext) {
            final LALParser.CondValueAccessContext va =
                (LALParser.CondValueAccessContext) ctx;
            final String cast = va.typeCast() != null ? extractCastType(va.typeCast()) : null;
            return new ExprCondition(visitValueAccess(va.valueAccess()), cast);
        }
        if (ctx instanceof LALParser.CondFunctionCallContext) {
            final LALParser.FunctionInvocationContext fi =
                ((LALParser.CondFunctionCallContext) ctx).functionInvocation();
            final String funcName = fi.functionName().getText();
            final List<LALScriptModel.FunctionArg> funcArgs = visitFunctionArgs(fi);
            final ValueAccess va = new ValueAccess(
                List.of(fi.getText()), false, false, false, false, false,
                List.of(), funcName, funcArgs);
            return new ExprCondition(va, null);
        }
        return new ExprCondition(
            new ValueAccess(List.of(ctx.getText()), false, false, List.of()), null);
    }

    // ==================== Value access ====================

    private static ValueAccess visitValueAccess(final LALParser.ValueAccessContext ctx) {
        final List<String> segments = new ArrayList<>();
        boolean parsedRef = false;
        boolean logRef = false;
        boolean processRegistryRef = false;
        boolean stringLiteral = false;
        boolean numberLiteral = false;
        String functionCallName = null;
        List<LALScriptModel.FunctionArg> functionCallArgs = null;

        final LALParser.ValueAccessPrimaryContext primary = ctx.valueAccessPrimary();
        if (primary instanceof LALParser.ValueParsedContext) {
            parsedRef = true;
            segments.add("parsed");
        } else if (primary instanceof LALParser.ValueLogContext) {
            logRef = true;
            segments.add("log");
        } else if (primary instanceof LALParser.ValueProcessRegistryContext) {
            processRegistryRef = true;
            segments.add("ProcessRegistry");
        } else if (primary instanceof LALParser.ValueIdentifierContext) {
            segments.add(((LALParser.ValueIdentifierContext) primary).IDENTIFIER().getText());
        } else if (primary instanceof LALParser.ValueStringContext) {
            stringLiteral = true;
            segments.add(stripQuotes(
                ((LALParser.ValueStringContext) primary).STRING().getText()));
        } else if (primary instanceof LALParser.ValueNumberContext) {
            numberLiteral = true;
            segments.add(((LALParser.ValueNumberContext) primary).NUMBER().getText());
        } else if (primary instanceof LALParser.ValueFunctionCallContext) {
            final LALParser.FunctionInvocationContext fi =
                ((LALParser.ValueFunctionCallContext) primary).functionInvocation();
            functionCallName = fi.functionName().getText();
            functionCallArgs = visitFunctionArgs(fi);
            segments.add(fi.getText());
        } else {
            segments.add(primary.getText());
        }

        final List<ValueAccessSegment> chain = new ArrayList<>();
        for (final LALParser.ValueAccessSegmentContext seg : ctx.valueAccessSegment()) {
            if (seg instanceof LALParser.SegmentFieldContext) {
                final String name =
                    ((LALParser.SegmentFieldContext) seg).anyIdentifier().getText();
                segments.add(name);
                chain.add(new FieldSegment(name, false));
            } else if (seg instanceof LALParser.SegmentSafeFieldContext) {
                final String name =
                    ((LALParser.SegmentSafeFieldContext) seg).anyIdentifier().getText();
                segments.add(name);
                chain.add(new FieldSegment(name, true));
            } else if (seg instanceof LALParser.SegmentMethodContext) {
                final LALParser.FunctionInvocationContext fi =
                    ((LALParser.SegmentMethodContext) seg).functionInvocation();
                segments.add(fi.functionName().getText() + "()");
                chain.add(new LALScriptModel.MethodSegment(
                    fi.functionName().getText(), visitFunctionArgs(fi), false));
            } else if (seg instanceof LALParser.SegmentSafeMethodContext) {
                final LALParser.FunctionInvocationContext fi =
                    ((LALParser.SegmentSafeMethodContext) seg).functionInvocation();
                segments.add(fi.functionName().getText() + "()");
                chain.add(new LALScriptModel.MethodSegment(
                    fi.functionName().getText(), visitFunctionArgs(fi), true));
            }
        }

        return new ValueAccess(segments, parsedRef, logRef,
            processRegistryRef, stringLiteral, numberLiteral,
            chain, functionCallName, functionCallArgs);
    }

    private static List<LALScriptModel.FunctionArg> visitFunctionArgs(
            final LALParser.FunctionInvocationContext fi) {
        if (fi.functionArgList() == null) {
            return List.of();
        }
        final List<LALScriptModel.FunctionArg> args = new ArrayList<>();
        for (final LALParser.FunctionArgContext fac : fi.functionArgList().functionArg()) {
            if (fac.valueAccess() != null) {
                final ValueAccess va = visitValueAccess(fac.valueAccess());
                final String cast = fac.typeCast() != null
                    ? extractCastType(fac.typeCast()) : null;
                args.add(new LALScriptModel.FunctionArg(va, cast));
            } else if (fac.STRING() != null) {
                final String val = stripQuotes(fac.STRING().getText());
                final ValueAccess va = new ValueAccess(
                    List.of(val), false, false, true, true, false,
                    List.of(), null, null);
                args.add(new LALScriptModel.FunctionArg(va, null));
            } else if (fac.NUMBER() != null) {
                final ValueAccess va = new ValueAccess(
                    List.of(fac.NUMBER().getText()), false, false,
                    false, false, true, List.of(), null, null);
                args.add(new LALScriptModel.FunctionArg(va, null));
            } else if (fac.boolValue() != null) {
                final ValueAccess va = new ValueAccess(
                    List.of(fac.boolValue().getText()), false, false,
                    false, false, false, List.of(), null, null);
                args.add(new LALScriptModel.FunctionArg(va, null));
            } else {
                // NULL
                final ValueAccess va = new ValueAccess(
                    List.of("null"), false, false, List.of());
                args.add(new LALScriptModel.FunctionArg(va, null));
            }
        }
        return args;
    }

    private static String resolveValueAsString(final LALParser.ValueAccessContext ctx) {
        final LALParser.ValueAccessPrimaryContext primary = ctx.valueAccessPrimary();
        if (primary instanceof LALParser.ValueStringContext) {
            return stripQuotes(((LALParser.ValueStringContext) primary).STRING().getText());
        }
        return primary.getText();
    }

    // ==================== Utilities ====================

    private static String extractCastType(final LALParser.TypeCastContext ctx) {
        if (ctx.STRING_TYPE() != null) {
            return "String";
        }
        if (ctx.LONG_TYPE() != null) {
            return "Long";
        }
        if (ctx.INTEGER_TYPE() != null) {
            return "Integer";
        }
        if (ctx.BOOLEAN_TYPE() != null) {
            return "Boolean";
        }
        return null;
    }

    static String stripQuotes(final String s) {
        if (s == null || s.length() < 2) {
            return s;
        }
        final char first = s.charAt(0);
        if ((first == '\'' || first == '"') && s.charAt(s.length() - 1) == first) {
            return s.substring(1, s.length() - 1);
        }
        // Handle slashy strings: $/ ... /$
        if (s.startsWith("$/") && s.endsWith("/$")) {
            return s.substring(2, s.length() - 2);
        }
        return s;
    }

    private static String truncate(final String s, final int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }

    // ==================== GString interpolation ====================

    /**
     * Parses Groovy-style GString interpolation in a string.
     * E.g. {@code "${log.service}:${parsed?.field}"} produces
     * [expr(log.service), literal(":"), expr(parsed?.field)].
     *
     * @return list of parts, or {@code null} if no interpolation found
     */
    static List<InterpolationPart> parseInterpolation(final String s) {
        if (!s.contains("${")) {
            return null;
        }
        final List<InterpolationPart> parts = new ArrayList<>();
        int pos = 0;
        while (pos < s.length()) {
            final int start = s.indexOf("${", pos);
            if (start < 0) {
                // Remaining literal text
                if (pos < s.length()) {
                    parts.add(InterpolationPart.ofLiteral(s.substring(pos)));
                }
                break;
            }
            // Literal text before ${
            if (start > pos) {
                parts.add(InterpolationPart.ofLiteral(s.substring(pos, start)));
            }
            // Find matching closing brace, respecting nesting
            int depth = 1;
            int i = start + 2;
            while (i < s.length() && depth > 0) {
                final char c = s.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
                i++;
            }
            if (depth != 0) {
                throw new IllegalArgumentException(
                    "Unclosed interpolation in: " + s);
            }
            final String expr = s.substring(start + 2, i - 1);
            // Parse the expression as a valueAccess through ANTLR
            parts.add(InterpolationPart.ofExpression(parseValueAccessExpr(expr)));
            pos = i;
        }
        return parts;
    }

    /**
     * Parses a standalone valueAccess expression string by wrapping it in
     * a minimal LAL script and extracting the parsed ValueAccess.
     */
    private static ValueAccess parseValueAccessExpr(final String expr) {
        // Wrap in: filter { if (EXPR) { sink {} } }
        // The expression becomes a condition, parsed as ExprCondition
        // whose ValueAccess is what we want.
        final String wrapper = "filter { if (" + expr + ") { sink {} } }";
        final LALScriptModel model = parse(wrapper);
        final IfBlock ifBlock = (IfBlock) model.getStatements().get(0);
        final LALScriptModel.Condition cond = ifBlock.getCondition();
        if (cond instanceof ExprCondition) {
            return ((ExprCondition) cond).getExpr();
        }
        if (cond instanceof ComparisonCondition) {
            return ((ComparisonCondition) cond).getLeft();
        }
        throw new IllegalArgumentException(
            "Cannot parse interpolation expression: " + expr);
    }
}
