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

package org.apache.skywalking.oal.tool.parser;

import java.util.List;
import org.antlr.v4.runtime.misc.NotNull;
import org.apache.skywalking.oal.tool.grammar.*;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;

public class OALListener extends OALParserBaseListener {
    private List<AnalysisResult> results;
    private AnalysisResult current;
    private DisableCollection collection;

    private ConditionExpression conditionExpression;

    public OALListener(OALScripts scripts) {
        this.results = scripts.getIndicatorStmts();
        this.collection = scripts.getDisableCollection();
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

    @Override public void enterSource(OALParser.SourceContext ctx) {
        current.setSourceName(ctx.getText());
        current.setSourceScopeId(DefaultScopeDefine.valueOf(metricNameFormat(ctx.getText())));
    }

    @Override
    public void enterSourceAttribute(OALParser.SourceAttributeContext ctx) {
        current.setSourceAttribute(ctx.getText());
    }

    @Override public void enterVariable(OALParser.VariableContext ctx) {
    }

    @Override public void exitVariable(OALParser.VariableContext ctx) {
        current.setVarName(ctx.getText());
        current.setMetricName(metricNameFormat(ctx.getText()));
        current.setTableName(ctx.getText().toLowerCase());
    }

    @Override public void enterFunctionName(OALParser.FunctionNameContext ctx) {
        current.setAggregationFunctionName(ctx.getText());
    }

    @Override public void enterFilterStatement(OALParser.FilterStatementContext ctx) {
        conditionExpression = new ConditionExpression();
    }

    @Override public void exitFilterStatement(OALParser.FilterStatementContext ctx) {
        current.addFilterExpressionsParserResult(conditionExpression);
        conditionExpression = null;
    }

    @Override public void enterFuncParamExpression(OALParser.FuncParamExpressionContext ctx) {
        conditionExpression = new ConditionExpression();
    }

    @Override public void exitFuncParamExpression(OALParser.FuncParamExpressionContext ctx) {
        current.addFuncConditionExpression(conditionExpression);
        conditionExpression = null;
    }

    /////////////
    // Expression
    ////////////
    @Override public void enterConditionAttribute(OALParser.ConditionAttributeContext ctx) {
        conditionExpression.setAttribute(ctx.getText());
    }

    @Override public void enterBooleanMatch(OALParser.BooleanMatchContext ctx) {
        conditionExpression.setExpressionType("booleanMatch");
    }

    @Override public void enterStringMatch(OALParser.StringMatchContext ctx) {
        conditionExpression.setExpressionType("stringMatch");
    }

    @Override public void enterGreaterMatch(OALParser.GreaterMatchContext ctx) {
        conditionExpression.setExpressionType("greaterMatch");
    }

    @Override public void enterGreaterEqualMatch(OALParser.GreaterEqualMatchContext ctx) {
        conditionExpression.setExpressionType("greaterEqualMatch");
    }

    @Override public void enterLessMatch(OALParser.LessMatchContext ctx) {
        conditionExpression.setExpressionType("lessMatch");
    }

    @Override public void enterLessEqualMatch(OALParser.LessEqualMatchContext ctx) {
        conditionExpression.setExpressionType("lessEqualMatch");
    }

    @Override public void enterBooleanConditionValue(OALParser.BooleanConditionValueContext ctx) {
        conditionExpression.setValue(ctx.getText());
    }

    @Override public void enterStringConditionValue(OALParser.StringConditionValueContext ctx) {
        conditionExpression.setValue(ctx.getText());
    }

    @Override public void enterEnumConditionValue(OALParser.EnumConditionValueContext ctx) {
        conditionExpression.setValue(ctx.getText());
    }

    @Override public void enterNumberConditionValue(OALParser.NumberConditionValueContext ctx) {
        conditionExpression.setValue(ctx.getText());
    }

    /////////////
    // Expression end.
    ////////////

    @Override public void enterLiteralExpression(OALParser.LiteralExpressionContext ctx) {
        current.addFuncArg(ctx.getText());
    }

    private String metricNameFormat(String source) {
        source = firstLetterUpper(source);
        int idx;
        while ((idx = source.indexOf("_")) > -1) {
            source = source.substring(0, idx) + firstLetterUpper(source.substring(idx + 1));
        }
        return source;
    }

    /**
     * Disable source
     *
     * @param ctx
     */
    @Override public void enterDisableSource(OALParser.DisableSourceContext ctx) {
        collection.add(ctx.getText());
    }

    private String firstLetterUpper(String source) {
        return source.substring(0, 1).toUpperCase() + source.substring(1);
    }
}
