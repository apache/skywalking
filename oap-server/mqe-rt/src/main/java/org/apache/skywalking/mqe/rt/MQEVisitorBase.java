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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.grammar.MQEParserBaseVisitor;
import org.apache.skywalking.mqe.rt.operation.AggregateLabelsOp;
import org.apache.skywalking.mqe.rt.operation.AggregationOp;
import org.apache.skywalking.mqe.rt.operation.BinaryOp;
import org.apache.skywalking.mqe.rt.operation.BoolOp;
import org.apache.skywalking.mqe.rt.operation.CompareOp;
import org.apache.skywalking.mqe.rt.operation.LogicalFunctionOp;
import org.apache.skywalking.mqe.rt.operation.MathematicalFunctionOp;
import org.apache.skywalking.mqe.rt.operation.SortLabelValuesOp;
import org.apache.skywalking.mqe.rt.operation.SortValuesOp;
import org.apache.skywalking.mqe.rt.operation.TopNOfOp;
import org.apache.skywalking.mqe.rt.operation.TrendOp;
import org.apache.skywalking.oap.server.baseline.BaselineModule;
import org.apache.skywalking.oap.server.baseline.service.BaselineQueryService;
import org.apache.skywalking.oap.server.baseline.service.PredictServiceMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataLabel;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResultType;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValue;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValues;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;
import static org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO.Util.composeLabelConditions;

@Slf4j
public abstract class MQEVisitorBase extends MQEParserBaseVisitor<ExpressionResult> {

    private final BiFunction<String, Step, String> getPredictTimeBucket = (timePoint, step) -> {
        switch (step) {
            case MINUTE:
                return Long.toString(Long.parseLong(timePoint) / 100);
            case HOUR:
                return timePoint;
            case DAY:
                return Long.toString(Long.parseLong(timePoint) * 100);
            default:
                throw new IllegalArgumentException("Unsupported time step: " + step);
        }
    };
    private BaselineQueryService baselineQueryService;
    protected final ModuleManager moduleManager;
    public final Step queryStep;

    protected MQEVisitorBase(final ModuleManager moduleManager, final Step queryStep) {
        this.moduleManager = moduleManager;
        this.queryStep = queryStep;
    }

    private BaselineQueryService getBaselineQueryService() {
        if (baselineQueryService == null) {
            this.baselineQueryService = moduleManager.find(BaselineModule.NAME)
                                                    .provider()
                                                    .getService(BaselineQueryService.class);
        }
        return baselineQueryService;
    }

    @Override
    public ExpressionResult visitParensOp(MQEParser.ParensOpContext ctx) {
        ExpressionResult result = visit(ctx.expression());
        if (result.isBoolResult()) {
            // The other operation will change the bool result of the expression
            if (!(ctx.parent instanceof MQEParser.BoolOPContext
                || ctx.parent instanceof MQEParser.ParensOpContext)) {
                result.setBoolResult(false);
            }
        }
        return result;
    }

    @Override
    public ExpressionResult visitAddSubOp(MQEParser.AddSubOpContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Binary OP: " + ctx.getText());
        try {
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
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitMulDivModOp(MQEParser.MulDivModOpContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Binary OP: " + ctx.getText());
        try {
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
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitScalar(MQEParser.ScalarContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Scalar: " + ctx.getText());
        try {
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
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitAggregationOp(MQEParser.AggregationOpContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Aggregation OP: " + ctx.getText());
        try {
            int opType = ctx.aggregation().getStart().getType();
            ExpressionResult expResult = visit(ctx.expression());
            if (StringUtil.isNotEmpty(expResult.getError())) {
                return expResult;
            }
            try {
                return AggregationOp.doAggregationOp(expResult, opType);
            } catch (IllegalExpressionException e) {
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitAggregateLabelsOp(final MQEParser.AggregateLabelsOpContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Aggregate Labels OP: " + ctx.getText());
        try {
            int funcType = ctx.aggregateLabelsFunc().getStart().getType();
            ExpressionResult expResult = visit(ctx.expression());
            if (StringUtil.isNotEmpty(expResult.getError())) {
                return expResult;
            }

            if (!expResult.isLabeledResult()) {
                expResult.setError(
                    "The result of expression [" + ctx.expression().getText() + "] is not a labeled result.");
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
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitMathematicalOperator0OP(MQEParser.MathematicalOperator0OPContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Mathematical OP: " + ctx.getText());
        try {
            int opType = ctx.mathematical_operator0().getStart().getType();
            ExpressionResult expResult = visit(ctx.expression());
            if (StringUtil.isNotEmpty(expResult.getError())) {
                return expResult;
            }
            try {
                return MathematicalFunctionOp.doFunction0Op(expResult, opType);
            } catch (IllegalExpressionException e) {
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitMathematicalOperator1OP(MQEParser.MathematicalOperator1OPContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Mathematical OP: " + ctx.getText());
        try {
            int opType = ctx.mathematical_operator1().getStart().getType();
            ExpressionResult expResult = visit(ctx.expression());
            if (StringUtil.isNotEmpty(expResult.getError())) {
                return expResult;
            }
            try {
                return MathematicalFunctionOp.doFunction1Op(expResult, opType, Integer.parseInt(ctx.parameter().INTEGER().getText()));
            } catch (IllegalExpressionException e) {
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitTopNOP(MQEParser.TopNOPContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE TopN OP: " + ctx.getText());
        try {
            return visit(ctx.topN().metric());
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitTopNOfOP(MQEParser.TopNOfOPContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE TopNOf OP: " + ctx.getText());
        try {
            List<MQEParser.TopNContext> topNContexts = ctx.topN();
            List<ExpressionResult> topNResults = new ArrayList<>();
            for (MQEParser.TopNContext topNContext : topNContexts) {
                topNResults.add(visit(topNContext.metric()));
            }
            try {
                return TopNOfOp.doMergeTopNResult(topNResults, Integer.parseInt(ctx.INTEGER().getText()), ctx.order().getStart().getType());
            } catch (IllegalExpressionException e) {
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitRelablesOP(MQEParser.RelablesOPContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Relabels OP: " + ctx.getText());
        try {
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
                    "Target label [" + targetLabel.getKey() + "]: the number of relabel values is not equal to the number of replace label.");
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
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitLogicalOperatorOP(MQEParser.LogicalOperatorOPContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Logical OP: " + ctx.getText());
        try {
            int opType = ctx.logical_operator().getStart().getType();
            try {
                return LogicalFunctionOp.doOP(opType, ctx.expressionList(), this);
            } catch (IllegalExpressionException e) {
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitCompareOp(MQEParser.CompareOpContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Compare OP: " + ctx.getText());
        try {
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
                // The following operation won't change the bool result of the expression
                if (ctx.parent == null ||
                    ctx.parent instanceof MQEParser.ParensOpContext ||
                    ctx.parent instanceof MQEParser.BoolOPContext) {
                    result.setBoolResult(true);
                }
                return result;
            } catch (IllegalExpressionException e) {
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitTrendOP(MQEParser.TrendOPContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Trend OP: " + ctx.getText());
        try {
            int opType = ctx.trend().getStart().getType();
            int trendRange = Integer.parseInt(ctx.INTEGER().getText());
            if (trendRange < 1) {
                return getErrorResult("The trend range must be greater than 0.");
            }
            ExpressionResult expResult = visit(ctx.metric());
            if (StringUtil.isNotEmpty(expResult.getError())) {
                return expResult;
            }
            if (expResult.getType() != ExpressionResultType.TIME_SERIES_VALUES) {
                expResult.setError(
                    "The result of expression [" + ctx.metric().getText() + "] is not a time series result.");
                return expResult;
            }
            try {
                return TrendOp.doTrendOp(expResult, opType, trendRange, queryStep);
            } catch (IllegalExpressionException e) {
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitSortValuesOP(MQEParser.SortValuesOPContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Sort Values OP: " + ctx.getText());
        try {
            ExpressionResult result = visit(ctx.expression());
            int order = ctx.order().getStart().getType();
            Optional<Integer> limit = Optional.empty();
            if (ctx.INTEGER() != null) {
                limit = Optional.of(Integer.valueOf(ctx.INTEGER().getText()));
            }
            try {
                return SortValuesOp.doSortValuesOp(result, limit, order);
            } catch (IllegalExpressionException e) {
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitSortLabelValuesOP(MQEParser.SortLabelValuesOPContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Sort Label Values OP: " + ctx.getText());
        try {
            ExpressionResult result = visit(ctx.expression());
            int order = ctx.order().getStart().getType();
            List<String> labelNames = new ArrayList<>();
            for (MQEParser.LabelNameContext labelNameContext : ctx.labelNameList().labelName()) {
                labelNames.add(labelNameContext.getText());
            }
            try {
                return SortLabelValuesOp.doSortLabelValuesOp(result, order, labelNames);
            } catch (IllegalExpressionException e) {
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitBoolOP(MQEParser.BoolOPContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Bool OP: " + ctx.getText());
        try {
            ExpressionResult left = visit(ctx.expression(0));
            if (StringUtil.isNotBlank(left.getError())) {
                return left;
            }
            ExpressionResult right = visit(ctx.expression(1));
            if (StringUtil.isNotBlank(right.getError())) {
                return right;
            }
            int opType = ctx.bool_operator().getStart().getType();
            try {
                ExpressionResult result = BoolOp.doBoolOp(left, right, opType);
                if (ctx.parent == null ||
                    ctx.parent instanceof MQEParser.ParensOpContext ||
                    ctx.parent instanceof MQEParser.BoolOPContext) {
                    result.setBoolResult(true);
                }
                return result;
            } catch (IllegalExpressionException e) {
                return getErrorResult(e.getMessage());
            }
        } finally {
            traceContext.stopSpan(span);
        }
    }

    @Override
    public ExpressionResult visitBaselineOP(MQEParser.BaselineOPContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Baseline OP: " + ctx.getText());
        try {
            return visit(ctx.metric());
        } finally {
            traceContext.stopSpan(span);
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

    protected ExpressionResult getErrorResult(String error) {
        ExpressionResult result = new ExpressionResult();
        result.setType(ExpressionResultType.UNKNOWN);
        result.setError(error);
        return result;
    }

    @SneakyThrows
    protected List<MQEValues> queryBaseline(String serviceName,
                                            String metricName,
                                            ArrayList<String> pointOfTimes,
                                            int valueType) {
        List<MQEValues> mqeValuesList = new ArrayList<>();
        MQEValues mqeValues = new MQEValues();
        for (String pointOfTime : pointOfTimes) {
            Map<String, PredictServiceMetrics.PredictMetricsValue> predictValues = getBaselineQueryService().queryPredictMetricsFromCache(
                serviceName, getPredictTimeBucket.apply(pointOfTime, queryStep));
            PredictServiceMetrics.PredictMetricsValue predictValue = predictValues.get(metricName);
            MQEValue mqeValue = new MQEValue();
            //use timeBucket as id here
            mqeValue.setId(pointOfTime);
            if (predictValue == null || predictValue.getSingleValue() == null) {
                mqeValue.setEmptyValue(true);
            } else {
                mqeValue.setDoubleValue(getValueByType(predictValue.getSingleValue(), valueType));
            }
            mqeValues.getValues().add(mqeValue);
        }
        mqeValuesList.add(mqeValues);
        return mqeValuesList;
    }

    @SneakyThrows
    protected List<MQEValues> queryLabeledBaseline(String serviceName,
                                                   String metricName,
                                                   List<KeyValue> queryLabels,
                                                   ArrayList<String> pointOfTimes,
                                                   int valueType) {
        Map<String, DataTable> timeValues = new HashMap<>();
        for (String pointOfTime : pointOfTimes) {
            Map<String, PredictServiceMetrics.PredictMetricsValue> predictValues = getBaselineQueryService().queryPredictMetricsFromCache(
                serviceName, getPredictTimeBucket.apply(pointOfTime, queryStep));
            PredictServiceMetrics.PredictMetricsValue predictValue = predictValues.get(metricName);
            if (predictValue != null && predictValue.getLabeledValue() != null) {
                DataTable dataTable = new DataTable();
                for (PredictServiceMetrics.PredictLabelValue labeledValue : predictValue.getLabeledValue()) {
                    DataLabel dataLabel = new DataLabel();
                    if (labeledValue != null) {
                        dataLabel.putAll(labeledValue.getLabels());
                        dataTable.put(dataLabel, getValueByType(labeledValue.getValue(), valueType));
                    }
                }
                timeValues.put(pointOfTime, dataTable);
            }
        }
        return buildLabledMqeValuesList(timeValues, queryLabels, pointOfTimes);
    }

    private long getValueByType(PredictServiceMetrics.PredictSingleValue predictSingleValue,
                                int valueType) {
        switch (valueType) {
            case MQEParser.VALUE:
                return predictSingleValue.getValue();
            case MQEParser.UPPER:
                return predictSingleValue.getUpperValue();
            case MQEParser.LOWER:
                return predictSingleValue.getLowerValue();
            default:
                throw new IllegalArgumentException("Unsupported predicted value type: " + valueType);
        }
    }

    protected List<MQEValues> buildLabledMqeValuesList(Map<String, DataTable> timeValues, List<KeyValue> queryLabels,
                                                       ArrayList<String> pointOfTimes) {
        List<MQEValues> mqeValuesList = new ArrayList<>();
        List<String> labelConditions = composeLabelConditions(queryLabels, timeValues.values());
        for (String labelCondition : labelConditions) {
            MQEValues mqeValues = new MQEValues();
            for (String pointOfTime : pointOfTimes) {
                DataTable dataTable = timeValues.getOrDefault(pointOfTime, new DataTable());
                Long metricValue = dataTable.get(labelCondition);
                MQEValue mqeValue = new MQEValue();
                //use timeBucket as id here
                mqeValue.setId(pointOfTime);
                if (metricValue != null) {
                    mqeValue.setDoubleValue(metricValue);
                } else {
                    mqeValue.setEmptyValue(true);
                }
                mqeValues.getValues().add(mqeValue);
            }
            DataLabel dataLabel = new DataLabel();
            dataLabel.put(labelCondition);
            for (Map.Entry<String, String> label : dataLabel.entrySet()) {
                mqeValues.getMetric().getLabels().add(new KeyValue(label.getKey(), label.getValue()));
            }
            //Sort labels by key in natural order by default
            mqeValues.getMetric().sortLabelsByKey(Comparator.naturalOrder());
            mqeValuesList.add(mqeValues);
        }
        return mqeValuesList;
    }
}

