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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.skywalking.lal.rt.grammar.LALLexer;
import org.apache.skywalking.lal.rt.grammar.LALParser;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.AbortStatement;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.DefStatement;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.InterpolationPart;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.CompareOp;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.ComparisonCondition;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.Condition;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.DropperStatement;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.EnforcerStatement;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.ExprCondition;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.ExtractorBlock;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.ExtractorStatement;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.FieldAssignment;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.FieldSegment;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.FieldType;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.FilterStatement;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.IfBlock;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.JsonParser;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.LogicalCondition;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.LogicalOp;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.MetricsBlock;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.NotCondition;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.NullConditionValue;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.NumberConditionValue;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.RateLimitBlock;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.SamplerBlock;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.SamplerContent;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.SinkBlock;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.SinkStatement;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.StringConditionValue;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.TagAssignment;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.TagValue;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.TextParser;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.ValueAccess;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.ValueAccessConditionValue;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.ValueAccessSegment;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.IndexSegment;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALScriptModel.YamlParser;

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
        if (ctx.defStatement() != null) {
            return visitDefStatement(ctx.defStatement());
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
                    abortOnFail = parseBoolText(abfCtx.boolValue().getText());
                }
            }
            return new TextParser(pattern, abortOnFail);
        }
        if (ctx.jsonBlock() != null) {
            return new JsonParser(extractAbortOnFail(
                ctx.jsonBlock().jsonContent() != null
                    ? ctx.jsonBlock().jsonContent().abortOnFailureStatement() : null));
        }
        return new YamlParser(extractAbortOnFail(
            ctx.yamlBlock().yamlContent() != null
                ? ctx.yamlBlock().yamlContent().abortOnFailureStatement() : null));
    }

    /**
     * Extract the {@code abortOnFailure} flag from an optional
     * {@code abortOnFailureStatement} node, defaulting to {@code false}
     * when the statement is absent. Shared between the JSON and YAML
     * parser blocks (text uses a list form, handled inline).
     */
    private static boolean extractAbortOnFail(
            final LALParser.AbortOnFailureStatementContext ctx) {
        return ctx != null && parseBoolText(ctx.boolValue().getText());
    }

    private static boolean parseBoolText(final String text) {
        return "true".equals(text);
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
        if (ctx.defStatement() != null) {
            return (ExtractorStatement) visitDefStatement(ctx.defStatement());
        }
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
            final String cast = extractCastType(ctx.timestampStatement().typeCast());
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
        if (ctx.outputFieldStatement() != null) {
            return visitOutputFieldStatement(ctx.outputFieldStatement());
        }
        // ifStatement
        return (ExtractorStatement) visitIfStatement(ctx.ifStatement());
    }

    private static FieldAssignment visitFieldAssignment(
            final FieldType type,
            final LALParser.ValueAccessContext vaCtx,
            final LALParser.TypeCastContext tcCtx) {
        final ValueAccess va = visitValueAccess(vaCtx);
        final String cast = extractCastType(tcCtx);
        return new FieldAssignment(type, va, cast, null);
    }

    private static LALScriptModel.OutputFieldAssignment visitOutputFieldStatement(
            final LALParser.OutputFieldStatementContext ctx) {
        final String fieldName = ctx.anyIdentifier().getText();
        final ValueAccess value = visitValueAccess(ctx.valueAccess());
        final String castType = extractCastType(ctx.typeCast());
        return new LALScriptModel.OutputFieldAssignment(fieldName, value, castType);
    }

    // ==================== Def statement ====================

    private static DefStatement visitDefStatement(
            final LALParser.DefStatementContext ctx) {
        final String varName = ctx.IDENTIFIER().getText();
        final ValueAccess initializer = visitValueAccess(ctx.valueAccess());
        final String castType = extractCastType(ctx.typeCast());
        return new DefStatement(varName, initializer, castType);
    }

    // ==================== Tag statement ====================

    private static TagAssignment visitTagStatement(final LALParser.TagStatementContext ctx) {
        final Map<String, TagValue> tags = new LinkedHashMap<>();
        if (ctx.tagMap() != null) {
            for (int i = 0; i < ctx.tagMap().anyIdentifier().size(); i++) {
                final String key = ctx.tagMap().anyIdentifier(i).getText();
                final ValueAccess va = visitValueAccess(ctx.tagMap().valueAccess(i));
                final String cast = extractCastType(ctx.tagMap().typeCast(i));
                tags.put(key, new TagValue(va, cast));
            }
        } else if (ctx.STRING() != null) {
            final String key = stripQuotes(ctx.STRING().getText());
            final ValueAccess va = visitValueAccess(ctx.valueAccess());
            final String cast = extractCastType(ctx.typeCast());
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
                timestampCast = extractCastType(msc.metricsTimestampStatement().typeCast());
            }
            if (msc.metricsLabelsStatement() != null) {
                for (final LALParser.LabelEntryContext lec :
                        msc.metricsLabelsStatement().labelMap().labelEntry()) {
                    final String key = lec.anyIdentifier().getText();
                    final ValueAccess va = visitValueAccess(lec.valueAccess());
                    final String cast = extractCastType(lec.typeCast());
                    labels.put(key, new TagValue(va, cast));
                }
            }
            if (msc.metricsValueStatement() != null) {
                value = visitValueAccess(msc.metricsValueStatement().valueAccess());
                valueCast = extractCastType(msc.metricsValueStatement().typeCast());
            }
        }

        return new MetricsBlock(name, timestampValue, timestampCast, labels, value, valueCast);
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
            contents.add(visitRateLimitBlock(rlc));
        }
        for (final LALParser.IfStatementContext isc : ctx.samplerContent().ifStatement()) {
            contents.add((SamplerContent) visitIfStatement(isc));
        }
        return new SamplerBlock(contents);
    }

    private static RateLimitBlock visitRateLimitBlock(
            final LALParser.RateLimitBlockContext rlc) {
        final String id = stripQuotes(rlc.rateLimitId().getText());
        final long rpm = parseStrictInteger(
            rlc.rateLimitContent().NUMBER().getText(), "rpm");
        return new RateLimitBlock(id, parseInterpolation(id), rpm);
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
        // Handle samplerContent alternative (rateLimit blocks inside if within sampler)
        final LALParser.SamplerContentContext sc = ctx.samplerContent();
        if (sc != null) {
            final List<SamplerContent> samplerItems = new ArrayList<>();
            for (final LALParser.RateLimitBlockContext rlc : sc.rateLimitBlock()) {
                samplerItems.add(visitRateLimitBlock(rlc));
            }
            for (final LALParser.IfStatementContext isc : sc.ifStatement()) {
                samplerItems.add((SamplerContent) visitIfStatement(isc));
            }
            if (!samplerItems.isEmpty()) {
                stmts.add((FilterStatement) new SamplerBlock(samplerItems));
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
        if (ctx instanceof LALParser.CondEqContext) {
            final LALParser.CondEqContext eq = (LALParser.CondEqContext) ctx;
            return visitComparison(eq.conditionExpr(0), CompareOp.EQ, eq.conditionExpr(1));
        }
        if (ctx instanceof LALParser.CondNeqContext) {
            final LALParser.CondNeqContext neq = (LALParser.CondNeqContext) ctx;
            return visitComparison(neq.conditionExpr(0), CompareOp.NEQ, neq.conditionExpr(1));
        }
        if (ctx instanceof LALParser.CondGtContext) {
            final LALParser.CondGtContext gt = (LALParser.CondGtContext) ctx;
            return visitComparison(gt.conditionExpr(0), CompareOp.GT, gt.conditionExpr(1));
        }
        if (ctx instanceof LALParser.CondLtContext) {
            final LALParser.CondLtContext lt = (LALParser.CondLtContext) ctx;
            return visitComparison(lt.conditionExpr(0), CompareOp.LT, lt.conditionExpr(1));
        }
        if (ctx instanceof LALParser.CondGteContext) {
            final LALParser.CondGteContext gte = (LALParser.CondGteContext) ctx;
            return visitComparison(gte.conditionExpr(0), CompareOp.GTE, gte.conditionExpr(1));
        }
        if (ctx instanceof LALParser.CondLteContext) {
            final LALParser.CondLteContext lte = (LALParser.CondLteContext) ctx;
            return visitComparison(lte.conditionExpr(0), CompareOp.LTE, lte.conditionExpr(1));
        }
        // condSingle
        final LALParser.CondSingleContext single = (LALParser.CondSingleContext) ctx;
        return visitConditionExprAsCondition(single.conditionExpr());
    }

    private static Condition visitComparison(
            final LALParser.ConditionExprContext leftCtx,
            final CompareOp op,
            final LALParser.ConditionExprContext rightCtx) {
        if (leftCtx instanceof LALParser.CondValueAccessContext) {
            final LALParser.CondValueAccessContext lva =
                (LALParser.CondValueAccessContext) leftCtx;
            final ValueAccess left = visitValueAccess(lva.valueAccess());
            final String leftCast = extractCastType(lva.typeCast());
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
            final String numText =
                ((LALParser.CondNumberContext) ctx).NUMBER().getText();
            return new NumberConditionValue(parseLiteralAsDouble(numText), numText);
        }
        if (ctx instanceof LALParser.CondNullContext) {
            return new NullConditionValue();
        }
        if (ctx instanceof LALParser.CondValueAccessContext) {
            final LALParser.CondValueAccessContext va =
                (LALParser.CondValueAccessContext) ctx;
            // ANTLR grammar routes NUMBER/NULL/STRING/bool through condValueAccess
            // (since valueAccessPrimary includes them and condValueAccess has priority).
            // Detect standalone literals and create proper ConditionValue types.
            final LALParser.ValueAccessContext vaCtx = va.valueAccess();
            final LALParser.ValueAccessTermContext singleTerm = singleTermOf(vaCtx);
            if (va.typeCast() == null && singleTerm != null
                    && singleTerm.valueAccessSegment().isEmpty()) {
                final LALParser.ValueAccessPrimaryContext primary =
                    singleTerm.valueAccessPrimary();
                if (primary instanceof LALParser.ValueNumberContext) {
                    final String numText =
                        ((LALParser.ValueNumberContext) primary).NUMBER().getText();
                    return new NumberConditionValue(parseLiteralAsDouble(numText), numText);
                }
                if (primary instanceof LALParser.ValueNullContext) {
                    return new NullConditionValue();
                }
            }
            final String cast = extractCastType(va.typeCast());
            return new ValueAccessConditionValue(visitValueAccess(vaCtx), cast);
        }
        if (ctx instanceof LALParser.CondParenGroupContext) {
            // (condition) used as a value — e.g. in: if ((x == y)) { ... }
            // Wrap as a ValueAccess containing the paren expression text
            return new ValueAccessConditionValue(
                new ValueAccess(List.of(ctx.getText()), false, false, List.of()), null);
        }
        // condBool, condFunctionCall
        return new StringConditionValue(ctx.getText());
    }

    private static Condition visitConditionExprAsCondition(
            final LALParser.ConditionExprContext ctx) {
        if (ctx instanceof LALParser.CondValueAccessContext) {
            final LALParser.CondValueAccessContext va =
                (LALParser.CondValueAccessContext) ctx;
            final String cast = extractCastType(va.typeCast());
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
        if (ctx instanceof LALParser.CondParenGroupContext) {
            return visitCondition(
                ((LALParser.CondParenGroupContext) ctx).condition());
        }
        return new ExprCondition(
            new ValueAccess(List.of(ctx.getText()), false, false, List.of()), null);
    }

    // ==================== Value access ====================

    private static ValueAccess visitValueAccess(final LALParser.ValueAccessContext ctx) {
        return visitValueAccessAdd(ctx.valueAccessAdd());
    }

    private static ValueAccess visitValueAccessAdd(final LALParser.ValueAccessAddContext ctx) {
        final List<LALParser.ValueAccessMulContext> muls = ctx.valueAccessMul();
        if (muls.size() == 1) {
            return visitValueAccessMul(muls.get(0));
        }
        final List<ValueAccess> parts = new ArrayList<>(muls.size());
        for (final LALParser.ValueAccessMulContext mul : muls) {
            parts.add(visitValueAccessMul(mul));
        }
        return binaryExpr(parts, collectInfixOps(ctx,
            LALLexer.PLUS, LALScriptModel.BinaryOp.PLUS,
            LALLexer.MINUS, LALScriptModel.BinaryOp.MINUS));
    }

    private static ValueAccess visitValueAccessMul(final LALParser.ValueAccessMulContext ctx) {
        final List<LALParser.ValueAccessTermContext> terms = ctx.valueAccessTerm();
        if (terms.size() == 1) {
            return visitValueAccessTerm(terms.get(0));
        }
        final List<ValueAccess> parts = new ArrayList<>(terms.size());
        for (final LALParser.ValueAccessTermContext term : terms) {
            parts.add(visitValueAccessTerm(term));
        }
        return binaryExpr(parts, collectInfixOps(ctx,
            LALLexer.STAR, LALScriptModel.BinaryOp.STAR,
            LALLexer.SLASH, LALScriptModel.BinaryOp.SLASH));
    }

    /**
     * Walk a parser context's terminal children to recover the infix
     * operator sequence (one operator per gap between operands). Used by
     * the additive and multiplicative visitors to rebuild operator order
     * from ANTLR's flattened child list.
     */
    private static List<LALScriptModel.BinaryOp> collectInfixOps(
            final ParserRuleContext ctx,
            final int firstToken, final LALScriptModel.BinaryOp firstOp,
            final int secondToken, final LALScriptModel.BinaryOp secondOp) {
        final List<LALScriptModel.BinaryOp> ops = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            final ParseTree child = ctx.getChild(i);
            if (!(child instanceof TerminalNode)) {
                continue;
            }
            final int type = ((TerminalNode) child).getSymbol().getType();
            if (type == firstToken) {
                ops.add(firstOp);
            } else if (type == secondToken) {
                ops.add(secondOp);
            }
        }
        return ops;
    }

    /**
     * Wrap a list of operands and operators as a {@link ValueAccess}
     * representing a binary expression node — used by the additive and
     * multiplicative visitors so neither has to know the AST construction
     * details.
     */
    private static ValueAccess binaryExpr(final List<ValueAccess> parts,
                                            final List<LALScriptModel.BinaryOp> ops) {
        return new ValueAccess(
            List.of("expr"), false, false, false, false, false,
            List.of(), null, null,
            parts, ops, null, null);
    }

    private static ValueAccess visitValueAccessTerm(
            final LALParser.ValueAccessTermContext ctx) {
        final List<String> segments = new ArrayList<>();
        boolean parsedRef = false;
        boolean logRef = false;
        boolean processRegistryRef = false;
        boolean stringLiteral = false;
        boolean numberLiteral = false;
        String functionCallName = null;
        List<LALScriptModel.FunctionArg> functionCallArgs = null;
        ValueAccess parenInner = null;
        String parenCast = null;

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
        } else if (primary instanceof LALParser.ValueParenContext) {
            final LALParser.ValueParenContext parenCtx =
                (LALParser.ValueParenContext) primary;
            parenInner = visitValueAccess(parenCtx.valueAccess());
            parenCast = extractCastType(parenCtx.typeCast());
            segments.add("paren");
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
            } else if (seg instanceof LALParser.SegmentIndexContext) {
                final int index = parseStrictInt(
                    ((LALParser.SegmentIndexContext) seg).NUMBER().getText(), "[index]");
                segments.add("[" + index + "]");
                chain.add(new IndexSegment(index));
            }
        }

        return new ValueAccess(segments, parsedRef, logRef,
            processRegistryRef, stringLiteral, numberLiteral,
            chain, functionCallName, functionCallArgs,
            List.of(), parenInner, parenCast);
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
                final String cast = extractCastType(fac.typeCast());
                args.add(new LALScriptModel.FunctionArg(va, cast));
            } else if (fac.STRING() != null) {
                args.add(new LALScriptModel.FunctionArg(
                    literalArg(stripQuotes(fac.STRING().getText()), true, false), null));
            } else if (fac.NUMBER() != null) {
                args.add(new LALScriptModel.FunctionArg(
                    literalArg(fac.NUMBER().getText(), false, true), null));
            } else if (fac.boolValue() != null) {
                args.add(new LALScriptModel.FunctionArg(
                    literalArg(fac.boolValue().getText(), false, false), null));
            } else {
                // NULL
                args.add(new LALScriptModel.FunctionArg(
                    new ValueAccess(List.of("null"), false, false, List.of()), null));
            }
        }
        return args;
    }

    /**
     * Build a single-segment, no-chain {@link ValueAccess} for a literal
     * function-argument value (string / number / boolean). Centralises the
     * boolean-flag layout that would otherwise be repeated at each call site.
     */
    private static ValueAccess literalArg(final String text,
                                            final boolean stringLiteral,
                                            final boolean numberLiteral) {
        return new ValueAccess(
            List.of(text), false, false, false, stringLiteral, numberLiteral,
            List.of(), null, null);
    }

    private static String resolveValueAsString(final LALParser.ValueAccessContext ctx) {
        final LALParser.ValueAccessTermContext term = singleTermOf(ctx);
        if (term == null) {
            return ctx.getText();
        }
        final LALParser.ValueAccessPrimaryContext primary = term.valueAccessPrimary();
        if (primary instanceof LALParser.ValueStringContext) {
            return stripQuotes(((LALParser.ValueStringContext) primary).STRING().getText());
        }
        return primary.getText();
    }

    /**
     * Returns the single {@link LALParser.ValueAccessTermContext} of a
     * {@code valueAccess} when it has no arithmetic operators, otherwise null.
     */
    private static LALParser.ValueAccessTermContext singleTermOf(
            final LALParser.ValueAccessContext ctx) {
        final LALParser.ValueAccessAddContext add = ctx.valueAccessAdd();
        if (add.valueAccessMul().size() != 1) {
            return null;
        }
        final LALParser.ValueAccessMulContext mul = add.valueAccessMul(0);
        if (mul.valueAccessTerm().size() != 1) {
            return null;
        }
        return mul.valueAccessTerm(0);
    }

    /**
     * Parse a NUMBER literal that must represent a plain integer (no
     * decimal, no exponent, no Java-style suffix). Used by integer-only
     * grammar slots — {@code rateLimit { rpm N }} and {@code list[N]} —
     * which share the lexer NUMBER token with arithmetic expressions but
     * cannot accept the suffixes/forms supported there. Throws a clear
     * compile-time error for shape violations and for values that exceed
     * Java's {@code long} range (rather than letting
     * {@link NumberFormatException} leak from {@code Long.parseLong}).
     */
    private static long parseStrictInteger(final String numText, final String slot) {
        for (int i = 0; i < numText.length(); i++) {
            final char c = numText.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException(
                    slot + " expects a plain integer literal, got '" + numText
                        + "' (suffixes / decimals / exponents are not accepted here)");
            }
        }
        try {
            return Long.parseLong(numText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                slot + " value '" + numText + "' exceeds the supported range "
                    + "(must fit in a Java long)");
        }
    }

    /**
     * Like {@link #parseStrictInteger} but additionally requires the value
     * to fit in a Java {@code int}. Used by the {@code [index]} grammar
     * slot, where silent narrowing of an oversized literal would wrap to a
     * negative index instead of producing a clear error.
     */
    private static int parseStrictInt(final String numText, final String slot) {
        final long value = parseStrictInteger(numText, slot);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                slot + " value '" + numText + "' exceeds the supported range "
                    + "(must fit in a Java int)");
        }
        return (int) value;
    }

    /**
     * Parse a NUMBER literal text (which may carry an L/F/D suffix) as a Java double
     * for use in {@link NumberConditionValue}. Suffix is stripped before parsing.
     */
    private static double parseLiteralAsDouble(final String numText) {
        String t = numText;
        if (!t.isEmpty()) {
            final char last = t.charAt(t.length() - 1);
            if (last == 'L' || last == 'l' || last == 'F' || last == 'f'
                    || last == 'D' || last == 'd') {
                t = t.substring(0, t.length() - 1);
            }
        }
        return Double.parseDouble(t);
    }

    // ==================== Utilities ====================

    /**
     * Resolve a {@code typeCast} context to its cast-name string
     * ({@code "Integer"}, {@code "String"}, FQCN, …). Accepts a {@code null}
     * context and returns {@code null} so call sites can pass the context
     * directly without a guard — replaces the ubiquitous
     * {@code extractCastType(ctx.typeCast())}
     * idiom.
     */
    private static String extractCastType(final LALParser.TypeCastContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.STRING_TYPE() != null) {
            return "String";
        }
        if (ctx.LONG_TYPE() != null) {
            return "Long";
        }
        if (ctx.INTEGER_TYPE() != null) {
            return "Integer";
        }
        if (ctx.DOUBLE_TYPE() != null) {
            return "Double";
        }
        if (ctx.FLOAT_TYPE() != null) {
            return "Float";
        }
        if (ctx.BOOLEAN_TYPE() != null) {
            return "Boolean";
        }
        if (ctx.qualifiedName() != null) {
            return ctx.qualifiedName().getText();
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
