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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.grammar.MQEParserBaseVisitor;
import org.apache.skywalking.mqe.rt.operation.AggregateLabelsOp;
import org.apache.skywalking.mqe.rt.operation.AggregationOp;
import org.apache.skywalking.mqe.rt.operation.BinaryOp;
import org.apache.skywalking.mqe.rt.operation.FunctionOp;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.type.ExpressionResultType;
import org.apache.skywalking.mqe.rt.type.MQEValue;
import org.apache.skywalking.mqe.rt.type.MQEValues;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Slf4j
public abstract class MQEVisitorBase extends MQEParserBaseVisitor<ExpressionResult> {

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
            return AggregateLabelsOp.doAggregateLabelsOp(expResult, funcType);
        } catch (IllegalExpressionException e) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError(e.getMessage());
            return result;
        }
    }

    @Override
    public ExpressionResult visitFunction0OP(MQEParser.Function0OPContext ctx) {
        int opType = ctx.function0().getStart().getType();
        ExpressionResult expResult = visit(ctx.expression());
        if (StringUtil.isNotEmpty(expResult.getError())) {
            return expResult;
        }
        try {
            return FunctionOp.doFunction0Op(expResult, opType);
        } catch (IllegalExpressionException e) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError(e.getMessage());
            return result;
        }
    }

    @Override
    public ExpressionResult visitFunction1OP(MQEParser.Function1OPContext ctx) {
        int opType = ctx.function1().getStart().getType();
        ExpressionResult expResult = visit(ctx.expression());
        if (StringUtil.isNotEmpty(expResult.getError())) {
            return expResult;
        }
        try {
            return FunctionOp.doFunction1Op(expResult, opType, Integer.parseInt(ctx.parameter().INTEGER().getText()));
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

        List<String> relabelList = Collections.emptyList();
        String newLabelValue = ctx.label().labelValue().getText();
        String labelValueTrim = newLabelValue.substring(1, newLabelValue.length() - 1);
        if (StringUtil.isNotBlank(labelValueTrim)) {
            relabelList = Arrays.asList(labelValueTrim.split(Const.COMMA));
        }
        List<MQEValues> mqeValuesList = result.getResults();

        if (mqeValuesList.size() != relabelList.size()) {
            // Reserve the original result type
            result.setError("The number of relabels is not equal to the number of labels.");
            return result;
        }
        // For now, we only have a single label named `label`
        for (int i = 0; i < mqeValuesList.size(); i++) {
            mqeValuesList.get(i).getMetric().getLabels().get(0).setValue(relabelList.get(i));
        }

        return result;
    }

    @Override
    public abstract ExpressionResult visitMetric(MQEParser.MetricContext ctx);
}
