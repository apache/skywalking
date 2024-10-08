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

package org.apache.skywalking.oap.query.promql.rt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.RuleContext;
import org.apache.skywalking.oap.query.promql.entity.ErrorType;
import org.apache.skywalking.oap.query.promql.entity.LabelName;
import org.apache.skywalking.oap.query.promql.entity.LabelValuePair;
import org.apache.skywalking.oap.query.promql.entity.MetricRangeData;
import org.apache.skywalking.oap.query.promql.entity.MetricInfo;
import org.apache.skywalking.oap.query.promql.handler.PromQLApiHandler;
import org.apache.skywalking.oap.query.promql.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.query.promql.rt.result.MetricsRangeResult;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResult;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResultType;
import org.apache.skywalking.oap.query.promql.rt.result.ScalarResult;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataLabel;
import org.apache.skywalking.oap.server.core.query.AggregationQueryService;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.MetricsQueryService;
import org.apache.skywalking.oap.server.core.query.RecordQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.promql.rt.grammar.PromQLParser;
import org.apache.skywalking.promql.rt.grammar.PromQLParserBaseVisitor;

import static org.apache.skywalking.oap.query.promql.rt.PromOpUtils.buildMatrixValues;
import static org.apache.skywalking.oap.query.promql.rt.PromOpUtils.formatDuration;
import static org.apache.skywalking.oap.query.promql.rt.PromOpUtils.matrixBinaryOp;
import static org.apache.skywalking.oap.query.promql.rt.PromOpUtils.matrixCompareOp;
import static org.apache.skywalking.oap.query.promql.rt.PromOpUtils.matrixScalarBinaryOp;
import static org.apache.skywalking.oap.query.promql.rt.PromOpUtils.matrixScalarCompareOp;
import static org.apache.skywalking.oap.query.promql.rt.PromOpUtils.scalarBinaryOp;
import static org.apache.skywalking.oap.query.promql.rt.PromOpUtils.scalarCompareOp;
import static org.apache.skywalking.oap.server.core.analysis.metrics.DataLabel.GENERAL_LABEL_NAME;

@Slf4j
public class PromQLExprQueryVisitor extends PromQLParserBaseVisitor<ParseResult> {
    private final PromQLApiHandler.QueryType queryType;
    private final MetricsQueryService metricsQueryService;
    private final RecordQueryService recordQueryService;
    private final AggregationQueryService aggregationQueryService;
    private Duration duration;

    public PromQLExprQueryVisitor(final MetricsQueryService metricsQueryService,
                                  final RecordQueryService recordQueryService,
                                  final AggregationQueryService aggregationQueryService,
                                  final Duration duration,
                                  final PromQLApiHandler.QueryType queryType) {
        this.metricsQueryService = metricsQueryService;
        this.recordQueryService = recordQueryService;
        this.aggregationQueryService = aggregationQueryService;
        this.duration = duration;
        this.queryType = queryType;
    }

    @Override
    public ParseResult visitParensOp(PromQLParser.ParensOpContext ctx) {
        return visit(ctx.expression());
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

    @Override
    public ParseResult visitAggregationOp(final PromQLParser.AggregationOpContext ctx) {
        ParseResult parseResult = visit(ctx.expression());
        if (StringUtil.isNotBlank(parseResult.getErrorInfo())) {
            return parseResult;
        }

        if (!parseResult.getResultType().equals(ParseResultType.METRICS_RANGE)) {
            ParseResult result = new ParseResult();
            result.setErrorType(ErrorType.BAD_DATA);
            result.setErrorInfo("Expected type instant vector in aggregation expression.");
            return result;
        }

        MetricsRangeResult metricsRangeResult = (MetricsRangeResult) parseResult;
        if (CollectionUtils.isEmpty(metricsRangeResult.getMetricDataList())) {
            return metricsRangeResult;
        }

        List<String> resultLabelNames = metricsRangeResult.getMetricDataList()
                                                          .get(0).getMetric().getLabels()
                                                          .stream().map(LabelValuePair::getLabelName)
                                                          .collect(Collectors.toList());

        List<String> groupingBy = new ArrayList<>();
        PromQLParser.AggregationClauseContext clauseContext = ctx.aggregationClause();
        if (clauseContext != null) {
            List<String> clauseGroupingBy = clauseContext.labelNameList().labelName().stream()
                                                         .map(RuleContext::getText)
                                                         .filter(resultLabelNames::contains)
                                                         .collect(Collectors.toList());
            if (clauseContext.getStart().getType() == PromQLParser.WITHOUT) {
                groupingBy = resultLabelNames.stream()
                                             .filter(labelName -> !clauseGroupingBy.contains(labelName))
                                             .collect(Collectors.toList());
            } else {
                groupingBy = clauseGroupingBy;
            }
        }

        return PromOpUtils.matrixAggregateOp(
            (MetricsRangeResult) parseResult, ctx.aggregationFunc().getStart().getType(), groupingBy
        );
    }

    private ParseResult compareOp(ParseResult left, ParseResult right, int opType, boolean boolModifier) {
        try {
            if (left.getResultType() == ParseResultType.SCALAR && right.getResultType() == ParseResultType.SCALAR) {
                if (!boolModifier) {
                    throw new IllegalExpressionException("Comparisons between scalars must use BOOL modifier.");
                } else {
                    ScalarResult scalarLeft = (ScalarResult) left;
                    ScalarResult scalarRight = (ScalarResult) right;
                    int value = scalarCompareOp(scalarLeft.getValue(), scalarRight.getValue(), opType);
                    ScalarResult scalarResult = new ScalarResult();
                    scalarResult.setValue(value);
                    scalarResult.setResultType(ParseResultType.SCALAR);
                    return scalarResult;
                }
            } else if (left.getResultType() == ParseResultType.METRICS_RANGE && right.getResultType() == ParseResultType.SCALAR) {
                return matrixScalarCompareOp((MetricsRangeResult) left, (ScalarResult) right, opType);
            } else if (left.getResultType() == ParseResultType.SCALAR && right.getResultType() == ParseResultType.METRICS_RANGE) {
                return matrixScalarCompareOp((MetricsRangeResult) right, (ScalarResult) left, opType);
            } else if (left.getResultType() == ParseResultType.METRICS_RANGE && right.getResultType() == ParseResultType.METRICS_RANGE) {
                try {
                    return matrixCompareOp((MetricsRangeResult) left, (MetricsRangeResult) right, opType);
                } catch (IllegalExpressionException e) {
                    MetricsRangeResult result = new MetricsRangeResult();
                    result.setErrorType(ErrorType.BAD_DATA);
                    result.setErrorInfo(e.getMessage());
                    return result;
                }
            }
        } catch (IllegalExpressionException e) {
            MetricsRangeResult result = new MetricsRangeResult();
            result.setErrorType(ErrorType.BAD_DATA);
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
        result.setResultType(ParseResultType.SCALAR);
        return result;
    }

    @Override
    public ParseResult visitMetricInstant(PromQLParser.MetricInstantContext ctx) {
        ParseResult result = new ParseResult();
        try {
            String metricName = ctx.metricName().getText();
            Optional<ValueColumnMetadata.ValueColumn> valueColumn = getValueColumn(metricName);
            if (valueColumn.isEmpty()) {
                result.setErrorType(ErrorType.BAD_DATA);
                result.setErrorInfo("Metric: [" + metricName + "] does not exist.");
                return result;
            }
            if (ctx.labelList() == null) {
                result.setErrorType(ErrorType.BAD_DATA);
                result.setErrorInfo("No labels found in the expression.");
                return result;
            }
            Map<LabelName, String> generalLabelMap = new HashMap<>();
            Map<String, String> queryLabel = new HashMap<>();
            for (PromQLParser.LabelContext labelCtx : ctx.labelList().label()) {
                String labelName = labelCtx.labelName().getText();
                String labelValue = labelCtx.labelValue().getText();
                String labelValueTrim = labelValue.substring(1, labelValue.length() - 1);
                try {
                    if (LabelName.isLabelName(labelName)) {
                        generalLabelMap.put(LabelName.labelOf(labelName), labelValueTrim);
                    } else {
                        queryLabel.put(labelName, labelValueTrim);
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalExpressionException("Label:[" + labelName + "] is illegal.");
                }
            }
            final Layer layer;
            checkLabels(generalLabelMap, LabelName.LAYER);
            try {
                layer = Layer.valueOf(generalLabelMap.get(LabelName.LAYER));
            } catch (IllegalArgumentException e) {
                throw new IllegalExpressionException(
                    "Layer:[" + generalLabelMap.get(LabelName.LAYER) + "] is missing or illegal.");
            }
            ValueColumnMetadata.ValueColumn metaData = valueColumn.get();
            Scope scope = Scope.Finder.valueOf(metaData.getScopeId());
            Column.ValueDataType dataType = metaData.getDataType();
            MetricsRangeResult matrixResult = new MetricsRangeResult();
            matrixResult.setResultType(ParseResultType.METRICS_RANGE);
            if (StringUtil.isNotBlank(generalLabelMap.get(LabelName.TOP_N))) {
                if (Column.ValueDataType.SAMPLED_RECORD == dataType) {
                    queryRecords(metricName, layer, scope, generalLabelMap, matrixResult);
                } else {
                    queryTopN(metricName, layer, scope, generalLabelMap, matrixResult);
                }
            } else {
                if (Column.ValueDataType.COMMON_VALUE == dataType) {
                    metricsValuesQuery(metricName, layer, scope, generalLabelMap, matrixResult);
                } else if (Column.ValueDataType.LABELED_VALUE == dataType) {
                    //compatible with old version query, if true support use `labels` as the query label.
                    boolean isMultiIntValues = valueColumn.get().isMultiIntValues();
                    labeledMetricsValuesQuery(metricName, layer, scope, generalLabelMap, queryLabel, matrixResult, isMultiIntValues);
                }
            }
            return matrixResult;
        } catch (IllegalExpressionException e) {
            result.setErrorType(ErrorType.BAD_DATA);
            result.setErrorInfo(e.getMessage());
            return result;
        } catch (IOException e) {
            result.setErrorType(ErrorType.INTERNAL);
            result.setErrorInfo("Internal IO exception.");
            log.error("Query metrics error.", e);
            return result;
        }
    }

    @Override
    public ParseResult visitMetricRange(PromQLParser.MetricRangeContext ctx) {
        if (PromQLApiHandler.QueryType.RANGE == queryType) {
            ParseResult result = new ParseResult();
            result.setErrorType(ErrorType.BAD_DATA);
            result.setErrorInfo("Range expression should use instant query.");
            return result;
        }

        String timeRange = ctx.DURATION().getText().toUpperCase();
        long endTS = this.duration.getEndTimestamp();
        long startTS = endTS - formatDuration(timeRange).getMillis();
        duration = DurationUtils.timestamp2Duration(startTS, endTS);
        ParseResult result = visit(ctx.metricInstant());
        result.setRangeExpression(true);
        return result;
    }

    private void checkLabels(Map<LabelName, String> labelMap,
                             LabelName... labelNames) throws IllegalExpressionException {
        StringBuilder missLabels = new StringBuilder();
        int j = 0;
        for (final LabelName name : labelNames) {
            String labelName = name.toString();
            if (labelMap.get(name) == null) {
                missLabels.append(j++ > 0 ? "," : "").append(labelName);
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
        List<SelectedRecord> selectedRecords = aggregationQueryService.sortMetrics(topNCondition, duration);
        for (SelectedRecord selectedRecord : selectedRecords) {
            MetricRangeData metricData = new MetricRangeData();
            MetricInfo metricInfo = buildMetricInfo(metricName, layer, scope, labelMap,
                                                    Optional.empty(),
                                                    Optional.ofNullable(selectedRecord.getName()), Optional.empty(), false
            );
            metricData.setMetric(metricInfo);
            metricData.setValues(buildMatrixValues(duration, String.valueOf(selectedRecord.getValue())));
            matrixResult.getMetricDataList().add(metricData);
        }
    }

    private void queryRecords(String metricName,
                              Layer layer,
                              Scope scope,
                              Map<LabelName, String> labelMap,
                              MetricsRangeResult matrixResult) throws IOException, IllegalExpressionException {
        RecordCondition recordCondition = buildRecordCondition(metricName, layer, scope, labelMap);
        List<Record> records = recordQueryService.readRecords(recordCondition, duration);
        for (Record record : records) {
            MetricRangeData metricData = new MetricRangeData();
            MetricInfo metricInfo = buildMetricInfo(metricName, layer, scope, labelMap,
                                                    Optional.empty(), Optional.empty(),
                                                    Optional.ofNullable(record.getName()), false
            );
            metricData.setMetric(metricInfo);
            metricData.setValues(buildMatrixValues(duration, String.valueOf(record.getValue())));
            matrixResult.getMetricDataList().add(metricData);
        }
    }

    private void metricsValuesQuery(String metricName,
                                    Layer layer,
                                    Scope scope,
                                    Map<LabelName, String> labelMap,
                                    MetricsRangeResult matrixResult) throws IOException, IllegalExpressionException {
        MetricsCondition metricsCondition = buildMetricsCondition(metricName, layer, scope, labelMap);
        MetricsValues metricsValues = metricsQueryService.readMetricsValues(
            metricsCondition, duration);
        MetricRangeData metricData = new MetricRangeData();
        MetricInfo metricInfo = buildMetricInfo(
            metricName, layer, scope, labelMap, Optional.empty(), Optional.empty(), Optional.empty(), false);
        metricData.setMetric(metricInfo);
        metricData.setValues(buildMatrixValues(duration, metricsValues));
        matrixResult.getMetricDataList().add(metricData);
    }

    private void labeledMetricsValuesQuery(String metricName,
                                           Layer layer,
                                           Scope scope,
                                           Map<LabelName, String> sysLabelMap,
                                           Map<String, String> queryLabel,
                                           MetricsRangeResult matrixResult,
                                           boolean isMultiIntValues) throws IOException, IllegalExpressionException {
        MetricsCondition metricsCondition = buildMetricsCondition(metricName, layer, scope, sysLabelMap);
        Map<String, String> relabelMap = new HashMap<>();

        List<KeyValue> queryLabelList = new ArrayList<>();
        if (isMultiIntValues) {
            // compatible with old version query.
            queryLabelList.add(new KeyValue(GENERAL_LABEL_NAME, sysLabelMap.get(LabelName.LABELS)));
            String relabels = sysLabelMap.get(LabelName.RELABELS);
            List<String> relabelList = Collections.emptyList();
            if (StringUtil.isNotBlank(relabels)) {
                relabelList = Arrays.asList(relabels.split(Const.COMMA));
            }
            List<String> labelValues = Arrays.asList(sysLabelMap.get(LabelName.LABELS).split(Const.COMMA));
            for (int i = 0; i < labelValues.size(); i++) {
                if (relabelList.size() > i) {
                    relabelMap.put(labelValues.get(i), relabelList.get(i));
                }
            }
        } else {
            for (Map.Entry<String, String> entry : queryLabel.entrySet()) {
                queryLabelList.add(new KeyValue(entry.getKey(), entry.getValue()));
            }
            // as the percentile metric values changed from `0,1,2,3,4` to `50,75,90,95,99`,
            // we don't need to relabel it for now.
            // we should implement `label_replace()` function in the future.
        }

        List<MetricsValues> metricsValuesList = metricsQueryService.readLabeledMetricsValues(
            metricsCondition, queryLabelList, duration);

        for (MetricsValues metricsValues : metricsValuesList) {
            MetricRangeData metricData = new MetricRangeData();
            MetricInfo metricInfo = buildMetricInfo(
                metricName, layer, scope, sysLabelMap,
                Optional.ofNullable(relabelMap.getOrDefault(
                    metricsValues.getLabel(),
                    metricsValues.getLabel()
                )),
                Optional.empty(),
                Optional.empty(),
                isMultiIntValues
            );
            metricData.setMetric(metricInfo);
            metricData.setValues(buildMatrixValues(duration, metricsValues));
            matrixResult.getMetricDataList().add(metricData);
        }
    }

    private ParseResult binaryOp(ParseResult left, ParseResult right, int opType) {
        if (left.getResultType() == ParseResultType.SCALAR && right.getResultType() == ParseResultType.SCALAR) {
            ScalarResult scalarLeft = (ScalarResult) left;
            ScalarResult scalarRight = (ScalarResult) right;
            double value = scalarBinaryOp(scalarLeft.getValue(), scalarRight.getValue(), opType);
            ScalarResult scalarResult = new ScalarResult();
            scalarResult.setValue(value);
            scalarResult.setResultType(ParseResultType.SCALAR);
            return scalarResult;
        } else if (left.getResultType() == ParseResultType.METRICS_RANGE && right.getResultType() == ParseResultType.SCALAR) {
            return matrixScalarBinaryOp((MetricsRangeResult) left, (ScalarResult) right, opType);
        } else if (left.getResultType() == ParseResultType.SCALAR && right.getResultType() == ParseResultType.METRICS_RANGE) {
            return matrixScalarBinaryOp((MetricsRangeResult) right, (ScalarResult) left, opType);
        } else if (left.getResultType() == ParseResultType.METRICS_RANGE && right.getResultType() == ParseResultType.METRICS_RANGE) {
            try {
                return matrixBinaryOp((MetricsRangeResult) left, (MetricsRangeResult) right, opType);
            } catch (IllegalExpressionException e) {
                MetricsRangeResult result = new MetricsRangeResult();
                result.setErrorType(ErrorType.BAD_DATA);
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
                checkLabels(labelMap, LabelName.SERVICE_INSTANCE);
                entity.setServiceInstanceName(labelMap.get(LabelName.SERVICE_INSTANCE));
                break;
            case Endpoint:
                checkLabels(labelMap, LabelName.ENDPOINT);
                entity.setEndpointName(labelMap.get(LabelName.ENDPOINT));
                break;
            case ServiceRelation:
                checkLabels(labelMap, LabelName.DEST_SERVICE, LabelName.DEST_LAYER);
                entity.setDestServiceName(labelMap.get(LabelName.DEST_SERVICE));
                entity.setDestNormal(Layer.nameOf(labelMap.get(LabelName.DEST_LAYER)).isNormal());
                break;
            case ServiceInstanceRelation:
                checkLabels(labelMap, LabelName.DEST_SERVICE, LabelName.SERVICE_INSTANCE, LabelName.DEST_SERVICE_INSTANCE, LabelName.DEST_LAYER);
                entity.setDestServiceName(labelMap.get(LabelName.DEST_SERVICE));
                entity.setServiceInstanceName(labelMap.get(LabelName.SERVICE_INSTANCE));
                entity.setDestServiceInstanceName(labelMap.get(LabelName.DEST_SERVICE_INSTANCE));
                entity.setDestNormal(Layer.nameOf(labelMap.get(LabelName.DEST_LAYER)).isNormal());
                break;
            case EndpointRelation:
                checkLabels(labelMap, LabelName.DEST_SERVICE, LabelName.ENDPOINT, LabelName.DEST_ENDPOINT, LabelName.DEST_LAYER);
                entity.setDestServiceName(labelMap.get(LabelName.DEST_SERVICE));
                entity.setEndpointName(labelMap.get(LabelName.ENDPOINT));
                entity.setDestEndpointName(labelMap.get(LabelName.DEST_ENDPOINT));
                entity.setDestNormal(Layer.nameOf(labelMap.get(LabelName.DEST_LAYER)).isNormal());
                break;
        }
        return entity;
    }

    private TopNCondition buildTopNCondition(String metricName,
                                             Layer layer,
                                             Scope scope,
                                             Map<LabelName, String> labelMap) throws IllegalExpressionException {
        //sortMetrics query ParentService could be null.
        checkLabels(labelMap, LabelName.TOP_N, LabelName.ORDER);
        TopNCondition topNCondition = new TopNCondition();
        topNCondition.setName(metricName);
        topNCondition.setParentService(labelMap.get(LabelName.PARENT_SERVICE));
        topNCondition.setTopN(Integer.parseInt(labelMap.get(LabelName.TOP_N)));
        topNCondition.setOrder(Order.valueOf(labelMap.get(LabelName.ORDER)));
        topNCondition.setNormal(layer.isNormal());
        topNCondition.setScope(scope);
        return topNCondition;
    }

    private RecordCondition buildRecordCondition(String metricName,
                                                 Layer layer,
                                                 Scope scope,
                                                 Map<LabelName, String> labelMap) throws IllegalExpressionException {
        checkLabels(labelMap, LabelName.TOP_N, LabelName.PARENT_SERVICE, LabelName.ORDER);
        String parentServiceName = labelMap.get(LabelName.PARENT_SERVICE);
        RecordCondition recordCondition = new RecordCondition();
        recordCondition.setName(metricName);
        recordCondition.setParentEntity(buildEntity(layer, scope, parentServiceName, labelMap));
        recordCondition.setTopN(Integer.parseInt(labelMap.get(LabelName.TOP_N)));
        recordCondition.setOrder(Order.valueOf(labelMap.get(LabelName.ORDER)));
        return recordCondition;
    }

    private MetricsCondition buildMetricsCondition(String metricName,
                                                   Layer layer,
                                                   Scope scope,
                                                   Map<LabelName, String> labelMap) throws IllegalExpressionException {
        checkLabels(labelMap, LabelName.SERVICE);
        String serviceName = labelMap.get(LabelName.SERVICE);
        MetricsCondition metricsCondition = new MetricsCondition();
        metricsCondition.setEntity(buildEntity(layer, scope, serviceName, labelMap));
        metricsCondition.setName(metricName);
        return metricsCondition;
    }

    private MetricInfo buildMetricInfo(String metricName,
                                       Layer layer,
                                       Scope scope,
                                       Map<LabelName, String> sysLabelMap,
                                       Optional<String> valueLabel,
                                       Optional<String> topNEntityName,
                                       Optional<String> recordName,
                                       boolean isMultiIntValues) throws IllegalExpressionException {

        MetricInfo metricInfo = new MetricInfo(metricName);
        valueLabel.ifPresent(s ->
                             {
                                 if (isMultiIntValues) {
                                     metricInfo.getLabels().add(new LabelValuePair(LabelName.LABELS.getLabel(), s));
                                 } else {
                                     DataLabel dataLabel = new DataLabel();
                                     dataLabel.put(s);
                                     for (Map.Entry<String, String> label : dataLabel.entrySet()) {
                                         metricInfo.getLabels().add(new LabelValuePair(label.getKey(), label.getValue()));
                                     }
                                 }
                             });
        metricInfo.getLabels().add(new LabelValuePair(LabelName.LAYER.getLabel(), layer.name()));
        switch (scope) {
            case Service:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE.getLabel(), Scope.Service.name()));
                if (topNEntityName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.SERVICE.getLabel(), topNEntityName.get()));
                } else if (recordName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.RECORD.getLabel(), recordName.get()));
                } else {
                    checkLabels(sysLabelMap, LabelName.SERVICE);
                    metricInfo.getLabels()
                              .add(new LabelValuePair(LabelName.SERVICE.getLabel(), sysLabelMap.get(LabelName.SERVICE)));
                }
                break;
            case ServiceInstance:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE.getLabel(), Scope.ServiceInstance.name()));
                if (topNEntityName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.SERVICE_INSTANCE.getLabel(), topNEntityName.get()));
                } else if (recordName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.RECORD.getLabel(), recordName.get()));
                } else {
                    checkLabels(sysLabelMap, LabelName.SERVICE, LabelName.SERVICE_INSTANCE);
                    metricInfo.getLabels()
                              .add(new LabelValuePair(LabelName.SERVICE.getLabel(), sysLabelMap.get(LabelName.SERVICE)));
                    metricInfo.getLabels()
                              .add(new LabelValuePair(
                                  LabelName.SERVICE_INSTANCE.getLabel(),
                                  sysLabelMap.get(LabelName.SERVICE_INSTANCE)
                              ));
                }
                break;
            case Endpoint:
                metricInfo.getLabels().add(new LabelValuePair(LabelName.SCOPE.getLabel(), Scope.Endpoint.name()));
                if (topNEntityName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.ENDPOINT.getLabel(), topNEntityName.get()));
                } else if (recordName.isPresent()) {
                    metricInfo.getLabels().add(new LabelValuePair(LabelName.RECORD.getLabel(), recordName.get()));
                } else {
                    checkLabels(sysLabelMap, LabelName.SERVICE, LabelName.ENDPOINT);
                    metricInfo.getLabels()
                              .add(new LabelValuePair(LabelName.SERVICE.getLabel(), sysLabelMap.get(LabelName.SERVICE)));
                    metricInfo.getLabels()
                              .add(new LabelValuePair(LabelName.ENDPOINT.getLabel(), sysLabelMap.get(LabelName.ENDPOINT)));
                }
                break;
        }

        return metricInfo;
    }

    private Optional<ValueColumnMetadata.ValueColumn> getValueColumn(String metricName) {
        return ValueColumnMetadata.INSTANCE.readValueColumnDefinition(metricName);
    }
}
