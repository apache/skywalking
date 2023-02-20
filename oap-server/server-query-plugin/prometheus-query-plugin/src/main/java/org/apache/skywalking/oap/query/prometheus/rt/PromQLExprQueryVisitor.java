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

package org.apache.skywalking.oap.query.prometheus.rt;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.query.graphql.resolver.MetricsQuery;
import org.apache.skywalking.oap.query.graphql.resolver.RecordsQuery;
import org.apache.skywalking.oap.query.prometheus.entity.ErrorType;
import org.apache.skywalking.oap.query.prometheus.entity.LabelName;
import org.apache.skywalking.oap.query.prometheus.entity.LabelValuePair;
import org.apache.skywalking.oap.query.prometheus.entity.MetricRangeData;
import org.apache.skywalking.oap.query.prometheus.entity.MetricInfo;
import org.apache.skywalking.oap.query.prometheus.handler.PrometheusApiHandler;
import org.apache.skywalking.oap.query.prometheus.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.query.prometheus.rt.result.MetricsRangeResult;
import org.apache.skywalking.oap.query.prometheus.rt.result.ParseResult;
import org.apache.skywalking.oap.query.prometheus.rt.result.ParseResultType;
import org.apache.skywalking.oap.query.prometheus.rt.result.ScalarResult;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.promql.rt.grammar.PromQLParser;
import org.apache.skywalking.promql.rt.grammar.PromQLParserBaseVisitor;

import static org.apache.skywalking.oap.query.prometheus.rt.PromOpUtils.buildMatrixValues;
import static org.apache.skywalking.oap.query.prometheus.rt.PromOpUtils.matrixBinaryOp;
import static org.apache.skywalking.oap.query.prometheus.rt.PromOpUtils.matrixCompareOp;
import static org.apache.skywalking.oap.query.prometheus.rt.PromOpUtils.matrixScalarBinaryOp;
import static org.apache.skywalking.oap.query.prometheus.rt.PromOpUtils.matrixScalarCompareOp;
import static org.apache.skywalking.oap.query.prometheus.rt.PromOpUtils.scalarBinaryOp;
import static org.apache.skywalking.oap.query.prometheus.rt.PromOpUtils.scalarCompareOp;
import static org.apache.skywalking.oap.query.prometheus.rt.PromOpUtils.timestamp2Duration;

@Slf4j
public class PromQLExprQueryVisitor extends PromQLParserBaseVisitor<ParseResult> {
    private final MetricsQuery metricsQuery;
    private final RecordsQuery recordsQuery;
    private final PrometheusApiHandler.QueryType queryType;
    private Duration duration;

    public PromQLExprQueryVisitor(final MetricsQuery metricsQuery,
                                  final RecordsQuery recordsQuery,
                                  final Duration duration,
                                  final PrometheusApiHandler.QueryType queryType) {
        this.metricsQuery = metricsQuery;
        this.recordsQuery = recordsQuery;
        this.duration = duration;
        this.queryType = queryType;
    }

    @Override
    public ParseResult visitAddSubOp(PromQLParser.AddSubOpContext ctx) {
        ParseResult left = visit(ctx.expression(0));
        if (StringUtil.isNotBlank(left.getErrorInfo())) {
            return left;
        }
        ParseResult right = visit(ctx.expression(1));
        if (StringUtil.isNotBlank(right.getErrorInfo())) {
            return right;
        }
        int opType = ctx.addSub().getStart().getType();
        return binaryOp(left, right, opType);
    }

    @Override
    public ParseResult visitMulDivModOp(PromQLParser.MulDivModOpContext ctx) {
        ParseResult left = visit(ctx.expression(0));
        if (StringUtil.isNotBlank(left.getErrorInfo())) {
            return left;
        }
        ParseResult right = visit(ctx.expression(1));
        if (StringUtil.isNotBlank(right.getErrorInfo())) {
            return right;
        }
        int opType = ctx.mulDivMod().getStart().getType();
        return binaryOp(left, right, opType);
    }

    @Override
    public ParseResult visitCompareOp(PromQLParser.CompareOpContext ctx) {
        ParseResult left = visit(ctx.expression(0));
        if (StringUtil.isNotBlank(left.getErrorInfo())) {
            return left;
        }
        ParseResult right = visit(ctx.expression(1));
        if (StringUtil.isNotBlank(right.getErrorInfo())) {
            return right;
        }
        boolean boolModifier = ctx.compare().BOOL() != null;
        int opType = ctx.compare().getStart().getType();
        return compareOp(left, right, opType, boolModifier);
    }

    private ParseResult compareOp(ParseResult left, ParseResult right, int opType, boolean boolModifier) {
        try {
            if (left.getResultType() == ParseResultType.scalar && right.getResultType() == ParseResultType.scalar) {
                if (!boolModifier) {
                    throw new IllegalExpressionException("Comparisons between scalars must use BOOL modifier.");
                } else {
                    ScalarResult scalarLeft = (ScalarResult) left;
                    ScalarResult scalarRight = (ScalarResult) right;
                    int value = scalarCompareOp(scalarLeft.getValue(), scalarRight.getValue(), opType);
                    ScalarResult scalarResult = new ScalarResult();
                    scalarResult.setValue(value);
                    scalarResult.setResultType(ParseResultType.scalar);
                    return scalarResult;
                }
            } else if (left.getResultType() == ParseResultType.metrics_range && right.getResultType() == ParseResultType.scalar) {
                return matrixScalarCompareOp((MetricsRangeResult) left, (ScalarResult) right, opType);
            } else if (left.getResultType() == ParseResultType.scalar && right.getResultType() == ParseResultType.metrics_range) {
                return matrixScalarCompareOp((MetricsRangeResult) right, (ScalarResult) left, opType);
            } else if (left.getResultType() == ParseResultType.metrics_range && right.getResultType() == ParseResultType.metrics_range) {
                try {
                    return matrixCompareOp((MetricsRangeResult) left, (MetricsRangeResult) right, opType);
                } catch (IllegalExpressionException e) {
                    MetricsRangeResult result = new MetricsRangeResult();
                    result.setErrorType(ErrorType.bad_data);
                    result.setErrorInfo(e.getMessage());
                    return result;
                }
            }
        } catch (IllegalExpressionException e) {
            MetricsRangeResult result = new MetricsRangeResult();
            result.setErrorType(ErrorType.bad_data);
            result.setErrorInfo(e.getMessage());
            return result;
        }
        return new ParseResult();
    }

    @Override
    public ParseResult visitNumberLiteral(PromQLParser.NumberLiteralContext ctx) {
        ScalarResult result = new ScalarResult();
        double value = Double.parseDouble(ctx.NUMBER().getText());
        result.setValue(value);
        result.setResultType(ParseResultType.scalar);
        return result;
    }

    @Override
    public ParseResult visitMetricInstant(PromQLParser.MetricInstantContext ctx) {
        ParseResult result = new ParseResult();
        try {
            String metricName = ctx.metricName().getText();
            Optional<ValueColumnMetadata.ValueColumn> valueColumn = getValueColumn(metricName);
            if (!valueColumn.isPresent()) {
                result.setErrorType(ErrorType.bad_data);
                result.setErrorInfo("Metric: [" + metricName + "] dose not exist.");
                return result;
            }
            if (ctx.labelList() == null) {
                result.setErrorType(ErrorType.bad_data);
                result.setErrorInfo("No labels found in the expression.");
                return result;
            }
            Map<LabelName, String> labelMap = new HashMap<>();
            for (PromQLParser.LabelContext labelCtx : ctx.labelList().label()) {
                String labelName = labelCtx.labelName().getText();
                String labelValue = labelCtx.labelValue().getText();
                String labelValueTrim = labelValue.substring(1, labelValue.length() - 1);
                try {
                    labelMap.put(LabelName.valueOf(labelName), labelValueTrim);
                } catch (IllegalArgumentException e) {
                    throw new IllegalExpressionException("Label:[" + labelName + "] is illegal.");
                }
            }
            final Layer layer;
            checkLabels(labelMap, LabelName.layer);
            try {
                layer = Layer.valueOf(labelMap.get(LabelName.layer));
            } catch (IllegalArgumentException e) {
                throw new IllegalExpressionException(
                    "Layer:[" + labelMap.get(LabelName.layer) + "] is missing or illegal.");
            }
            ValueColumnMetadata.ValueColumn metaData = valueColumn.get();
            Scope scope = Scope.Finder.valueOf(metaData.getScopeId());
            Column.ValueDataType dataType = metaData.getDataType();
            MetricsRangeResult matrixResult = new MetricsRangeResult();
            matrixResult.setResultType(ParseResultType.metrics_range);
            if (StringUtil.isNotBlank(labelMap.get(LabelName.top_n))) {
                if (Column.ValueDataType.SAMPLED_RECORD == dataType) {
                    queryRecords(metricName, layer, scope, labelMap, matrixResult);
                } else {
                    queryTopN(metricName, layer, scope, labelMap, matrixResult);
                }
            } else {
                if (Column.ValueDataType.COMMON_VALUE == dataType) {
                    metricsValuesQuery(metricName, layer, scope, labelMap, matrixResult);
                } else if (Column.ValueDataType.LABELED_VALUE == dataType) {
                    labeledMetricsValuesQuery(metricName, layer, scope, labelMap, matrixResult);
                }
            }
            return matrixResult;
        } catch (IllegalExpressionException e) {
            result.setErrorType(ErrorType.bad_data);
            result.setErrorInfo(e.getMessage());
            return result;
        } catch (IOException e) {
            result.setErrorType(ErrorType.internal);
            result.setErrorInfo("Internal IO exception.");
            log.error("Query metrics error.", e);
            return result;
        }
    }

    @Override
    public ParseResult visitMetricRange(PromQLParser.MetricRangeContext ctx) {
        if (PrometheusApiHandler.QueryType.RANGE == queryType) {
            ParseResult result = new ParseResult();
            result.setErrorType(ErrorType.bad_data);
            result.setErrorInfo("Range expression should use instant query.");
            return result;
        }

        String timeRange = "PT" + ctx.DURATION().getText().toUpperCase();
        long endTS = System.currentTimeMillis();
        long startTS = endTS - java.time.Duration.parse(timeRange).toMillis();
        duration = timestamp2Duration(startTS, endTS);
        ParseResult result = visit(ctx.metricInstant());
        result.setRangeExpression(true);
        return result;
    }

    private void checkLabels(Map<LabelName, String> labelMap,
                             LabelName... labelNames) throws IllegalExpressionException {
        StringBuilder missLabels = new StringBuilder();
        for (int i = 0; i < labelNames.length; i++) {
            String labelName = labelNames[i].name();
            if (labelMap.get(labelNames[i]) == null) {
                if (i == 0) {
                    missLabels.append(labelName);
                } else {
                    missLabels.append(",").append(labelName);
                }
            }
        }
        String result = missLabels.toString();
        if (StringUtil.isNotBlank(result)) {
            throw new IllegalExpressionException("Metrics expression missing label: " + result);
        }
    }

    private void queryTopN(String metricName,
                           Layer layer,
                           Scope scope,
                           Map<LabelName, String> labelMap,
                           MetricsRangeResult matrixResult) throws IOException, IllegalExpressionException {
        TopNCondition topNCondition = buildTopNCondition(metricName, layer, scope, labelMap);
        List<SelectedRecord> selectedRecords = metricsQuery.sortMetrics(topNCondition, duration);
        for (SelectedRecord selectedRecord : selectedRecords) {
            MetricRangeData metricData = new MetricRangeData();
            MetricInfo metricInfo = buildMetricInfo(metricName, layer, scope, labelMap,
                                                    Optional.empty(),
                                                    Optional.ofNullable(selectedRecord.getName()), Optional.empty()
            );
            metricData.setMetric(metricInfo);
            metricData.setValues(buildMatrixValues(duration, selectedRecord.getValue()));
            matrixResult.getMetricDataList().add(metricData);
        }
    }

    private void queryRecords(String metricName,
                              Layer layer,
                              Scope scope,
                              Map<LabelName, String> labelMap,
                              MetricsRangeResult matrixResult) throws IOException, IllegalExpressionException {
        RecordCondition recordCondition = buildRecordCondition(metricName, layer, scope, labelMap);
        List<Record> records = recordsQuery.readRecords(recordCondition, duration);
        for (Record record : records) {
            MetricRangeData metricData = new MetricRangeData();
            MetricInfo metricInfo = buildMetricInfo(metricName, layer, scope, labelMap,
                                                    Optional.empty(), Optional.empty(),
                                                    Optional.ofNullable(record.getName())
            );
            metricData.setMetric(metricInfo);
            metricData.setValues(buildMatrixValues(duration, record.getValue()));
            matrixResult.getMetricDataList().add(metricData);
        }
    }

    private void metricsValuesQuery(String metricName,
                                    Layer layer,
                                    Scope scope,
                                    Map<LabelName, String> labelMap,
                                    MetricsRangeResult matrixResult) throws IOException, IllegalExpressionException {
        MetricsCondition metricsCondition = buildMetricsCondition(metricName, layer, scope, labelMap);
        MetricsValues metricsValues = metricsQuery.readMetricsValues(
            metricsCondition, duration);
        MetricRangeData metricData = new MetricRangeData();
        MetricInfo metricInfo = buildMetricInfo(
            metricName, layer, scope, labelMap, Optional.empty(), Optional.empty(), Optional.empty());
        metricData.setMetric(metricInfo);
        metricData.setValues(buildMatrixValues(duration, metricsValues));
        matrixResult.getMetricDataList().add(metricData);
    }

    private void labeledMetricsValuesQuery(String metricName,
                                           Layer layer,
                                           Scope scope,
                                           Map<LabelName, String> labelMap,
                                           MetricsRangeResult matrixResult) throws IOException, IllegalExpressionException {
        MetricsCondition metricsCondition = buildMetricsCondition(metricName, layer, scope, labelMap);
        Map<String, String> relabelMap = new HashMap<>();
        String queryLabels = labelMap.get(LabelName.labels);
        List<String> queryLabelList = Collections.EMPTY_LIST;
        if (StringUtil.isNotBlank(queryLabels)) {
            queryLabelList = Arrays.asList(queryLabels.split(Const.COMMA));

            String relabels = labelMap.get(LabelName.relabels);
            List<String> relabelList = Collections.EMPTY_LIST;
            if (StringUtil.isNotBlank(relabels)) {
                relabelList = Arrays.asList(relabels.split(Const.COMMA));
            }
            for (int i = 0; i < queryLabelList.size(); i++) {
                if (relabelList.size() > i) {
                    relabelMap.put(queryLabelList.get(i), relabelList.get(i));
                    continue;
                }
                relabelMap.put(queryLabelList.get(i), queryLabelList.get(i));
            }
        }
        List<MetricsValues> metricsValuesList = metricsQuery.readLabeledMetricsValues(
            metricsCondition, queryLabelList, duration);

        for (MetricsValues metricsValues : metricsValuesList) {
            MetricRangeData metricData = new MetricRangeData();
            MetricInfo metricInfo = buildMetricInfo(
                metricName, layer, scope, labelMap,
                Optional.ofNullable(relabelMap.getOrDefault(
                    metricsValues.getLabel(),
                    metricsValues.getLabel()
                )),
                Optional.empty(),
                Optional.empty()
            );
            metricData.setMetric(metricInfo);
            metricData.setValues(buildMatrixValues(duration, metricsValues));
            matrixResult.getMetricDataList().add(metricData);
        }
    }

    private ParseResult binaryOp(ParseResult left, ParseResult right, int opType) {
        if (left.getResultType() == ParseResultType.scalar && right.getResultType() == ParseResultType.scalar) {
            ScalarResult scalarLeft = (ScalarResult) left;
            ScalarResult scalarRight = (ScalarResult) right;
            double value = scalarBinaryOp(scalarLeft.getValue(), scalarRight.getValue(), opType);
            ScalarResult scalarResult = new ScalarResult();
            scalarResult.setValue(value);
            scalarResult.setResultType(ParseResultType.scalar);
            return scalarResult;
        } else if (left.getResultType() == ParseResultType.metrics_range && right.getResultType() == ParseResultType.scalar) {
            return matrixScalarBinaryOp((MetricsRangeResult) left, (ScalarResult) right, opType);
        } else if (left.getResultType() == ParseResultType.scalar && right.getResultType() == ParseResultType.metrics_range) {
            return matrixScalarBinaryOp((MetricsRangeResult) right, (ScalarResult) left, opType);
        } else if (left.getResultType() == ParseResultType.metrics_range && right.getResultType() == ParseResultType.metrics_range) {
            try {
                return matrixBinaryOp((MetricsRangeResult) left, (MetricsRangeResult) right, opType);
            } catch (IllegalExpressionException e) {
                MetricsRangeResult result = new MetricsRangeResult();
                result.setErrorType(ErrorType.bad_data);
                result.setErrorInfo(e.getMessage());
                return result;
            }
        }
        return new ParseResult();
    }

    private Entity buildEntity(Layer layer,
                               Scope scope,
                               String serviceName,
                               Map<LabelName, String> labelMap) throws IllegalExpressionException {
        Entity entity = new Entity();
        entity.setScope(scope);
        entity.setNormal(layer.isNormal());
        entity.setServiceName(serviceName);
        switch (scope) {
            case ServiceInstance:
                checkLabels(labelMap, LabelName.service_instance);
                entity.setServiceInstanceName(labelMap.get(LabelName.service_instance));
                break;
            case Endpoint:
                checkLabels(labelMap, LabelName.endpoint);
                entity.setEndpointName(labelMap.get(LabelName.endpoint));
                break;
        }
        return entity;
    }

    private TopNCondition buildTopNCondition(String metricName,
                                             Layer layer,
                                             Scope scope,
                                             Map<LabelName, String> labelMap) throws IllegalExpressionException {
        checkLabels(labelMap, LabelName.top_n, LabelName.parent_service, LabelName.order);
        TopNCondition topNCondition = new TopNCondition();
        topNCondition.setName(metricName);
        topNCondition.setParentService(labelMap.get(LabelName.parent_service));
        topNCondition.setTopN(Integer.parseInt(labelMap.get(LabelName.top_n)));
        topNCondition.setOrder(Order.valueOf(labelMap.get(LabelName.order)));
        topNCondition.setNormal(layer.isNormal());
        topNCondition.setScope(scope);
        return topNCondition;
    }

    private RecordCondition buildRecordCondition(String metricName,
                                                 Layer layer,
                                                 Scope scope,
                                                 Map<LabelName, String> labelMap) throws IllegalExpressionException {
        checkLabels(labelMap, LabelName.top_n, LabelName.parent_service, LabelName.order);
        String parentServiceName = labelMap.get(LabelName.parent_service);
        RecordCondition recordCondition = new RecordCondition();
        recordCondition.setName(metricName);
        recordCondition.setParentEntity(buildEntity(layer, scope, parentServiceName, labelMap));
        recordCondition.setTopN(Integer.parseInt(labelMap.get(LabelName.top_n)));
        recordCondition.setOrder(Order.valueOf(labelMap.get(LabelName.order)));
        return recordCondition;
    }

    private MetricsCondition buildMetricsCondition(String metricName,
                                                   Layer layer,
                                                   Scope scope,
                                                   Map<LabelName, String> labelMap) throws IllegalExpressionException {
        checkLabels(labelMap, LabelName.service);
        String serviceName = labelMap.get(LabelName.service);
        MetricsCondition metricsCondition = new MetricsCondition();
        metricsCondition.setEntity(buildEntity(layer, scope, serviceName, labelMap));
        metricsCondition.setName(metricName);
        return metricsCondition;
    }

    private MetricInfo buildMetricInfo(String metricName,
                                       Layer layer,
                                       Scope scope,
                                       Map<LabelName, String> labelMap,
                                       Optional<String> valueLabel,
                                       Optional<String> topNEntityName,
                                       Optional<String> recordName) throws IllegalExpressionException {

        MetricInfo metricInfo = new MetricInfo(metricName);
        valueLabel.ifPresent(s -> metricInfo.getLabels().add(new LabelValuePair(LabelName.label, s)));
        metricInfo.getLabels().add(new LabelValuePair(LabelName.layer, layer.name()));
        switch (scope) {
            case Service:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.scope, Scope.Service.name()));
                if (topNEntityName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.service, topNEntityName.get()));
                } else if (recordName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.record, recordName.get()));
                } else {
                    checkLabels(labelMap, LabelName.service);
                    metricInfo.getLabels()
                              .add(new LabelValuePair(LabelName.service, labelMap.get(LabelName.service)));
                }
                break;
            case ServiceInstance:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.scope, Scope.ServiceInstance.name()));
                if (topNEntityName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.service_instance, topNEntityName.get()));
                } else if (recordName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.record, recordName.get()));
                } else {
                    checkLabels(labelMap, LabelName.service_instance);
                    metricInfo.getLabels()
                              .add(new LabelValuePair(
                                  LabelName.service_instance,
                                  labelMap.get(LabelName.service_instance)
                              ));
                }
                break;
            case Endpoint:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.scope, Scope.Endpoint.name()));
                if (topNEntityName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.endpoint, topNEntityName.get()));
                } else if (recordName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.record, recordName.get()));
                } else {
                    checkLabels(labelMap, LabelName.endpoint);
                    metricInfo.getLabels()
                              .add(new LabelValuePair(LabelName.endpoint, labelMap.get(LabelName.endpoint)));
                }
                break;
        }

        return metricInfo;
    }

    private Optional<ValueColumnMetadata.ValueColumn> getValueColumn(String metricName) {
        return ValueColumnMetadata.INSTANCE.readValueColumnDefinition(metricName);
    }
}
