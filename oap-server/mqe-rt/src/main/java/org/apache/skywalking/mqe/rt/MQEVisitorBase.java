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

package org.apache.skywalking.mqe.rt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.grammar.MQEParserBaseVisitor;
import org.apache.skywalking.mqe.rt.operation.AggregateLabelsOp;
import org.apache.skywalking.mqe.rt.operation.AggregationOp;
import org.apache.skywalking.mqe.rt.operation.BinaryOp;
import org.apache.skywalking.mqe.rt.operation.CompareOp;
import org.apache.skywalking.mqe.rt.operation.LogicalFunctionOp;
import org.apache.skywalking.mqe.rt.operation.MathematicalFunctionOp;
import org.apache.skywalking.mqe.rt.operation.TrendOp;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.type.ExpressionResultType;
import org.apache.skywalking.mqe.rt.type.MQEValue;
import org.apache.skywalking.mqe.rt.type.MQEValues;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Slf4j
public abstract class MQEVisitorBase extends MQEParserBaseVisitor<ExpressionResult> {
    public final Step queryStep;

    protected MQEVisitorBase(final Step queryStep) {
        this.queryStep = queryStep;
    }

    @Override
    public ExpressionResult visitParensOp(MQEParser.ParensOpContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public ExpressionResult visitAddSubOp(MQEParser.AddSubOpContext ctx) {
        ExpressionResult left = visit(ctx.expression(0));
        if (StringUtil.isNotBlank(left.getError())) {
            return left;
        }
        ExpressionResult right = visit(ctx.expression(1));
        if (StringUtil.isNotBlank(right.getError())) {
            return right;
        }
        int opType = ctx.addSub().getStart().getType();
        try {
            return BinaryOp.doBinaryOp(left, right, opType);
        } catch (IllegalExpressionException e) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError(e.getMessage());
            return result;
        }
    }

    @Override
    public ExpressionResult visitMulDivModOp(MQEParser.MulDivModOpContext ctx) {
        ExpressionResult left = visit(ctx.expression(0));
        if (StringUtil.isNotBlank(left.getError())) {
            return left;
        }
        ExpressionResult right = visit(ctx.expression(1));
        if (StringUtil.isNotBlank(right.getError())) {
            return right;
        }
        int opType = ctx.mulDivMod().getStart().getType();
        try {
            return BinaryOp.doBinaryOp(left, right, opType);
        } catch (IllegalExpressionException e) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError(e.getMessage());
            return result;
        }
    }

    @Override
    public ExpressionResult visitScalar(MQEParser.ScalarContext ctx) {
        ExpressionResult result = new ExpressionResult();
        double value = Double.parseDouble(ctx.getText());
        MQEValue mqeValue = new MQEValue();
        mqeValue.setDoubleValue(value);
        mqeValue.setEmptyValue(false);
        MQEValues mqeValues = new MQEValues();
        mqeValues.getValues().add(mqeValue);
        result.getResults().add(mqeValues);
        result.setType(ExpressionResultType.SINGLE_VALUE);
        return result;
    }

    @Override
    public ExpressionResult visitAggregationOp(MQEParser.AggregationOpContext ctx) {
        int opType = ctx.aggregation().getStart().getType();
        ExpressionResult expResult = visit(ctx.expression());
        if (StringUtil.isNotEmpty(expResult.getError())) {
            return expResult;
        }
        try {
            return AggregationOp.doAggregationOp(expResult, opType);
        } catch (IllegalExpressionException e) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError(e.getMessage());
            return result;
        }
    }

    @Override
    public ExpressionResult visitAggregateLabelsOp(final MQEParser.AggregateLabelsOpContext ctx) {
        int funcType = ctx.aggregateLabelsFunc().getStart().getType();
        ExpressionResult expResult = visit(ctx.expression());
        if (StringUtil.isNotEmpty(expResult.getError())) {
            return expResult;
        }

        if (!expResult.isLabeledResult()) {
            expResult.setError("The result of expression [" + ctx.expression().getText() + "] is not a labeled result.");
            return expResult;
        }

        try {
            if (expResult.getResults().isEmpty()) {
                return expResult;
            }
            List<String> labelNames = new ArrayList<>();
            MQEParser.LabelNameListContext labelNameListContext = ctx.aggregateLabelsFunc().labelNameList();
            if (null != labelNameListContext) {
                for (MQEParser.LabelNameContext labelNameContext : labelNameListContext.labelName()) {
                    // ignore the label name that does not exist in the result
                    if (expResult.getResults()
                                 .get(0)
                                 .getMetric()
                                 .getLabels()
                                 .stream()
                                 .anyMatch(label -> label.getKey().equals(labelNameContext.getText()))) {
                        labelNames.add(labelNameContext.getText());
                    }
                }
            }
            return AggregateLabelsOp.doAggregateLabelsOp(expResult, funcType, labelNames);
        } catch (IllegalExpressionException e) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError(e.getMessage());
            return result;
        }
    }

    @Override
    public ExpressionResult visitMathematicalOperator0OP(MQEParser.MathematicalOperator0OPContext ctx) {
        int opType = ctx.mathematical_operator0().getStart().getType();
        ExpressionResult expResult = visit(ctx.expression());
        if (StringUtil.isNotEmpty(expResult.getError())) {
            return expResult;
        }
        try {
            return MathematicalFunctionOp.doFunction0Op(expResult, opType);
        } catch (IllegalExpressionException e) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError(e.getMessage());
            return result;
        }
    }

    @Override
    public ExpressionResult visitMathematicalOperator1OP(MQEParser.MathematicalOperator1OPContext ctx) {
        int opType = ctx.mathematical_operator1().getStart().getType();
        ExpressionResult expResult = visit(ctx.expression());
        if (StringUtil.isNotEmpty(expResult.getError())) {
            return expResult;
        }
        try {
            return MathematicalFunctionOp.doFunction1Op(expResult, opType, Integer.parseInt(ctx.parameter().INTEGER().getText()));
        } catch (IllegalExpressionException e) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError(e.getMessage());
            return result;
        }
    }

    @Override
    public ExpressionResult visitTopNOP(MQEParser.TopNOPContext ctx) {
        return visit(ctx.metric());
    }

    @Override
    public ExpressionResult visitRelablesOP(MQEParser.RelablesOPContext ctx) {
        ExpressionResult result = visit(ctx.expression());
        if (!result.isLabeledResult()) {
            // Reserve the original result type
            result.setError("The result of expression [" + ctx.expression().getText() + "] is not a labeled result.");
            return result;
        }
        KeyValue targetLabel = buildLabel(ctx.label());
        KeyValue replaceLabel = buildLabel(ctx.replaceLabel().label());
        List<KeyValue> targetLabels = parseLabelValue(targetLabel, Const.COMMA);
        List<KeyValue> replaceLabels = parseLabelValue(replaceLabel, Const.COMMA);

        Map<KeyValue, KeyValue> relabelMap = new HashMap<>();
        if (targetLabels.isEmpty() || replaceLabels.isEmpty()) {
            return result;
        }
        if (targetLabels.size() != replaceLabels.size()) {
            result.setError(
                "Targrt label [" + targetLabel.getKey() + "]: the number of relabel values is not equal to the number of replace label.");
            return result;
        }
        for (int i = 0; i < targetLabels.size(); i++) {
            relabelMap.put(targetLabels.get(i), replaceLabels.get(i));
        }
        for (MQEValues mqeValues : result.getResults()) {
            for (KeyValue label : mqeValues.getMetric().getLabels()) {
                if (relabelMap.containsKey(label)) {
                    KeyValue replaceLabelValue = relabelMap.get(label);
                    label.setKey(replaceLabelValue.getKey());
                    label.setValue(replaceLabelValue.getValue());
                }
            }
        }
        return result;
    }

    @Override
    public ExpressionResult visitLogicalOperatorOP(MQEParser.LogicalOperatorOPContext ctx) {
        int opType = ctx.logical_operator().getStart().getType();
        try {
            return LogicalFunctionOp.doOP(opType, ctx.expressionList(), this);
        } catch (IllegalExpressionException e) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError(e.getMessage());
            return result;
        }
    }

    @Override
    public ExpressionResult visitCompareOp(MQEParser.CompareOpContext ctx) {
        ExpressionResult left = visit(ctx.expression(0));
        if (StringUtil.isNotBlank(left.getError())) {
            return left;
        }
        ExpressionResult right = visit(ctx.expression(1));
        if (StringUtil.isNotBlank(right.getError())) {
            return right;
        }
        int opType = ctx.compare().getStart().getType();
        try {
            ExpressionResult result = CompareOp.doCompareOP(left, right, opType);
            if (ctx.parent == null) {
                result.setBoolResult(true);
            }
            return result;
        } catch (IllegalExpressionException e) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError(e.getMessage());
            return result;
        }
    }

    @Override
    public ExpressionResult visitTrendOP(MQEParser.TrendOPContext ctx) {
        int opType = ctx.trend().getStart().getType();
        int trendRange = Integer.parseInt(ctx.INTEGER().getText());
        if (trendRange < 1) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError("The trend range must be greater than 0.");
            return result;
        }
        ExpressionResult expResult = visit(ctx.metric());
        if (StringUtil.isNotEmpty(expResult.getError())) {
            return expResult;
        }
        if (expResult.getType() != ExpressionResultType.TIME_SERIES_VALUES) {
            expResult.setError("The result of expression [" + ctx.metric().getText() + "] is not a time series result.");
            return expResult;
        }
        try {
            return TrendOp.doTrendOp(expResult, opType, trendRange, queryStep);
        } catch (IllegalExpressionException e) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError(e.getMessage());
            return result;
        }
    }

    @Override
    public abstract ExpressionResult visitMetric(MQEParser.MetricContext ctx);

    protected KeyValue buildLabel(MQEParser.LabelContext ctx) {
        String labelName = ctx.labelName().getText();
        String labelValue = ctx.labelValue().getText();
        String labelValueTrim = labelValue.substring(1, labelValue.length() - 1);
        return new KeyValue(labelName, labelValueTrim);
    }

    protected List<KeyValue> buildLabels(MQEParser.LabelListContext ctx) {
        List<KeyValue> labels = new ArrayList<>();
        if (ctx != null) {
            for (MQEParser.LabelContext labelContext : ctx.label()) {
                labels.add(buildLabel(labelContext));
            }
        }
        return labels;
    }

    protected List<KeyValue> parseLabelValue(KeyValue keyValue, String separator) {
        List<KeyValue> labels = new ArrayList<>();
        if (keyValue != null) {
            if (StringUtil.isNotBlank(keyValue.getValue())) {
                if (keyValue.getValue().indexOf(separator) > 0) {
                    String[] subValues = keyValue.getValue().split(separator);
                    for (String subValue : subValues) {
                        labels.add(new KeyValue(keyValue.getKey(), subValue));
                    }
                } else {
                    labels.add(keyValue);
                }
            }
        }
        return labels;
    }
}
