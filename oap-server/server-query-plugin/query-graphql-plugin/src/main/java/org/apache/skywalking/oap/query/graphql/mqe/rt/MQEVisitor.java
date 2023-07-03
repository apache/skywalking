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

package org.apache.skywalking.oap.query.graphql.mqe.rt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.query.graphql.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.query.graphql.mqe.rt.operation.AggregationOp;
import org.apache.skywalking.oap.query.graphql.resolver.MetricsQuery;
import org.apache.skywalking.oap.query.graphql.resolver.RecordsQuery;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResult;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResultType;
import org.apache.skywalking.oap.query.graphql.type.mql.MQEValue;
import org.apache.skywalking.oap.query.graphql.type.mql.MQEValues;
import org.apache.skywalking.oap.query.graphql.type.mql.Metadata;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.grammar.MQEParserBaseVisitor;

import static org.apache.skywalking.oap.query.graphql.mqe.rt.operation.BinaryOp.doBinaryOp;
import static org.apache.skywalking.oap.query.graphql.mqe.rt.operation.FunctionOp.doFunction0Op;
import static org.apache.skywalking.oap.query.graphql.mqe.rt.operation.FunctionOp.doFunction1Op;

@Slf4j
public class MQEVisitor extends MQEParserBaseVisitor<ExpressionResult> {
    private final MetricsQuery metricsQuery;
    private final RecordsQuery recordsQuery;
    private final Entity entity;
    private final Duration duration;

    private final static String LABEL = "label";

    public MQEVisitor(final MetricsQuery metricsQuery,
                      final RecordsQuery recordsQuery,
                      final Entity entity,
                      final Duration duration) {
        this.metricsQuery = metricsQuery;
        this.recordsQuery = recordsQuery;
        this.entity = entity;
        this.duration = duration;
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
            return doBinaryOp(left, right, opType);
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
            return doBinaryOp(left, right, opType);
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
            return AggregationOp.doAggregationOp(expResult, opType, ctx.parameter());
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
            return doFunction0Op(expResult, opType);
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
            return doFunction1Op(expResult, opType, Integer.parseInt(ctx.parameter().INTEGER().getText()));
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
            // Resever the original result type
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
            // Resever the original result type
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
    public ExpressionResult visitMetric(MQEParser.MetricContext ctx) {
        ExpressionResult result = new ExpressionResult();
        String metricName = ctx.metricName().getText();
        Optional<ValueColumnMetadata.ValueColumn> valueColumn = ValueColumnMetadata.INSTANCE.readValueColumnDefinition(
            metricName);
        if (valueColumn.isEmpty()) {
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError("Metric: [" + metricName + "] dose not exist.");
            return result;
        }

        Column.ValueDataType dataType = valueColumn.get().getDataType();
        try {
            if (Column.ValueDataType.COMMON_VALUE == dataType) {
                if (ctx.parent instanceof MQEParser.TopNOPContext) {
                    MQEParser.TopNOPContext parent = (MQEParser.TopNOPContext) ctx.parent;
                    querySortMetrics(metricName, Integer.parseInt(parent.parameter().getText()),
                                     Order.valueOf(parent.order().getText().toUpperCase()), result
                    );
                } else {
                    queryMetrics(metricName, result);
                }
            } else if (Column.ValueDataType.LABELED_VALUE == dataType) {
                List<String> queryLabelList = Collections.emptyList();
                if (ctx.label() != null) {
                    String labelValue = ctx.label().labelValue().getText();
                    String labelValueTrim = labelValue.substring(1, labelValue.length() - 1);
                    if (StringUtil.isNotBlank(labelValueTrim)) {
                        queryLabelList = Arrays.asList(labelValueTrim.split(Const.COMMA));
                    }
                }
                queryLabeledMetrics(metricName, queryLabelList, result);
            } else if (Column.ValueDataType.SAMPLED_RECORD == dataType) {
                if (ctx.parent instanceof MQEParser.TopNOPContext) {
                    MQEParser.TopNOPContext parent = (MQEParser.TopNOPContext) ctx.parent;
                    queryRecords(metricName, Integer.parseInt(parent.parameter().getText()),
                                 Order.valueOf(parent.order().getText().toUpperCase()), result
                    );
                } else {
                    throw new IllegalExpressionException(
                        "Metric: [" + metricName + "] is topN record, need top_n function for query.");
                }
            }
        } catch (IllegalExpressionException e) {
            ExpressionResult errorResult = new ExpressionResult();
            errorResult.setType(ExpressionResultType.UNKNOWN);
            errorResult.setError(e.getMessage());
            return errorResult;
        } catch (IOException e) {
            ExpressionResult errorResult = new ExpressionResult();
            errorResult.setType(ExpressionResultType.UNKNOWN);
            errorResult.setError("Internal IO exception, query metrics error.");
            log.error("Query metrics from backend error.", e);
            return errorResult;
        }
        return result;
    }

    private void querySortMetrics(String metricName,
                                  int topN,
                                  Order order,
                                  ExpressionResult result) throws IOException {
        TopNCondition topNCondition = new TopNCondition();
        topNCondition.setName(metricName);
        topNCondition.setTopN(topN);
        topNCondition.setParentService(entity.getServiceName());
        topNCondition.setOrder(order);
        topNCondition.setNormal(entity.getNormal());
        List<SelectedRecord> selectedRecords = metricsQuery.sortMetrics(topNCondition, duration);

        List<MQEValue> mqeValueList = new ArrayList<>(selectedRecords.size());
        selectedRecords.forEach(selectedRecord -> {
            MQEValue mqeValue = new MQEValue();
            mqeValue.setId(selectedRecord.getName());
            mqeValue.setEmptyValue(false);
            mqeValue.setDoubleValue(Double.parseDouble(selectedRecord.getValue()));
            mqeValueList.add(mqeValue);
        });
        Metadata metadata = new Metadata();
        MQEValues mqeValues = new MQEValues();
        mqeValues.setValues(mqeValueList);
        mqeValues.setMetric(metadata);
        result.getResults().add(mqeValues);
        result.setType(ExpressionResultType.SORTED_LIST);
    }

    private void queryRecords(String metricName, int topN, Order order, ExpressionResult result) throws IOException {
        RecordCondition recordCondition = new RecordCondition();
        recordCondition.setName(metricName);
        recordCondition.setTopN(topN);
        recordCondition.setParentEntity(entity);
        recordCondition.setOrder(order);
        List<Record> records = recordsQuery.readRecords(recordCondition, duration);

        List<MQEValue> mqeValueList = new ArrayList<>(records.size());
        records.forEach(record -> {
            MQEValue mqeValue = new MQEValue();
            mqeValue.setId(record.getName());
            mqeValue.setEmptyValue(false);
            mqeValue.setDoubleValue(Double.parseDouble(record.getValue()));
            mqeValue.setTraceID(record.getRefId());
            mqeValueList.add(mqeValue);
        });
        Metadata metadata = new Metadata();
        MQEValues mqeValues = new MQEValues();
        mqeValues.setValues(mqeValueList);
        mqeValues.setMetric(metadata);
        result.getResults().add(mqeValues);
        result.setType(ExpressionResultType.RECORD_LIST);
    }

    private void queryMetrics(String metricName, ExpressionResult result) throws IOException {
        MetricsCondition metricsCondition = new MetricsCondition();
        metricsCondition.setName(metricName);
        metricsCondition.setEntity(entity);
        MetricsValues metricsValues = metricsQuery.readMetricsValues(metricsCondition, duration);
        List<PointOfTime> times = duration.assembleDurationPoints();
        List<MQEValue> mqeValueList = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            long retTimestamp = DurationUtils.INSTANCE.parseToDateTime(duration.getStep(), times.get(i).getPoint())
                                                      .getMillis();
            KVInt kvInt = metricsValues.getValues().getValues().get(i);
            MQEValue mqeValue = new MQEValue();
            mqeValue.setId(Long.toString(retTimestamp));
            mqeValue.setEmptyValue(kvInt.isEmptyValue());
            mqeValue.setDoubleValue(kvInt.getValue());
            mqeValueList.add(mqeValue);
        }
        Metadata metadata = new Metadata();
        MQEValues mqeValues = new MQEValues();
        mqeValues.setValues(mqeValueList);
        mqeValues.setMetric(metadata);
        result.getResults().add(mqeValues);
        result.setType(ExpressionResultType.TIME_SERIES_VALUES);
    }

    private void queryLabeledMetrics(String metricName,
                                     List<String> queryLabelList,
                                     ExpressionResult result) throws IOException {
        MetricsCondition metricsCondition = new MetricsCondition();
        metricsCondition.setName(metricName);
        metricsCondition.setEntity(entity);
        List<MetricsValues> metricsValuesList = metricsQuery.readLabeledMetricsValues(
            metricsCondition, queryLabelList, duration);
        List<PointOfTime> times = duration.assembleDurationPoints();
        metricsValuesList.forEach(metricsValues -> {
            List<MQEValue> mqeValueList = new ArrayList<>(times.size());
            for (int i = 0; i < times.size(); i++) {
                long retTimestamp = DurationUtils.INSTANCE.parseToDateTime(duration.getStep(), times.get(i).getPoint())
                                                          .getMillis();
                KVInt kvInt = metricsValues.getValues().getValues().get(i);
                MQEValue mqeValue = new MQEValue();
                mqeValue.setEmptyValue(kvInt.isEmptyValue());
                mqeValue.setId(Long.toString(retTimestamp));
                mqeValueList.add(mqeValue);
                if (!kvInt.isEmptyValue()) {
                    mqeValue.setDoubleValue(kvInt.getValue());
                }
            }

            Metadata metadata = new Metadata();
            KeyValue labelValue = new KeyValue(LABEL, metricsValues.getLabel());
            metadata.getLabels().add(labelValue);
            MQEValues mqeValues = new MQEValues();
            mqeValues.setValues(mqeValueList);
            mqeValues.setMetric(metadata);
            result.getResults().add(mqeValues);
        });
        result.setType(ExpressionResultType.TIME_SERIES_VALUES);
        result.setLabeledResult(true);
    }
}
