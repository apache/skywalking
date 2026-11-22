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

package org.apache.skywalking.oal.v2.parser;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.apache.skywalking.oal.rt.grammar.OALParser;
import org.apache.skywalking.oal.rt.grammar.OALParserBaseListener;
import org.apache.skywalking.oal.v2.model.FilterExpression;
import org.apache.skywalking.oal.v2.model.FilterOperator;
import org.apache.skywalking.oal.v2.model.FilterValue;
import org.apache.skywalking.oal.v2.model.FunctionArgument;
import org.apache.skywalking.oal.v2.model.FunctionCall;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.model.SourceLocation;
import org.apache.skywalking.oal.v2.model.SourceReference;
import org.apache.skywalking.oal.v2.util.TypeCastUtil;

/**
 * V2 implementation of OAL listener that converts ANTLR parse tree to V2 immutable models.
 *
 * This listener walks the ANTLR parse tree and builds strongly-typed, immutable MetricDefinition objects.
 *
 * Example OAL script:
 * <pre>
 * service_resp_time = from(Service.latency).longAvg();
 * service_sla = from(Service.*).filter(status == true).percent();
 * endpoint_calls = from(Endpoint.*).filter(latency &gt; 100).count();
 * </pre>
 *
 * Parsing flow for "service_resp_time = from(Service.latency).longAvg();":
 * <ol>
 *   <li>enterAggregationStatement() - Start metric definition, set name "service_resp_time"</li>
 *   <li>enterSource() - Set source name "Service"</li>
 *   <li>enterSourceAttribute() - Add attribute "latency" to source</li>
 *   <li>enterFunctionName() - Set function name "longAvg"</li>
 *   <li>exitAggregationStatement() - Build and save complete metric</li>
 * </ol>
 *
 * Parsing flow for "service_sla = from(Service.*).filter(status == true).percent();":
 * <ol>
 *   <li>enterAggregationStatement() - Start metric, set name "service_sla"</li>
 *   <li>enterSource() - Set source "Service"</li>
 *   <li>enterSourceAttribute() - Mark as wildcard (*)</li>
 *   <li>enterFilterStatement() - Start filter</li>
 *   <li>enterBooleanMatch() - Parse filter: status == true</li>
 *   <li>exitFilterStatement() - Add filter to metric</li>
 *   <li>enterFunctionName() - Set function "percent"</li>
 *   <li>exitAggregationStatement() - Build complete metric</li>
 * </ol>
 */
public class OALListenerV2 extends OALParserBaseListener {

    @Getter
    private final List<MetricDefinition> metrics = new ArrayList<>();

    @Getter
    private final List<String> disabledSources = new ArrayList<>();

    private final String fileName;

    // Current parsing context
    private MetricDefinition.Builder currentMetric;
    /** The metric a rule-check error names, while its statement is walked. */
    private String currentMetricName;
    private SourceReference.Builder currentSource;
    private FunctionCall.Builder currentFunction;
    private FilterExpression.Builder currentFilter;

    public OALListenerV2(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void enterAggregationStatement(OALParser.AggregationStatementContext ctx) {
        currentMetric = MetricDefinition.builder();
        currentSource = SourceReference.builder();

        // Set metric name
        String varName = ctx.variable().getText();
        currentMetric.name(varName);
        currentMetricName = varName;
        currentMetric.tableName(varName);

        // Set source location
        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine();
        currentMetric.location(SourceLocation.of(fileName, line, column));
    }

    @Override
    public void exitMetricStatement(OALParser.MetricStatementContext ctx) {
        final List<OALParser.SourceAttributeStmtContext> attributes = ctx.sourceAttributeStmt();
        if (attributes.size() > 1) {
            for (final OALParser.SourceAttributeStmtContext attribute : attributes) {
                if (attribute.sourceAttribute().ALL() != null) {
                    throw error(attribute.getStart(), "from(" + ctx.source().getText()
                        + "...) takes either * or an attribute path, not both");
                }
            }
        }
        if (currentMetric != null) {
            // Verbatim "from(SourceName.attr)" / "from(SourceName.*)" slice —
            // ANTLR Interval from the FROM token through the FIRST closing
            // paren that ends the source-attrs list. The dsl-debugging
            // captureSource probe stamps this directly so the source sample's
            // sourceText matches what the operator wrote in .oal byte-for-byte
            // (no runtime reconstruction).
            if (ctx.FROM() != null && ctx.RR_BRACKET() != null) {
                final int start = ctx.FROM().getSymbol().getStartIndex();
                final int stop = ctx.RR_BRACKET().getSymbol().getStopIndex();
                currentMetric.fromText(
                    ctx.getStart().getInputStream().getText(Interval.of(start, stop)));
            }
        }
    }

    @Override
    public void exitAggregationStatement(OALParser.AggregationStatementContext ctx) {
        if (currentMetric != null && currentSource != null) {
            currentMetric.source(currentSource.build());
            // Verbatim ANTLR slice of the full assignment statement
            // (e.g. "service_relation_server_cpm = from(ServiceRelation.*).filter(detectPoint == DetectPoint.SERVER).cpm();")
            // — captured straight from the input stream, threaded into the
            // per-metric GateHolder for dsl-debugging.
            currentMetric.sourceText(rawTextOf(ctx));
            metrics.add(currentMetric.build());
            currentMetric = null;
            currentSource = null;
        }
        currentMetricName = null;
    }

    @Override
    public void enterSource(OALParser.SourceContext ctx) {
        if (currentSource != null) {
            currentSource.name(ctx.getText());
        }
    }

    @Override
    public void enterSourceAttribute(OALParser.SourceAttributeContext ctx) {
        if (currentSource == null) {
            return;
        }

        String text = ctx.getText();
        if ("*".equals(text)) {
            currentSource.wildcard(true);
        } else {
            currentSource.addAttribute(text);
        }
    }

    @Override
    public void enterSourceAttrCast(OALParser.SourceAttrCastContext ctx) {
        if (currentSource != null) {
            String castType = ctx.getText();
            currentSource.castType(castType);
        }
    }

    @Override
    public void enterFunctionName(OALParser.FunctionNameContext ctx) {
        currentFunction = FunctionCall.builder();
        currentFunction.name(ctx.getText());
    }

    @Override
    public void enterLiteralExpression(OALParser.LiteralExpressionContext ctx) {
        if (currentFunction == null) {
            return;
        }

        if (ctx.BOOL_LITERAL() != null) {
            currentFunction.addLiteral(Boolean.parseBoolean(ctx.getText()));
        } else if (ctx.STRING_LITERAL() != null) {
            currentFunction.addLiteral(TypeCastUtil.decode(ctx.STRING_LITERAL().getText()));
        } else {
            final Token number = ctx.NUMBER_LITERAL().getSymbol();
            final String text = number.getText();
            try {
                currentFunction.addArgument(TypeCastUtil.isLongSuffixed(text)
                    ? FunctionArgument.longLiteral(TypeCastUtil.argumentSpelling(text))
                    : FunctionArgument.literal(TypeCastUtil.argumentNumber(text)));
            } catch (IllegalArgumentException e) {
                throw error(number, e.getMessage());
            }
        }
    }

    @Override
    public void enterAttributeExpression(OALParser.AttributeExpressionContext ctx) {
        if (currentFunction != null) {
            currentFunction.addAttribute(
                attributePath(ctx.attributeExpressionSegment()),
                ctx.functionArgCast() == null ? null : ctx.functionArgCast().getText());
        }
    }

    @Override
    public void exitAggregateFunction(OALParser.AggregateFunctionContext ctx) {
        if (currentFunction != null && currentMetric != null) {
            // Verbatim ANTLR slice — e.g. "cpm()", "percentile2(10)" — captured
            // exactly as written (including any whitespace inside the parens),
            // so the dsl-debugging UI can match an aggregation capture record
            // back to the original .oal byte-for-byte.
            currentFunction.sourceText(rawTextOf(ctx));
            currentMetric.aggregationFunction(currentFunction.build());
            currentFunction = null;
        }
    }

    @Override
    public void enterFuncParamExpression(OALParser.FuncParamExpressionContext ctx) {
        // Function parameter expressions are filter expressions
        // They will be handled by the expression context handlers
        currentFilter = FilterExpression.builder();
    }

    @Override
    public void exitFuncParamExpression(OALParser.FuncParamExpressionContext ctx) {
        if (currentFilter != null && currentFunction != null) {
            currentFunction.addExpression(currentFilter.build());
            currentFilter = null;
        }
    }

    @Override
    public void enterDecorateSource(OALParser.DecorateSourceContext ctx) {
        if (currentMetric != null && ctx.STRING_LITERAL() != null) {
            // As written: the templates paste it into a Java string, which reads its escapes.
            currentMetric.decorator(TypeCastUtil.unquoted(ctx.STRING_LITERAL().getText()));
        }
    }

    @Override
    public void enterFilterStatement(OALParser.FilterStatementContext ctx) {
        currentFilter = FilterExpression.builder();
    }

    @Override
    public void enterBooleanMatch(OALParser.BooleanMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.EQUAL);
        currentFilter.booleanValue(Boolean.parseBoolean(ctx.booleanConditionValue().getText()));
    }

    @Override
    public void enterNumberMatch(OALParser.NumberMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.EQUAL);
        filterNumber(ctx.numberConditionValue());
    }

    @Override
    public void enterStringMatch(OALParser.StringMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.EQUAL);
        if (ctx.stringConditionValue() != null) {
            filterString(ctx.stringConditionValue());
        } else if (ctx.enumConditionValue() != null) {
            currentFilter.enumValue(ctx.enumConditionValue().getText());
        } else {
            currentFilter.nullValue();
        }
    }

    @Override
    public void enterGreaterMatch(OALParser.GreaterMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.GREATER);
        filterNumber(ctx.numberConditionValue());
    }

    @Override
    public void enterLessMatch(OALParser.LessMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.LESS);
        filterNumber(ctx.numberConditionValue());
    }

    @Override
    public void enterGreaterEqualMatch(OALParser.GreaterEqualMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.GREATER_EQUAL);
        filterNumber(ctx.numberConditionValue());
    }

    @Override
    public void enterLessEqualMatch(OALParser.LessEqualMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.LESS_EQUAL);
        filterNumber(ctx.numberConditionValue());
    }

    @Override
    public void enterLikeMatch(OALParser.LikeMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.LIKE);
        filterString(ctx.stringConditionValue());
    }

    @Override
    public void enterNotEqualMatch(OALParser.NotEqualMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.NOT_EQUAL);
        if (ctx.numberConditionValue() != null) {
            filterNumber(ctx.numberConditionValue());
        } else if (ctx.stringConditionValue() != null) {
            filterString(ctx.stringConditionValue());
        } else if (ctx.enumConditionValue() != null) {
            currentFilter.enumValue(ctx.enumConditionValue().getText());
        } else {
            currentFilter.nullValue();
        }
    }

    @Override
    public void enterBooleanNotEqualMatch(OALParser.BooleanNotEqualMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.NOT_EQUAL);
        currentFilter.booleanValue(Boolean.parseBoolean(ctx.booleanConditionValue().getText()));
    }

    @Override
    public void enterInMatch(OALParser.InMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.IN);

        // The grammar keeps a list to one kind: numbers, strings or enum constants.
        final OALParser.MultiConditionValueContext multiCtx = ctx.multiConditionValue();
        final List<Object> values = new ArrayList<>();
        String refusal = null;
        for (final OALParser.NumberConditionValueContext numCtx : multiCtx.numberConditionValue()) {
            try {
                values.add(TypeCastUtil.numberValue(numCtx.getText()));
            } catch (final IllegalArgumentException e) {
                refusal = refusal == null ? e.getMessage() : refusal;
                values.add(numCtx.getText());
            }
        }
        for (final OALParser.StringConditionValueContext strCtx : multiCtx.stringConditionValue()) {
            values.add(TypeCastUtil.decode(strCtx.STRING_LITERAL().getText()));
        }
        for (final OALParser.EnumConditionValueContext enumCtx : multiCtx.enumConditionValue()) {
            values.add(FilterValue.ofEnum(enumCtx.getText()));
        }
        currentFilter.arrayValue(values);
        currentFilter.writtenValue(writtenItems(multiCtx));
        if (refusal != null) {
            currentFilter.refusedValue(refusal);
        }
    }

    /**
     * A compared number: its value, and as written; one the stock matchers refuse (out of the long range, a leading 0)
     * fails the compile only where a stock matcher takes it.
     */
    private void filterNumber(final OALParser.NumberConditionValueContext ctx) {
        final Object written = TypeCastUtil.numberAsWritten(ctx.getText());
        try {
            currentFilter.numberValue(TypeCastUtil.numberValue(ctx.getText()));
        } catch (final IllegalArgumentException e) {
            currentFilter.value(written).refusedValue(e.getMessage());
        }
        currentFilter.writtenValue(written);
    }

    /**
     * A compared string: decoded, and as written, with no escape decoded, where the two differ.
     */
    private void filterString(final OALParser.StringConditionValueContext ctx) {
        final String quoted = ctx.STRING_LITERAL().getText();
        currentFilter.stringValue(TypeCastUtil.decode(quoted));
        final String written = TypeCastUtil.unquoted(quoted);
        if (written.indexOf('\\') >= 0) {
            currentFilter.writtenValue(written);
        }
    }

    /**
     * A list's items as written: a number's value, or its text where it has an L or a _; a string's text between its
     * quotes, unescaped by Java later; a constant's name.
     */
    private static List<Object> writtenItems(final OALParser.MultiConditionValueContext multiCtx) {
        final List<Object> items = new ArrayList<>();
        for (final OALParser.NumberConditionValueContext numCtx : multiCtx.numberConditionValue()) {
            items.add(TypeCastUtil.numberAsWritten(numCtx.getText()));
        }
        for (final OALParser.StringConditionValueContext strCtx : multiCtx.stringConditionValue()) {
            items.add(TypeCastUtil.unquoted(strCtx.getText()));
        }
        for (final OALParser.EnumConditionValueContext enumCtx : multiCtx.enumConditionValue()) {
            items.add(enumCtx.getText());
        }
        return items;
    }

    @Override
    public void enterContainMatch(OALParser.ContainMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.CONTAIN);
        filterString(ctx.stringConditionValue());
    }

    @Override
    public void enterNotContainMatch(OALParser.NotContainMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        attribute(ctx.conditionAttributeStmt());
        currentFilter.operator(FilterOperator.NOT_CONTAIN);
        filterString(ctx.stringConditionValue());
    }

    @Override
    public void exitFilterStatement(OALParser.FilterStatementContext ctx) {
        if (currentFilter != null && currentMetric != null) {
            // Verbatim ANTLR slice — e.g. "filter(detectPoint == DetectPoint.SERVER)" —
            // captured byte-for-byte from the input stream so the dsl-debugging UI
            // can highlight the exact filter clause that fired in the original .oal.
            currentFilter.sourceText(rawTextOf(ctx));
            currentMetric.addFilter(currentFilter.build());
            currentFilter = null;
        }
    }

    private void attribute(final OALParser.ConditionAttributeStmtContext ctx) {
        currentFilter.fieldName(attributePath(ctx.conditionAttribute()));
        if (ctx.expressionAttrCast() != null) {
            currentFilter.castType(ctx.expressionAttrCast().getText());
        }
    }

    /** The dotted attribute path, without the cast that may precede it. */
    private static String attributePath(final List<? extends ParserRuleContext> segments) {
        final StringBuilder path = new StringBuilder();
        for (final ParserRuleContext segment : segments) {
            if (path.length() > 0) {
                path.append('.');
            }
            path.append(segment.getText());
        }
        return path.toString();
    }

    private IllegalArgumentException error(final Token token, final String message) {
        return new IllegalArgumentException(
            fileName + ":" + token.getLine() + ":" + token.getCharPositionInLine() + " - "
                + (currentMetricName == null ? "" : currentMetricName + ": ") + message);
    }

    /** Verbatim source slice of an ANTLR rule context. */
    private static String rawTextOf(final ParserRuleContext ctx) {
        if (ctx.getStart() == null || ctx.getStop() == null) {
            return "";
        }
        return ctx.getStart().getInputStream().getText(
            Interval.of(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex()));
    }

    @Override
    public void enterDisableSource(OALParser.DisableSourceContext ctx) {
        if (ctx.IDENTIFIER() != null) {
            disabledSources.add(ctx.IDENTIFIER().getText());
        }
    }
}
