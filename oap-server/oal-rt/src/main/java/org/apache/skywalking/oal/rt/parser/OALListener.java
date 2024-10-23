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

package org.apache.skywalking.oal.rt.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.antlr.v4.runtime.misc.NotNull;
import org.apache.skywalking.oal.rt.grammar.OALParser;
import org.apache.skywalking.oal.rt.grammar.OALParserBaseListener;
import org.apache.skywalking.oap.server.core.analysis.ISourceDecorator;
import org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ISource;

public class OALListener extends OALParserBaseListener {
    private List<AnalysisResult> results;
    private AnalysisResult current;
    private DisableCollection collection;

    private ConditionExpression conditionExpression;

    private final String sourcePackage;

    public OALListener(OALScripts scripts, String sourcePackage) {
        this.results = scripts.getMetricsStmts();
        this.collection = scripts.getDisableCollection();
        this.sourcePackage = sourcePackage;
    }

    @Override
    public void enterAggregationStatement(@NotNull OALParser.AggregationStatementContext ctx) {
        current = new AnalysisResult();
    }

    @Override
    public void exitAggregationStatement(@NotNull OALParser.AggregationStatementContext ctx) {
        DeepAnalysis deepAnalysis = new DeepAnalysis();
        results.add(deepAnalysis.analysis(current));
        current = null;
    }

    @Override
    public void enterSource(OALParser.SourceContext ctx) {
        current.getFrom().setSourceName(ctx.getText());
        current.getFrom().setSourceScopeId(DefaultScopeDefine.valueOf(metricsNameFormat(ctx.getText())));
    }

    @Override
    public void enterSourceAttribute(OALParser.SourceAttributeContext ctx) {
        current.getFrom().getSourceAttribute().add(ctx.getText());
    }

    @Override
    public void enterSourceAttrCast(OALParser.SourceAttrCastContext ctx) {
        current.getFrom().setSourceCastType(ctx.getText());
    }

    @Override
    public void enterVariable(OALParser.VariableContext ctx) {
    }

    @Override
    public void exitVariable(OALParser.VariableContext ctx) {
        current.setVarName(ctx.getText());
        current.setMetricsName(metricsNameFormat(ctx.getText()));
        current.setTableName(ctx.getText().toLowerCase());
    }

    @Override
    public void enterFunctionName(OALParser.FunctionNameContext ctx) {
        current.getAggregationFuncStmt().setAggregationFunctionName(ctx.getText());
    }

    @Override
    public void enterFilterStatement(OALParser.FilterStatementContext ctx) {
        conditionExpression = new ConditionExpression();
    }

    @Override
    public void exitFilterStatement(OALParser.FilterStatementContext ctx) {
        current.getFilters().addFilterExpressionsParserResult(conditionExpression);
        conditionExpression = null;
    }

    @Override
    public void enterFuncParamExpression(OALParser.FuncParamExpressionContext ctx) {
        conditionExpression = new ConditionExpression();
    }

    @Override
    public void exitFuncParamExpression(OALParser.FuncParamExpressionContext ctx) {
        current.getAggregationFuncStmt().addFuncConditionExpression(conditionExpression);
        conditionExpression = null;
    }

    @Override
    public void enterDecorateSource(OALParser.DecorateSourceContext ctx) {
        Class<? extends Metrics> metricsClass = MetricsHolder.find(current.getAggregationFuncStmt().getAggregationFunctionName());
        if (LabeledValueHolder.class.isAssignableFrom(metricsClass)) {
            throw new IllegalArgumentException(
                "OAL metric: " + current.getMetricsName() + ", decorate source not support labeled value metrics.");
        }
        String decoratorName = ctx.STRING_LITERAL().getText();
        String decoratorNameTrim = decoratorName.substring(1, decoratorName.length() - 1);
        current.setSourceDecorator(decoratorNameTrim);
        Map<String, ISourceDecorator<ISource>> map = SourceDecoratorManager.DECORATOR_MAP;
        int currentScopeId = current.getFrom().getSourceScopeId();
        if (currentScopeId != DefaultScopeDefine.SERVICE) {
            throw new IllegalArgumentException("OAL metric: " + current.getMetricsName() + ", decorate source only support service scope.");
        }
        ISourceDecorator<ISource> decorator = map.get(decoratorNameTrim);
        if (decorator == null) {
            throw new IllegalArgumentException("OAL metric: " + current.getMetricsName() + " define a decorator: " + decoratorNameTrim
            + ", but can't find it.");
        }
        int scopeId = decorator.getSourceScope();
        if (scopeId != currentScopeId) {
            throw new IllegalArgumentException("OAL Decorate Source, expect decorator scope id is: " + currentScopeId + ", but got: " + scopeId);
        }
    }

    /////////////
    // Expression
    ////////////
    @Override
    public void enterConditionAttribute(OALParser.ConditionAttributeContext ctx) {
        conditionExpression.getAttributes().add(ctx.getText());
    }

    @Override
    public void enterBooleanMatch(OALParser.BooleanMatchContext ctx) {
        conditionExpression.setExpressionType("booleanMatch");
    }

    @Override
    public void enterNumberMatch(OALParser.NumberMatchContext ctx) {
        conditionExpression.setExpressionType("numberMatch");
    }

    @Override
    public void enterStringMatch(OALParser.StringMatchContext ctx) {
        conditionExpression.setExpressionType("stringMatch");
    }

    @Override
    public void enterGreaterMatch(OALParser.GreaterMatchContext ctx) {
        conditionExpression.setExpressionType("greaterMatch");
    }

    @Override
    public void enterGreaterEqualMatch(OALParser.GreaterEqualMatchContext ctx) {
        conditionExpression.setExpressionType("greaterEqualMatch");
    }

    @Override
    public void enterLessMatch(OALParser.LessMatchContext ctx) {
        conditionExpression.setExpressionType("lessMatch");
    }

    @Override
    public void enterLessEqualMatch(OALParser.LessEqualMatchContext ctx) {
        conditionExpression.setExpressionType("lessEqualMatch");
    }

    @Override
    public void enterNotEqualMatch(final OALParser.NotEqualMatchContext ctx) {
        conditionExpression.setExpressionType("notEqualMatch");
    }

    @Override
    public void enterBooleanNotEqualMatch(final OALParser.BooleanNotEqualMatchContext ctx) {
        conditionExpression.setExpressionType("booleanNotEqualMatch");
    }

    @Override
    public void enterLikeMatch(final OALParser.LikeMatchContext ctx) {
        conditionExpression.setExpressionType("likeMatch");
    }

    @Override
    public void enterContainMatch(final OALParser.ContainMatchContext ctx) {
        conditionExpression.setExpressionType("containMatch");
    }

    @Override
    public void enterNotContainMatch(final OALParser.NotContainMatchContext ctx) {
        conditionExpression.setExpressionType("notContainMatch");
    }

    @Override
    public void enterInMatch(final OALParser.InMatchContext ctx) {
        conditionExpression.setExpressionType("inMatch");
    }

    @Override
    public void enterMultiConditionValue(final OALParser.MultiConditionValueContext ctx) {
        conditionExpression.enterMultiConditionValue();
    }

    @Override
    public void exitMultiConditionValue(final OALParser.MultiConditionValueContext ctx) {
        conditionExpression.exitMultiConditionValue();
    }

    @Override
    public void enterBooleanConditionValue(OALParser.BooleanConditionValueContext ctx) {
        enterConditionValue(ctx.getText());
    }

    @Override
    public void enterStringConditionValue(OALParser.StringConditionValueContext ctx) {
        enterConditionValue(ctx.getText());
    }

    @Override
    public void enterEnumConditionValue(OALParser.EnumConditionValueContext ctx) {
        enterEnumConditionValue(ctx.getText());
    }

    @Override
    public void enterNumberConditionValue(OALParser.NumberConditionValueContext ctx) {
        conditionExpression.isNumber();
        enterConditionValue(ctx.getText());
    }

    @Override
    public void enterNullConditionValue(OALParser.NullConditionValueContext ctx) {
        enterConditionValue(ctx.getText());
    }

    @Override
    public void enterExpressionAttrCast(final OALParser.ExpressionAttrCastContext ctx) {
        conditionExpression.setCastType(ctx.getText());
    }

    private void enterConditionValue(String value) {
        conditionExpression.addValue(value);
    }

    private void enterEnumConditionValue(String value) {
        conditionExpression.addValue(sourcePackage + value);
    }

    /////////////
    // Expression end.
    ////////////

    @Override
    public void enterLiteralExpression(OALParser.LiteralExpressionContext ctx) {
        current.getAggregationFuncStmt().addFuncArg(new Argument(EntryMethod.LITERAL_TYPE, Arrays.asList(ctx.getText())));
    }

    @Override
    public void enterAttributeExpression(final OALParser.AttributeExpressionContext ctx) {
        current.getAggregationFuncStmt().addFuncArg(new Argument(EntryMethod.ATTRIBUTE_EXP_TYPE, new ArrayList<>(3)));
    }

    @Override
    public void enterAttributeExpressionSegment(OALParser.AttributeExpressionSegmentContext ctx) {
        current.getAggregationFuncStmt().getLastArgument().addText(ctx.getText());
    }

    @Override
    public void enterFunctionArgCast(final OALParser.FunctionArgCastContext ctx) {
        current.getAggregationFuncStmt().getLastArgument().setCastType(ctx.getText());
    }

    private String metricsNameFormat(String source) {
        source = firstLetterUpper(source);
        int idx;
        while ((idx = source.indexOf("_")) > -1) {
            source = source.substring(0, idx) + firstLetterUpper(source.substring(idx + 1));
        }
        return source;
    }

    /**
     * Disable source
     */
    @Override
    public void enterDisableSource(OALParser.DisableSourceContext ctx) {
        collection.add(ctx.getText());
    }

    private String firstLetterUpper(String source) {
        return source.substring(0, 1).toUpperCase() + source.substring(1);
    }
}
