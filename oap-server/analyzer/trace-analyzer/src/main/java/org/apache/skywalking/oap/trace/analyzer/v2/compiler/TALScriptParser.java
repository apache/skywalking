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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.AbortStatement;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.Condition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.DefStatement;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.DropperStatement;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.EnforcerStatement;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.ExtractorBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.ExtractorStatement;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.FieldSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.FieldType;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.FilterStatement;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.IfBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.IndexSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.InterpolationPart;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.MetricsBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.PersistBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.RateLimitBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.SamplerBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.SamplerContent;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.SinkBlock;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.TagValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.ValueAccess;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.ValueAccessSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALScriptModel.VoteStatement;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.BinaryOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.CompareOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.LogicalOp;
import org.apache.skywalking.tal.rt.grammar.TALLexer;
import org.apache.skywalking.tal.rt.grammar.TALParser;

/**
 * Parses a TAL rule's DSL text into the immutable {@link TALScriptModel} the generator compiles.
 *
 * <p>Sibling implementation: {@code LALScriptParser} in {@code log-analyzer} does the same for LAL over its own
 * grammar. Separate copies by design; see {@link TALValueCodegen}.
 */
public final class TALScriptParser {

    private TALScriptParser() {
    }

    public static TALScriptModel parse(final String dsl) {
        final TALLexer lexer = new TALLexer(CharStreams.fromString(dsl));
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final TALParser parser = new TALParser(tokens);

        final List<String> errors = new ArrayList<>();
        final BaseErrorListener collector = new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer,
                                    final Object offendingSymbol,
                                    final int line,
                                    final int charPositionInLine,
                                    final String msg,
                                    final RecognitionException e) {
                errors.add(line + ":" + charPositionInLine + " " + msg);
            }
        };
        // The lexer reports too: a character no token matches, such as a YAML `#` comment inside the DSL, is
        // otherwise skipped silently and the text around it compiles as live code.
        lexer.removeErrorListeners();
        lexer.addErrorListener(collector);
        parser.removeErrorListeners();
        parser.addErrorListener(collector);

        final TALParser.RootContext tree = parser.root();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                "TAL script parsing failed: " + String.join("; ", errors)
                    + " in script: " + truncate(dsl, 200));
        }

        return new TALScriptModel(visitFilterContent(tree.filterBlock().filterContent()));
    }

    // ==================== Filter content ====================

    private static List<FilterStatement> visitFilterContent(final TALParser.FilterContentContext ctx) {
        final List<FilterStatement> stmts = new ArrayList<>();
        for (final TALParser.FilterStatementContext fsc : ctx.filterStatement()) {
            stmts.add(visitFilterStatement(fsc));
        }
        return stmts;
    }

    private static FilterStatement visitFilterStatement(final TALParser.FilterStatementContext ctx) {
        if (ctx.extractorBlock() != null) {
            return visitExtractorBlock(ctx.extractorBlock());
        }
        if (ctx.sinkBlock() != null) {
            return visitSinkBlock(ctx.sinkBlock());
        }
        if (ctx.persistBlock() != null) {
            return visitPersistBlock(ctx.persistBlock());
        }
        if (ctx.abortBlock() != null) {
            return new AbortStatement();
        }
        if (ctx.defStatement() != null) {
            return visitDefStatement(ctx.defStatement());
        }
        if (ctx.aliasStatement() != null) {
            return visitAliasStatement(ctx.aliasStatement())
                .withSource(ctx.getStart().getLine(), rawTextOf(ctx));
        }
        return visitIfStatement(ctx.ifStatement());
    }

    // ==================== Extractor block ====================

    private static ExtractorBlock visitExtractorBlock(final TALParser.ExtractorBlockContext ctx) {
        final List<ExtractorStatement> stmts = new ArrayList<>();
        for (final TALParser.ExtractorStatementContext esc : ctx.extractorContent().extractorStatement()) {
            stmts.add(visitExtractorStatement(esc));
        }
        return new ExtractorBlock(stmts);
    }

    private static ExtractorStatement visitExtractorStatement(final TALParser.ExtractorStatementContext ctx) {
        // Capture (line, raw text) once at the statement boundary so every node below carries the same
        // source-origin pair; dsl-debugging's per-line probe reads them back at codegen.
        final int sourceLine = ctx.getStart().getLine();
        final String sourceText = rawTextOf(ctx);

        if (ctx.defStatement() != null) {
            return visitDefStatement(ctx.defStatement()).withSource(sourceLine, sourceText);
        }
        if (ctx.serviceStatement() != null) {
            return visitFieldAssignment(FieldType.SERVICE, ctx.serviceStatement().valueAccess(),
                ctx.serviceStatement().typeCast()).withSource(sourceLine, sourceText);
        }
        if (ctx.instanceStatement() != null) {
            return visitFieldAssignment(FieldType.INSTANCE, ctx.instanceStatement().valueAccess(),
                ctx.instanceStatement().typeCast()).withSource(sourceLine, sourceText);
        }
        if (ctx.endpointStatement() != null) {
            return visitFieldAssignment(FieldType.ENDPOINT, ctx.endpointStatement().valueAccess(),
                ctx.endpointStatement().typeCast()).withSource(sourceLine, sourceText);
        }
        if (ctx.layerStatement() != null) {
            return visitFieldAssignment(FieldType.LAYER, ctx.layerStatement().valueAccess(),
                ctx.layerStatement().typeCast()).withSource(sourceLine, sourceText);
        }
        if (ctx.tagStatement() != null) {
            return visitTagStatement(ctx.tagStatement()).withSource(sourceLine, sourceText);
        }
        if (ctx.aliasStatement() != null) {
            return visitAliasStatement(ctx.aliasStatement()).withSource(sourceLine, sourceText);
        }
        if (ctx.metricsBlock() != null) {
            return visitMetricsBlock(ctx.metricsBlock()).withSource(sourceLine, sourceText);
        }
        if (ctx.abortBlock() != null) {
            return new AbortStatement();
        }
        if (ctx.outputFieldStatement() != null) {
            return visitOutputFieldStatement(ctx.outputFieldStatement()).withSource(sourceLine, sourceText);
        }
        if (ctx.ifStatement() != null) {
            return visitIfStatement(ctx.ifStatement());
        }
        // Every alternative of the grammar's extractorStatement rule has a branch above; falling through means a
        // grammar change landed without one, and a named error beats a NullPointerException in the next visitor.
        throw new IllegalArgumentException(
            "Unsupported extractor statement in TAL rule at line " + sourceLine + ": " + sourceText);
    }

    /**
     * Verbatim source text of a rule context, pulled from the input stream rather than re-concatenated from
     * tokens, so whitespace inside the statement survives for the debugging UI.
     */
    private static String rawTextOf(final ParserRuleContext ctx) {
        if (ctx.getStart() == null || ctx.getStop() == null) {
            return "";
        }
        return ctx.getStart().getInputStream().getText(
            Interval.of(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex()));
    }

    private static TALScriptModel.FieldAssignment visitFieldAssignment(
            final FieldType type,
            final TALParser.ValueAccessContext vaCtx,
            final TALParser.TypeCastContext tcCtx) {
        return new TALScriptModel.FieldAssignment(type, visitValueAccess(vaCtx), extractCastType(tcCtx));
    }

    private static TALScriptModel.OutputFieldAssignment visitOutputFieldStatement(
            final TALParser.OutputFieldStatementContext ctx) {
        return new TALScriptModel.OutputFieldAssignment(
            ctx.anyIdentifier().getText(),
            visitValueAccess(ctx.valueAccess()),
            extractCastType(ctx.typeCast()));
    }

    private static DefStatement visitDefStatement(final TALParser.DefStatementContext ctx) {
        return new DefStatement(
            ctx.IDENTIFIER().getText(),
            visitValueAccess(ctx.valueAccess()),
            extractCastType(ctx.typeCast()));
    }

    private static TALScriptModel.AliasStatement visitAliasStatement(final TALParser.AliasStatementContext ctx) {
        return new TALScriptModel.AliasStatement(
            visitValueAccess(ctx.valueAccess()), extractCastType(ctx.typeCast()));
    }

    private static TALScriptModel.TagAssignment visitTagStatement(final TALParser.TagStatementContext ctx) {
        final Map<String, TagValue> tags = new LinkedHashMap<>();
        if (ctx.tagMap() != null) {
            final TALParser.TagMapContext map = ctx.tagMap();
            for (int i = 0; i < map.anyIdentifier().size(); i++) {
                tags.put(
                    stripQuotes(map.anyIdentifier(i).getText()),
                    new TagValue(visitValueAccess(map.valueAccess(i)), extractCastType(map.typeCast(i))));
            }
        } else {
            tags.put(
                unquote(ctx.STRING().getText()),
                new TagValue(visitValueAccess(ctx.valueAccess()), extractCastType(ctx.typeCast())));
        }
        return new TALScriptModel.TagAssignment(tags);
    }

    // ==================== Metrics block ====================

    private static MetricsBlock visitMetricsBlock(final TALParser.MetricsBlockContext ctx) {
        String name = null;
        ValueAccess scope = null;
        String function = null;
        ValueAccess value = null;
        String valueCast = null;
        Condition match = null;
        Condition status = null;
        final Map<String, TagValue> labels = new LinkedHashMap<>();
        String labelLimit = null;
        List<String> buckets = null;
        List<String> ranks = null;
        ValueAccess timestamp = null;
        String timestampCast = null;

        for (final TALParser.MetricsStatementContext msc : ctx.metricsContent().metricsStatement()) {
            if (msc.metricsNameStatement() != null) {
                name = metricName(msc.metricsNameStatement());
            } else if (msc.metricsScopeStatement() != null) {
                scope = visitValueAccess(msc.metricsScopeStatement().valueAccess());
            } else if (msc.metricsFunctionStatement() != null) {
                function = msc.metricsFunctionStatement().anyIdentifier().getText();
            } else if (msc.metricsValueStatement() != null) {
                value = visitValueAccess(msc.metricsValueStatement().valueAccess());
                valueCast = extractCastType(msc.metricsValueStatement().typeCast());
            } else if (msc.metricsMatchStatement() != null) {
                match = visitCondition(msc.metricsMatchStatement().condition());
            } else if (msc.metricsStatusStatement() != null) {
                status = visitCondition(msc.metricsStatusStatement().condition());
            } else if (msc.metricsLabelsStatement() != null) {
                for (final TALParser.LabelEntryContext lec : msc.metricsLabelsStatement().labelMap().labelEntry()) {
                    labels.put(
                        lec.anyIdentifier().getText(),
                        new TagValue(visitValueAccess(lec.valueAccess()), extractCastType(lec.typeCast())));
                }
            } else if (msc.metricsLabelLimitStatement() != null) {
                labelLimit = msc.metricsLabelLimitStatement().NUMBER().getText();
            } else if (msc.metricsBucketsStatement() != null) {
                buckets = numberListOf(msc.metricsBucketsStatement().numberList());
            } else if (msc.metricsRanksStatement() != null) {
                ranks = numberListOf(msc.metricsRanksStatement().numberList());
            } else if (msc.metricsTimestampStatement() != null) {
                timestamp = visitValueAccess(msc.metricsTimestampStatement().valueAccess());
                timestampCast = extractCastType(msc.metricsTimestampStatement().typeCast());
            }
        }

        return new MetricsBlock(name, scope, function, value, valueCast, match, status, labels, labelLimit, buckets,
            ranks, timestamp, timestampCast);
    }

    private static List<String> numberListOf(final TALParser.NumberListContext ctx) {
        final List<String> out = new ArrayList<>(ctx.NUMBER().size());
        for (final TerminalNode n : ctx.NUMBER()) {
            out.add(n.getText());
        }
        return out;
    }

    // ==================== Sink and persist ====================

    private static SinkBlock visitSinkBlock(final TALParser.SinkBlockContext ctx) {
        final List<VoteStatement> stmts = new ArrayList<>();
        for (final TALParser.SinkStatementContext ssc : ctx.sinkContent().sinkStatement()) {
            stmts.add(visitVoteStatement(
                ssc.samplerBlock(), ssc.enforcerStatement(), ssc.dropperStatement(),
                ssc.abortBlock(), ssc.ifStatement()));
        }
        return new SinkBlock(stmts);
    }

    private static PersistBlock visitPersistBlock(final TALParser.PersistBlockContext ctx) {
        final List<VoteStatement> stmts = new ArrayList<>();
        for (final TALParser.PersistStatementContext psc : ctx.persistContent().persistStatement()) {
            stmts.add(visitVoteStatement(
                psc.samplerBlock(), psc.enforcerStatement(), psc.dropperStatement(),
                psc.abortBlock(), psc.ifStatement()));
        }
        return new PersistBlock(stmts);
    }

    /**
     * {@code sink} and {@code persist} take the same statements, so one visitor serves both; the caller has
     * already pulled the alternatives off its own context type.
     */
    private static VoteStatement visitVoteStatement(final TALParser.SamplerBlockContext sampler,
                                                    final TALParser.EnforcerStatementContext enforcer,
                                                    final TALParser.DropperStatementContext dropper,
                                                    final TALParser.AbortBlockContext abort,
                                                    final TALParser.IfStatementContext ifStatement) {
        if (sampler != null) {
            return visitSamplerBlock(sampler);
        }
        if (enforcer != null) {
            return new EnforcerStatement();
        }
        if (dropper != null) {
            return new DropperStatement();
        }
        if (abort != null) {
            return new AbortStatement();
        }
        return visitIfStatement(ifStatement);
    }

    private static SamplerBlock visitSamplerBlock(final TALParser.SamplerBlockContext ctx) {
        return new SamplerBlock(visitSamplerContent(ctx.samplerContent()));
    }

    /**
     * The sampler's statements in the order the rule wrote them: a {@code rateLimit} after an {@code if} only
     * spends its budget on the spans the {@code if} let through.
     */
    private static List<SamplerContent> visitSamplerContent(final TALParser.SamplerContentContext ctx) {
        final List<SamplerContent> contents = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            final ParseTree child = ctx.getChild(i);
            if (child instanceof TALParser.RateLimitBlockContext) {
                contents.add(visitRateLimitBlock((TALParser.RateLimitBlockContext) child));
            } else if (child instanceof TALParser.IfStatementContext) {
                contents.add(visitIfStatement((TALParser.IfStatementContext) child));
            }
        }
        return contents;
    }

    private static RateLimitBlock visitRateLimitBlock(final TALParser.RateLimitBlockContext rlc) {
        final String id = unquote(rlc.rateLimitId().getText());
        final int rpm = parseStrictInt(rlc.rateLimitContent().NUMBER().getText(), "rpm");
        return new RateLimitBlock(id, parseInterpolation(id), rpm);
    }

    // ==================== If statement ====================

    private static IfBlock visitIfStatement(final TALParser.IfStatementContext ctx) {
        final int condCount = ctx.condition().size();
        final int bodyCount = ctx.ifBody().size();
        final boolean hasElse = bodyCount > condCount;

        // An else-if chain is built from the innermost branch backwards, so
        //   if (A) {b0} else if (B) {b1} else {b2}
        // becomes IfBlock(A, b0, [IfBlock(B, b1, b2)]).
        final List<FilterStatement> trailingElse = hasElse ? visitIfBody(ctx.ifBody(bodyCount - 1)) : null;

        IfBlock nested = null;
        for (int i = condCount - 1; i >= 1; i--) {
            final List<FilterStatement> elsePart = nested != null ? List.of(nested) : trailingElse;
            nested = new IfBlock(visitCondition(ctx.condition(i)), visitIfBody(ctx.ifBody(i)), elsePart);
        }

        final List<FilterStatement> topElse = nested != null ? List.of(nested) : trailingElse;
        return new IfBlock(visitCondition(ctx.condition(0)), visitIfBody(ctx.ifBody(0)), topElse);
    }

    private static List<FilterStatement> visitIfBody(final TALParser.IfBodyContext ctx) {
        final List<FilterStatement> stmts = new ArrayList<>();
        for (final TALParser.FilterStatementContext fsc : ctx.filterStatement()) {
            stmts.add(visitFilterStatement(fsc));
        }
        for (final TALParser.ExtractorStatementContext esc : ctx.extractorStatement()) {
            stmts.add((FilterStatement) visitExtractorStatement(esc));
        }
        for (final TALParser.SinkStatementContext ssc : ctx.sinkStatement()) {
            stmts.add((FilterStatement) visitVoteStatement(
                ssc.samplerBlock(), ssc.enforcerStatement(), ssc.dropperStatement(),
                ssc.abortBlock(), ssc.ifStatement()));
        }
        for (final TALParser.PersistStatementContext psc : ctx.persistStatement()) {
            stmts.add((FilterStatement) visitVoteStatement(
                psc.samplerBlock(), psc.enforcerStatement(), psc.dropperStatement(),
                psc.abortBlock(), psc.ifStatement()));
        }
        final TALParser.SamplerContentContext sc = ctx.samplerContent();
        if (sc != null) {
            final List<SamplerContent> samplerItems = visitSamplerContent(sc);
            if (!samplerItems.isEmpty()) {
                stmts.add(new SamplerBlock(samplerItems));
            }
        }
        return stmts;
    }

    // ==================== Conditions ====================

    private static Condition visitCondition(final TALParser.ConditionContext ctx) {
        if (ctx instanceof TALParser.CondAndContext) {
            final TALParser.CondAndContext and = (TALParser.CondAndContext) ctx;
            return new TALScriptModel.LogicalCondition(
                visitCondition(and.condition(0)), LogicalOp.AND, visitCondition(and.condition(1)));
        }
        if (ctx instanceof TALParser.CondOrContext) {
            final TALParser.CondOrContext or = (TALParser.CondOrContext) ctx;
            return new TALScriptModel.LogicalCondition(
                visitCondition(or.condition(0)), LogicalOp.OR, visitCondition(or.condition(1)));
        }
        if (ctx instanceof TALParser.CondNotContext) {
            return new TALScriptModel.NotCondition(
                visitCondition(((TALParser.CondNotContext) ctx).condition()));
        }
        if (ctx instanceof TALParser.CondEqContext) {
            final TALParser.CondEqContext eq = (TALParser.CondEqContext) ctx;
            return visitComparison(eq.conditionExpr(0), CompareOp.EQ, eq.conditionExpr(1));
        }
        if (ctx instanceof TALParser.CondNeqContext) {
            final TALParser.CondNeqContext neq = (TALParser.CondNeqContext) ctx;
            return visitComparison(neq.conditionExpr(0), CompareOp.NEQ, neq.conditionExpr(1));
        }
        if (ctx instanceof TALParser.CondGtContext) {
            final TALParser.CondGtContext gt = (TALParser.CondGtContext) ctx;
            return visitComparison(gt.conditionExpr(0), CompareOp.GT, gt.conditionExpr(1));
        }
        if (ctx instanceof TALParser.CondLtContext) {
            final TALParser.CondLtContext lt = (TALParser.CondLtContext) ctx;
            return visitComparison(lt.conditionExpr(0), CompareOp.LT, lt.conditionExpr(1));
        }
        if (ctx instanceof TALParser.CondGteContext) {
            final TALParser.CondGteContext gte = (TALParser.CondGteContext) ctx;
            return visitComparison(gte.conditionExpr(0), CompareOp.GTE, gte.conditionExpr(1));
        }
        if (ctx instanceof TALParser.CondLteContext) {
            final TALParser.CondLteContext lte = (TALParser.CondLteContext) ctx;
            return visitComparison(lte.conditionExpr(0), CompareOp.LTE, lte.conditionExpr(1));
        }
        return visitConditionExprAsCondition(((TALParser.CondSingleContext) ctx).conditionExpr());
    }

    private static Condition visitComparison(final TALParser.ConditionExprContext leftCtx,
                                             final CompareOp op,
                                             final TALParser.ConditionExprContext rightCtx) {
        if (leftCtx instanceof TALParser.CondValueAccessContext) {
            final TALParser.CondValueAccessContext lva = (TALParser.CondValueAccessContext) leftCtx;
            return new TALScriptModel.ComparisonCondition(
                visitValueAccess(lva.valueAccess()), extractCastType(lva.typeCast()), op,
                visitConditionExprAsValue(rightCtx));
        }
        if (leftCtx instanceof TALParser.CondFunctionCallContext) {
            final TALParser.FunctionInvocationContext fi =
                ((TALParser.CondFunctionCallContext) leftCtx).functionInvocation();
            return new TALScriptModel.ComparisonCondition(
                functionCallAccess(fi), null, op, visitConditionExprAsValue(rightCtx));
        }
        return new TALScriptModel.ExprCondition(textAccess(leftCtx.getText()), null);
    }

    private static TALScriptModel.ConditionValue visitConditionExprAsValue(
            final TALParser.ConditionExprContext ctx) {
        if (ctx instanceof TALParser.CondStringContext) {
            return new TALScriptModel.StringConditionValue(
                unquote(((TALParser.CondStringContext) ctx).STRING().getText()));
        }
        if (ctx instanceof TALParser.CondNumberContext) {
            final String numText = ((TALParser.CondNumberContext) ctx).NUMBER().getText();
            return new TALScriptModel.NumberConditionValue(parseLiteralAsDouble(numText), numText);
        }
        if (ctx instanceof TALParser.CondNullContext) {
            return new TALScriptModel.NullConditionValue();
        }
        if (ctx instanceof TALParser.CondBoolContext) {
            return new TALScriptModel.BoolConditionValue(
                ((TALParser.CondBoolContext) ctx).boolValue().TRUE() != null);
        }
        if (ctx instanceof TALParser.CondValueAccessContext) {
            final TALParser.CondValueAccessContext va = (TALParser.CondValueAccessContext) ctx;
            // valueAccessPrimary also matches NUMBER and NULL, and condValueAccess has priority over the
            // dedicated alternatives, so a standalone literal arrives here and has to be recovered as one.
            final TALParser.ValueAccessContext vaCtx = va.valueAccess();
            final TALParser.ValueAccessTermContext singleTerm = singleTermOf(vaCtx);
            if (va.typeCast() == null && singleTerm != null && singleTerm.valueAccessSegment().isEmpty()) {
                final TALParser.ValueAccessPrimaryContext primary = singleTerm.valueAccessPrimary();
                if (primary instanceof TALParser.ValueNumberContext) {
                    final String numText = ((TALParser.ValueNumberContext) primary).NUMBER().getText();
                    return new TALScriptModel.NumberConditionValue(parseLiteralAsDouble(numText), numText);
                }
                if (primary instanceof TALParser.ValueNullContext) {
                    return new TALScriptModel.NullConditionValue();
                }
                if (primary instanceof TALParser.ValueBoolContext) {
                    return new TALScriptModel.BoolConditionValue(
                        ((TALParser.ValueBoolContext) primary).boolValue().TRUE() != null);
                }
            }
            return new TALScriptModel.ValueAccessConditionValue(
                visitValueAccess(vaCtx), extractCastType(va.typeCast()));
        }
        if (ctx instanceof TALParser.CondParenGroupContext) {
            return new TALScriptModel.ValueAccessConditionValue(textAccess(ctx.getText()), null);
        }
        return new TALScriptModel.StringConditionValue(ctx.getText());
    }

    private static Condition visitConditionExprAsCondition(final TALParser.ConditionExprContext ctx) {
        if (ctx instanceof TALParser.CondValueAccessContext) {
            final TALParser.CondValueAccessContext va = (TALParser.CondValueAccessContext) ctx;
            return new TALScriptModel.ExprCondition(
                visitValueAccess(va.valueAccess()), extractCastType(va.typeCast()));
        }
        if (ctx instanceof TALParser.CondFunctionCallContext) {
            return new TALScriptModel.ExprCondition(
                functionCallAccess(((TALParser.CondFunctionCallContext) ctx).functionInvocation()), null);
        }
        if (ctx instanceof TALParser.CondParenGroupContext) {
            return visitCondition(((TALParser.CondParenGroupContext) ctx).condition());
        }
        return new TALScriptModel.ExprCondition(textAccess(ctx.getText()), null);
    }

    // ==================== Value access ====================

    private static ValueAccess visitValueAccess(final TALParser.ValueAccessContext ctx) {
        return visitValueAccessAdd(ctx.valueAccessAdd());
    }

    private static ValueAccess visitValueAccessAdd(final TALParser.ValueAccessAddContext ctx) {
        final List<TALParser.ValueAccessMulContext> muls = ctx.valueAccessMul();
        if (muls.size() == 1) {
            return visitValueAccessMul(muls.get(0));
        }
        final List<ValueAccess> parts = new ArrayList<>(muls.size());
        for (final TALParser.ValueAccessMulContext mul : muls) {
            parts.add(visitValueAccessMul(mul));
        }
        return binaryExpr(parts, collectInfixOps(ctx,
            TALLexer.PLUS, BinaryOp.PLUS, TALLexer.MINUS, BinaryOp.MINUS));
    }

    private static ValueAccess visitValueAccessMul(final TALParser.ValueAccessMulContext ctx) {
        final List<TALParser.ValueAccessTermContext> terms = ctx.valueAccessTerm();
        if (terms.size() == 1) {
            return visitValueAccessTerm(terms.get(0));
        }
        final List<ValueAccess> parts = new ArrayList<>(terms.size());
        for (final TALParser.ValueAccessTermContext term : terms) {
            parts.add(visitValueAccessTerm(term));
        }
        return binaryExpr(parts, collectInfixOps(ctx,
            TALLexer.STAR, BinaryOp.STAR, TALLexer.SLASH, BinaryOp.SLASH));
    }

    /**
     * Walk a context's terminal children to recover the infix operator sequence, one per gap between operands,
     * which ANTLR's flattened child list does not give directly.
     */
    private static List<BinaryOp> collectInfixOps(final ParserRuleContext ctx,
                                                  final int firstToken, final BinaryOp firstOp,
                                                  final int secondToken, final BinaryOp secondOp) {
        final List<BinaryOp> ops = new ArrayList<>();
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

    private static ValueAccess binaryExpr(final List<ValueAccess> parts, final List<BinaryOp> ops) {
        return new ValueAccess(List.of("expr"), false, false, false, false,
            List.of(), null, null, parts, ops, null, null);
    }

    private static ValueAccess visitValueAccessTerm(final TALParser.ValueAccessTermContext ctx) {
        final List<String> segments = new ArrayList<>();
        boolean spanRef = false;
        boolean metaRef = false;
        boolean stringLiteral = false;
        boolean numberLiteral = false;
        String functionCallName = null;
        List<TALScriptModel.FunctionArg> functionCallArgs = null;
        ValueAccess parenInner = null;
        String parenCast = null;

        final TALParser.ValueAccessPrimaryContext primary = ctx.valueAccessPrimary();
        if (primary instanceof TALParser.ValueSpanContext) {
            spanRef = true;
            segments.add("span");
        } else if (primary instanceof TALParser.ValueMetaContext) {
            metaRef = true;
            segments.add("meta");
        } else if (primary instanceof TALParser.ValueIdentifierContext) {
            segments.add(((TALParser.ValueIdentifierContext) primary).IDENTIFIER().getText());
        } else if (primary instanceof TALParser.ValueStringContext) {
            stringLiteral = true;
            segments.add(unquote(((TALParser.ValueStringContext) primary).STRING().getText()));
        } else if (primary instanceof TALParser.ValueNumberContext) {
            numberLiteral = true;
            segments.add(((TALParser.ValueNumberContext) primary).NUMBER().getText());
        } else if (primary instanceof TALParser.ValueFunctionCallContext) {
            final TALParser.FunctionInvocationContext fi =
                ((TALParser.ValueFunctionCallContext) primary).functionInvocation();
            functionCallName = fi.functionName().getText();
            functionCallArgs = visitFunctionArgs(fi);
            segments.add(fi.getText());
        } else if (primary instanceof TALParser.ValueParenContext) {
            final TALParser.ValueParenContext parenCtx = (TALParser.ValueParenContext) primary;
            parenInner = visitValueAccess(parenCtx.valueAccess());
            parenCast = extractCastType(parenCtx.typeCast());
            segments.add("paren");
        } else {
            segments.add(primary.getText());
        }

        final List<ValueAccessSegment> chain = new ArrayList<>();
        for (final TALParser.ValueAccessSegmentContext seg : ctx.valueAccessSegment()) {
            if (seg instanceof TALParser.SegmentFieldContext) {
                final String name = ((TALParser.SegmentFieldContext) seg).anyIdentifier().getText();
                segments.add(name);
                chain.add(new FieldSegment(name, false));
            } else if (seg instanceof TALParser.SegmentSafeFieldContext) {
                final String name = ((TALParser.SegmentSafeFieldContext) seg).anyIdentifier().getText();
                segments.add(name);
                chain.add(new FieldSegment(name, true));
            } else if (seg instanceof TALParser.SegmentMethodContext) {
                final TALParser.FunctionInvocationContext fi =
                    ((TALParser.SegmentMethodContext) seg).functionInvocation();
                segments.add(fi.functionName().getText() + "()");
                chain.add(new TALScriptModel.MethodSegment(
                    fi.functionName().getText(), visitFunctionArgs(fi), false));
            } else if (seg instanceof TALParser.SegmentSafeMethodContext) {
                final TALParser.FunctionInvocationContext fi =
                    ((TALParser.SegmentSafeMethodContext) seg).functionInvocation();
                segments.add(fi.functionName().getText() + "()");
                chain.add(new TALScriptModel.MethodSegment(
                    fi.functionName().getText(), visitFunctionArgs(fi), true));
            } else if (seg instanceof TALParser.SegmentIndexContext) {
                final int index = parseStrictInt(
                    ((TALParser.SegmentIndexContext) seg).NUMBER().getText(), "[index]");
                segments.add("[" + index + "]");
                chain.add(new IndexSegment(index));
            }
        }

        return new ValueAccess(segments, spanRef, metaRef, stringLiteral, numberLiteral,
            chain, functionCallName, functionCallArgs, List.of(), List.of(), parenInner, parenCast);
    }

    private static List<TALScriptModel.FunctionArg> visitFunctionArgs(
            final TALParser.FunctionInvocationContext fi) {
        if (fi.functionArgList() == null) {
            return List.of();
        }
        final List<TALScriptModel.FunctionArg> args = new ArrayList<>();
        for (final TALParser.FunctionArgContext fac : fi.functionArgList().functionArg()) {
            if (fac.detectPoint() != null) {
                // CLIENT / SERVER are written bare as a relation constructor's first argument; they reach the
                // generator as a plain identifier, which only the entity constructors accept.
                args.add(new TALScriptModel.FunctionArg(textAccess(fac.detectPoint().getText()), null));
            } else if (fac.valueAccess() != null) {
                args.add(new TALScriptModel.FunctionArg(
                    visitValueAccess(fac.valueAccess()), extractCastType(fac.typeCast())));
            } else if (fac.STRING() != null) {
                args.add(new TALScriptModel.FunctionArg(
                    literalArg(unquote(fac.STRING().getText()), true, false), null));
            } else if (fac.NUMBER() != null) {
                args.add(new TALScriptModel.FunctionArg(
                    literalArg(fac.NUMBER().getText(), false, true), null));
            } else if (fac.boolValue() != null) {
                args.add(new TALScriptModel.FunctionArg(
                    literalArg(fac.boolValue().getText(), false, false), null));
            } else {
                args.add(new TALScriptModel.FunctionArg(textAccess("null"), null));
            }
        }
        return args;
    }

    private static ValueAccess functionCallAccess(final TALParser.FunctionInvocationContext fi) {
        return new ValueAccess(List.of(fi.getText()), false, false, false, false,
            List.of(), fi.functionName().getText(), visitFunctionArgs(fi));
    }

    /**
     * A single-segment, no-chain access carrying raw text, for the forms the model has no richer shape for.
     */
    private static ValueAccess textAccess(final String text) {
        return new ValueAccess(List.of(text), false, false, List.of());
    }

    private static ValueAccess literalArg(final String text,
                                          final boolean stringLiteral,
                                          final boolean numberLiteral) {
        return new ValueAccess(List.of(text), false, false, stringLiteral, numberLiteral,
            List.of(), null, null);
    }

    /**
     * A metric's name is part of its storage schema, fixed when the rule loads, so it is a string literal; an
     * expression would be read as its own source text.
     */
    private static String metricName(final TALParser.MetricsNameStatementContext ctx) {
        final TALParser.ValueAccessTermContext term = singleTermOf(ctx.valueAccess());
        if (ctx.typeCast() == null && term != null && term.valueAccessSegment().isEmpty()
            && term.valueAccessPrimary() instanceof TALParser.ValueStringContext) {
            return unquote(((TALParser.ValueStringContext) term.valueAccessPrimary()).STRING().getText());
        }
        throw new IllegalArgumentException(
            "a metric's name is a string literal, got '" + rawTextOf(ctx.valueAccess()) + "' at line "
                + ctx.getStart().getLine());
    }

    /**
     * @return the single term of a {@code valueAccess} that carries no arithmetic operators, else null
     */
    private static TALParser.ValueAccessTermContext singleTermOf(final TALParser.ValueAccessContext ctx) {
        final TALParser.ValueAccessAddContext add = ctx.valueAccessAdd();
        if (add.valueAccessMul().size() != 1) {
            return null;
        }
        final TALParser.ValueAccessMulContext mul = add.valueAccessMul(0);
        if (mul.valueAccessTerm().size() != 1) {
            return null;
        }
        return mul.valueAccessTerm(0);
    }

    // ==================== Utilities ====================

    /**
     * Parse a NUMBER literal that must be a plain integer. The lexer's NUMBER token also carries the decimal,
     * exponent and suffix forms arithmetic accepts, which the {@code rpm} and {@code [index]} slots cannot.
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
                slot + " value '" + numText + "' exceeds the supported range (must fit in a Java long)");
        }
    }

    private static int parseStrictInt(final String numText, final String slot) {
        final long value = parseStrictInteger(numText, slot);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                slot + " value '" + numText + "' exceeds the supported range (must fit in a Java int)");
        }
        return (int) value;
    }

    private static double parseLiteralAsDouble(final String numText) {
        String t = numText;
        if (!t.isEmpty()) {
            final char last = t.charAt(t.length() - 1);
            if (last == 'L' || last == 'l' || last == 'F' || last == 'f' || last == 'D' || last == 'd') {
                t = t.substring(0, t.length() - 1);
            }
        }
        return Double.parseDouble(t);
    }

    /**
     * Resolve a {@code typeCast} context to its cast name. Accepts a null context so call sites can pass
     * {@code ctx.typeCast()} without a guard.
     */
    private static String extractCastType(final TALParser.TypeCastContext ctx) {
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

    /**
     * A STRING token's value: the quotes removed and the escapes decoded as Java decodes them, so the token
     * {@code "a\"b"} is the three characters {@code a"b}.
     */
    static String unquote(final String token) {
        final String body = stripQuotes(token);
        if (body == null || body.indexOf('\\') < 0) {
            return body;
        }
        final StringBuilder out = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            final char c = body.charAt(i);
            if (c != '\\' || i + 1 >= body.length()) {
                out.append(c);
                continue;
            }
            final char next = body.charAt(++i);
            switch (next) {
                case 'b':
                    out.append('\b');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                default:
                    if (next >= '0' && next <= '7') {
                        // Octal: up to three digits when the first is 0-3, else two, as the lexer allows.
                        final int max = next <= '3' ? i + 3 : i + 2;
                        int end = i + 1;
                        while (end < body.length() && end < max
                            && body.charAt(end) >= '0' && body.charAt(end) <= '7') {
                            end++;
                        }
                        out.append((char) Integer.parseInt(body.substring(i, end), 8));
                        i = end - 1;
                    } else {
                        // A quote, a backslash, or any other escaped character stands for itself.
                        out.append(next);
                    }
                    break;
            }
        }
        return out.toString();
    }

    static String stripQuotes(final String s) {
        if (s == null || s.length() < 2) {
            return s;
        }
        final char first = s.charAt(0);
        if ((first == '\'' || first == '"') && s.charAt(s.length() - 1) == first) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String truncate(final String s, final int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ==================== Interpolation ====================

    /**
     * Parse {@code ${...}} placeholders in a rate-limit id, so one rule can keep a counter per service or per
     * endpoint. {@code "slow-sql-${meta.service}"} yields [literal("slow-sql-"), expr(meta.service)].
     *
     * @param s the id as written
     * @return the parts, or null when the id carries no placeholder
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
                if (pos < s.length()) {
                    parts.add(InterpolationPart.ofLiteral(s.substring(pos)));
                }
                break;
            }
            if (start > pos) {
                parts.add(InterpolationPart.ofLiteral(s.substring(pos, start)));
            }
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
                throw new IllegalArgumentException("Unclosed interpolation in: " + s);
            }
            parts.add(InterpolationPart.ofExpression(parseValueAccessExpr(s.substring(start + 2, i - 1))));
            pos = i;
        }
        return parts;
    }

    /**
     * Parse a standalone expression by wrapping it in a minimal rule, since the grammar's entry point is a whole
     * {@code filter} block.
     */
    private static ValueAccess parseValueAccessExpr(final String expr) {
        final TALScriptModel model = parse("filter { if (" + expr + ") { abort {} } }");
        final Condition cond = ((IfBlock) model.getStatements().get(0)).getCondition();
        if (cond instanceof TALScriptModel.ExprCondition) {
            return ((TALScriptModel.ExprCondition) cond).getExpr();
        }
        if (cond instanceof TALScriptModel.ComparisonCondition) {
            return ((TALScriptModel.ComparisonCondition) cond).getLeft();
        }
        throw new IllegalArgumentException("Cannot parse interpolation expression: " + expr);
    }
}
