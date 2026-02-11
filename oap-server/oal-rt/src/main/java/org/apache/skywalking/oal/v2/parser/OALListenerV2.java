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
import org.apache.skywalking.oal.rt.grammar.OALParser;
import org.apache.skywalking.oal.rt.grammar.OALParserBaseListener;
import org.apache.skywalking.oal.v2.model.FilterExpression;
import org.apache.skywalking.oal.v2.model.FilterOperator;
import org.apache.skywalking.oal.v2.model.FunctionCall;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.model.SourceLocation;
import org.apache.skywalking.oal.v2.model.SourceReference;

/**
 * V2 implementation of OAL listener that converts ANTLR parse tree to V2 immutable models.
 *
 * This listener walks the ANTLR parse tree and builds strongly-typed, immutable MetricDefinition objects.
 *
 * Example OAL script:
 * <pre>
 * service_resp_time = from(Service.latency).longAvg();
 * service_sla = from(Service.*).filter(status == true).percent();
 * endpoint_calls = from(Endpoint.*).filter(latency > 100).count();
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
        currentMetric.tableName(varName);

        // Set source location
        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine();
        currentMetric.location(SourceLocation.of(fileName, line, column));
    }

    @Override
    public void exitAggregationStatement(OALParser.AggregationStatementContext ctx) {
        if (currentMetric != null && currentSource != null) {
            currentMetric.source(currentSource.build());
            metrics.add(currentMetric.build());
            currentMetric = null;
            currentSource = null;
        }
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

        String text = ctx.getText();

        // Boolean literal
        if ("true".equals(text) || "false".equals(text)) {
            currentFunction.addLiteral(Boolean.parseBoolean(text));
            return;
        }

        // String literal (remove quotes)
        if (text.startsWith("\"") && text.endsWith("\"")) {
            String str = text.substring(1, text.length() - 1);
            currentFunction.addLiteral(str);
            return;
        }

        // Number literal
        try {
            if (text.contains(".")) {
                currentFunction.addLiteral(Double.parseDouble(text));
            } else {
                currentFunction.addLiteral(Long.parseLong(text));
            }
        } catch (NumberFormatException e) {
            currentFunction.addLiteral(text);
        }
    }

    @Override
    public void enterAttributeExpression(OALParser.AttributeExpressionContext ctx) {
        if (currentFunction != null) {
            String attr = ctx.getText();
            currentFunction.addAttribute(attr);
        }
    }

    @Override
    public void exitAggregateFunction(OALParser.AggregateFunctionContext ctx) {
        if (currentFunction != null && currentMetric != null) {
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
            String decorator = ctx.STRING_LITERAL().getText();
            decorator = decorator.substring(1, decorator.length() - 1); // Remove quotes
            currentMetric.decorator(decorator);
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

        String field = ctx.conditionAttributeStmt().getText();
        boolean value = Boolean.parseBoolean(ctx.booleanConditionValue().getText());

        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.EQUAL);
        currentFilter.booleanValue(value);
    }

    @Override
    public void enterNumberMatch(OALParser.NumberMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();
        String numText = ctx.numberConditionValue().getText();

        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.EQUAL);

        try {
            if (numText.contains(".")) {
                currentFilter.numberValue(Double.parseDouble(numText));
            } else {
                currentFilter.numberValue(Long.parseLong(numText));
            }
        } catch (NumberFormatException e) {
            currentFilter.stringValue(numText);
        }
    }

    @Override
    public void enterStringMatch(OALParser.StringMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();

        // Could be stringConditionValue, enumConditionValue, or nullConditionValue
        if (ctx.stringConditionValue() != null) {
            String value = ctx.stringConditionValue().getText();
            value = value.substring(1, value.length() - 1); // Remove quotes
            currentFilter.fieldName(field);
            currentFilter.operator(FilterOperator.EQUAL);
            currentFilter.stringValue(value);
        } else if (ctx.enumConditionValue() != null) {
            String value = ctx.enumConditionValue().getText();
            currentFilter.fieldName(field);
            currentFilter.operator(FilterOperator.EQUAL);
            currentFilter.enumValue(value);
        } else if (ctx.nullConditionValue() != null) {
            currentFilter.fieldName(field);
            currentFilter.operator(FilterOperator.EQUAL);
            currentFilter.nullValue();
        }
    }

    @Override
    public void enterGreaterMatch(OALParser.GreaterMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();
        String numText = ctx.numberConditionValue().getText();

        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.GREATER);

        try {
            if (numText.contains(".")) {
                currentFilter.numberValue(Double.parseDouble(numText));
            } else {
                currentFilter.numberValue(Long.parseLong(numText));
            }
        } catch (NumberFormatException e) {
            currentFilter.stringValue(numText);
        }
    }

    @Override
    public void enterLessMatch(OALParser.LessMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();
        String numText = ctx.numberConditionValue().getText();

        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.LESS);

        try {
            if (numText.contains(".")) {
                currentFilter.numberValue(Double.parseDouble(numText));
            } else {
                currentFilter.numberValue(Long.parseLong(numText));
            }
        } catch (NumberFormatException e) {
            currentFilter.stringValue(numText);
        }
    }

    @Override
    public void enterGreaterEqualMatch(OALParser.GreaterEqualMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();
        String numText = ctx.numberConditionValue().getText();

        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.GREATER_EQUAL);

        try {
            if (numText.contains(".")) {
                currentFilter.numberValue(Double.parseDouble(numText));
            } else {
                currentFilter.numberValue(Long.parseLong(numText));
            }
        } catch (NumberFormatException e) {
            currentFilter.stringValue(numText);
        }
    }

    @Override
    public void enterLessEqualMatch(OALParser.LessEqualMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();
        String numText = ctx.numberConditionValue().getText();

        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.LESS_EQUAL);

        try {
            if (numText.contains(".")) {
                currentFilter.numberValue(Double.parseDouble(numText));
            } else {
                currentFilter.numberValue(Long.parseLong(numText));
            }
        } catch (NumberFormatException e) {
            currentFilter.stringValue(numText);
        }
    }

    @Override
    public void enterLikeMatch(OALParser.LikeMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();
        String value = ctx.stringConditionValue().getText();
        value = value.substring(1, value.length() - 1); // Remove quotes

        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.LIKE);
        currentFilter.stringValue(value);
    }

    @Override
    public void enterNotEqualMatch(OALParser.NotEqualMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();
        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.NOT_EQUAL);

        // Could be numberConditionValue, stringConditionValue, enumConditionValue, or nullConditionValue
        if (ctx.numberConditionValue() != null) {
            String numText = ctx.numberConditionValue().getText();
            try {
                if (numText.contains(".")) {
                    currentFilter.numberValue(Double.parseDouble(numText));
                } else {
                    currentFilter.numberValue(Long.parseLong(numText));
                }
            } catch (NumberFormatException e) {
                currentFilter.stringValue(numText);
            }
        } else if (ctx.stringConditionValue() != null) {
            String value = ctx.stringConditionValue().getText();
            value = value.substring(1, value.length() - 1); // Remove quotes
            currentFilter.stringValue(value);
        } else if (ctx.enumConditionValue() != null) {
            String value = ctx.enumConditionValue().getText();
            currentFilter.enumValue(value);
        } else if (ctx.nullConditionValue() != null) {
            currentFilter.nullValue();
        }
    }

    @Override
    public void enterBooleanNotEqualMatch(OALParser.BooleanNotEqualMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();
        boolean value = Boolean.parseBoolean(ctx.booleanConditionValue().getText());

        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.NOT_EQUAL);
        currentFilter.booleanValue(value);
    }

    @Override
    public void enterInMatch(OALParser.InMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();
        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.IN);

        // Parse multiConditionValue - we'll collect the array values
        // The values are parsed by enterNumberConditionValue, enterStringConditionValue, etc.
        // For now, create an empty array that will be populated by child handlers
        List<Object> values = new ArrayList<>();

        // Access the multiConditionValue context
        OALParser.MultiConditionValueContext multiCtx = ctx.multiConditionValue();
        if (multiCtx != null) {
            // Parse number values
            List<OALParser.NumberConditionValueContext> numValues = multiCtx.numberConditionValue();
            if (numValues != null && !numValues.isEmpty()) {
                for (OALParser.NumberConditionValueContext numCtx : numValues) {
                    String numText = numCtx.getText();
                    try {
                        if (numText.contains(".")) {
                            values.add(Double.parseDouble(numText));
                        } else {
                            values.add(Long.parseLong(numText));
                        }
                    } catch (NumberFormatException e) {
                        values.add(numText);
                    }
                }
            }

            // Parse string values
            List<OALParser.StringConditionValueContext> strValues = multiCtx.stringConditionValue();
            if (strValues != null && !strValues.isEmpty()) {
                for (OALParser.StringConditionValueContext strCtx : strValues) {
                    String str = strCtx.getText();
                    str = str.substring(1, str.length() - 1); // Remove quotes
                    values.add(str);
                }
            }

            // Parse enum values
            List<OALParser.EnumConditionValueContext> enumValues = multiCtx.enumConditionValue();
            if (enumValues != null && !enumValues.isEmpty()) {
                for (OALParser.EnumConditionValueContext enumCtx : enumValues) {
                    values.add(enumCtx.getText());
                }
            }
        }

        currentFilter.arrayValue(values);
    }

    @Override
    public void enterContainMatch(OALParser.ContainMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();
        String value = ctx.stringConditionValue().getText();
        value = value.substring(1, value.length() - 1); // Remove quotes

        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.CONTAIN);
        currentFilter.stringValue(value);
    }

    @Override
    public void enterNotContainMatch(OALParser.NotContainMatchContext ctx) {
        if (currentFilter == null) {
            return;
        }

        String field = ctx.conditionAttributeStmt().getText();
        String value = ctx.stringConditionValue().getText();
        value = value.substring(1, value.length() - 1); // Remove quotes

        currentFilter.fieldName(field);
        currentFilter.operator(FilterOperator.NOT_CONTAIN);
        currentFilter.stringValue(value);
    }

    @Override
    public void exitFilterStatement(OALParser.FilterStatementContext ctx) {
        if (currentFilter != null && currentMetric != null) {
            currentMetric.addFilter(currentFilter.build());
            currentFilter = null;
        }
    }

    @Override
    public void enterDisableSource(OALParser.DisableSourceContext ctx) {
        if (ctx.IDENTIFIER() != null) {
            disabledSources.add(ctx.IDENTIFIER().getText());
        }
    }
}
