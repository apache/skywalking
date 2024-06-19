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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.type.MQEValue;
import org.apache.skywalking.mqe.rt.type.MQEValues;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataLabel;
import org.apache.skywalking.oap.server.core.query.AggregationQueryService;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.MetricsQueryService;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.RecordQueryService;
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
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.mqe.rt.MQEVisitorBase;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.ExpressionResultType;
import org.joda.time.DateTime;

import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTrace.TRACE_CONTEXT;

@Slf4j
public class MQEVisitor extends MQEVisitorBase {
    private final Entity entity;
    private final Duration duration;
    private final ModuleManager moduleManager;
    private MetricsQueryService metricsQueryService;
    private AggregationQueryService aggregationQueryService;
    private RecordQueryService recordQueryService;

    public MQEVisitor(final ModuleManager moduleManager,
                      final Entity entity,
                      final Duration duration) {
        super(duration.getStep());
        this.moduleManager = moduleManager;
        this.entity = entity;
        this.duration = duration;
    }

    private MetricsQueryService getMetricsQueryService() {
        if (metricsQueryService == null) {
            this.metricsQueryService = moduleManager.find(CoreModule.NAME)
                                                    .provider()
                                                    .getService(MetricsQueryService.class);
        }
        return metricsQueryService;
    }

    private AggregationQueryService getAggregationQueryService() {
        if (aggregationQueryService == null) {
            this.aggregationQueryService = moduleManager.find(CoreModule.NAME)
                                                        .provider()
                                                        .getService(AggregationQueryService.class);
        }
        return aggregationQueryService;
    }

    private RecordQueryService getRecordQueryService() {
        if (recordQueryService == null) {
            this.recordQueryService = moduleManager.find(CoreModule.NAME)
                                                  .provider()
                                                  .getService(RecordQueryService.class);
        }
        return recordQueryService;
    }

    @Override
    public ExpressionResult visitMetric(MQEParser.MetricContext ctx) {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = traceContext.createSpan("MQE Metric OP: " + ctx.getText());
        try {
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
                        int topN = Integer.parseInt(parent.INTEGER().getText());
                        if (topN <= 0) {
                            throw new IllegalExpressionException("TopN value must be > 0.");
                        }
                        querySortMetrics(metricName, Integer.parseInt(parent.INTEGER().getText()),
                                         Order.valueOf(parent.order().getText().toUpperCase()), result);
                    } else if (ctx.parent instanceof MQEParser.TrendOPContext) {
                        //trend query requires get previous data according to the trend range
                        MQEParser.TrendOPContext parent = (MQEParser.TrendOPContext) ctx.parent;
                        int trendRange = Integer.parseInt(parent.INTEGER().getText());
                        queryMetrics(metricName, getTrendQueryDuration(trendRange), result);
                    } else {
                        queryMetrics(metricName, this.duration, result);
                    }
                } else if (Column.ValueDataType.LABELED_VALUE == dataType) {
                    if (ctx.parent instanceof MQEParser.TopNOPContext) {
                        throw new IllegalExpressionException(
                            "Metric: [" + metricName + "] is labeled value, dose not support top_n query.");
                    }
                    List<KeyValue> queryLabels = super.buildLabels(ctx.labelList());
                    if (ctx.parent instanceof MQEParser.TrendOPContext) {
                        MQEParser.TrendOPContext parent = (MQEParser.TrendOPContext) ctx.parent;
                        int trendRange = Integer.parseInt(parent.INTEGER().getText());
                        queryLabeledMetrics(metricName, queryLabels, getTrendQueryDuration(trendRange), result);
                    } else {
                        queryLabeledMetrics(metricName, queryLabels, this.duration, result);
                    }
                } else if (Column.ValueDataType.SAMPLED_RECORD == dataType) {
                    if (ctx.parent instanceof MQEParser.TopNOPContext) {
                        MQEParser.TopNOPContext parent = (MQEParser.TopNOPContext) ctx.parent;
                        int topN = Integer.parseInt(parent.INTEGER().getText());
                        if (topN <= 0) {
                            throw new IllegalExpressionException("TopN value must be > 0.");
                        }
                        queryRecords(metricName, Integer.parseInt(parent.INTEGER().getText()),
                                     Order.valueOf(parent.order().getText().toUpperCase()), result);
                    } else {
                        throw new IllegalExpressionException(
                            "Metric: [" + metricName + "] is topN record, need top_n function for query.");
                    }
                }
            } catch (IllegalExpressionException e) {
                return getErrorResult(e.getMessage());
            } catch (IOException e) {
                ExpressionResult errorResult = getErrorResult("Internal IO exception, query metrics error.");
                log.error("Query metrics from backend error.", e);
                return errorResult;
            }
            return result;
        } finally {
            traceContext.stopSpan(span);
        }
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
        List<SelectedRecord> selectedRecords = getAggregationQueryService().sortMetrics(topNCondition, duration);

        List<MQEValue> mqeValueList = new ArrayList<>(selectedRecords.size());
        selectedRecords.forEach(selectedRecord -> {
            MQEValue mqeValue = new MQEValue();
            mqeValue.setId(selectedRecord.getName());
            mqeValue.setEmptyValue(false);
            mqeValue.setDoubleValue(Double.parseDouble(selectedRecord.getValue()));
            mqeValueList.add(mqeValue);
        });
        MQEValues mqeValues = new MQEValues();
        mqeValues.setValues(mqeValueList);
        result.getResults().add(mqeValues);
        result.setType(ExpressionResultType.SORTED_LIST);
    }

    private void queryRecords(String metricName, int topN, Order order, ExpressionResult result) throws IOException {
        RecordCondition recordCondition = new RecordCondition();
        recordCondition.setName(metricName);
        recordCondition.setTopN(topN);
        recordCondition.setParentEntity(entity);
        recordCondition.setOrder(order);
        List<Record> records = getRecordQueryService().readRecords(recordCondition, duration);

        List<MQEValue> mqeValueList = new ArrayList<>(records.size());
        records.forEach(record -> {
            MQEValue mqeValue = new MQEValue();
            mqeValue.setId(record.getName());
            mqeValue.setEmptyValue(false);
            mqeValue.setDoubleValue(Double.parseDouble(record.getValue()));
            mqeValue.setTraceID(record.getRefId());
            mqeValueList.add(mqeValue);
        });
        MQEValues mqeValues = new MQEValues();
        mqeValues.setValues(mqeValueList);
        result.getResults().add(mqeValues);
        result.setType(ExpressionResultType.RECORD_LIST);
    }

    private void queryMetrics(String metricName, Duration queryDuration, ExpressionResult result) throws IOException {
        MetricsCondition metricsCondition = new MetricsCondition();
        metricsCondition.setName(metricName);
        metricsCondition.setEntity(entity);
        MetricsValues metricsValues = getMetricsQueryService().readMetricsValues(metricsCondition, queryDuration);
        List<PointOfTime> times = queryDuration.assembleDurationPoints();
        if (metricsValues.getValues().getValues().size() != times.size()) {
            log.warn("Metric: {} values size is not equal to duration points size, metrics values size: {}, duration points size: {}",
                     metricName, metricsValues.getValues().getValues().size(), times.size());
            return;
        }
        List<MQEValue> mqeValueList = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            long retTimestamp = DurationUtils.INSTANCE.parseToDateTime(queryDuration.getStep(), times.get(i).getPoint())
                                                      .getMillis();
            KVInt kvInt = metricsValues.getValues().getValues().get(i);
            MQEValue mqeValue = new MQEValue();
            mqeValue.setId(Long.toString(retTimestamp));
            mqeValue.setEmptyValue(kvInt.isEmptyValue());
            mqeValue.setDoubleValue(kvInt.getValue());
            mqeValueList.add(mqeValue);
        }
        MQEValues mqeValues = new MQEValues();
        mqeValues.setValues(mqeValueList);
        result.getResults().add(mqeValues);
        result.setType(ExpressionResultType.TIME_SERIES_VALUES);
    }

    private void queryLabeledMetrics(String metricName,
                                     List<KeyValue> queryLabels,
                                     Duration queryDuration,
                                     ExpressionResult result) throws IOException {
        MetricsCondition metricsCondition = new MetricsCondition();
        metricsCondition.setName(metricName);
        metricsCondition.setEntity(entity);
        List<MetricsValues> metricsValuesList = getMetricsQueryService().readLabeledMetricsValues(
            metricsCondition, queryLabels, queryDuration);
        List<PointOfTime> times = queryDuration.assembleDurationPoints();
        metricsValuesList.forEach(metricsValues -> {
            if (metricsValues.getValues().getValues().size() != times.size()) {
                log.warn("Metric: {} values size is not equal to duration points size, metrics values size: {}, duration points size: {}",
                         metricName, metricsValues.getValues().getValues().size(), times.size());
                return;
            }
            List<MQEValue> mqeValueList = new ArrayList<>(times.size());
            for (int i = 0; i < times.size(); i++) {
                long retTimestamp = DurationUtils.INSTANCE.parseToDateTime(queryDuration.getStep(), times.get(i).getPoint())
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

            MQEValues mqeValues = new MQEValues();
            DataLabel dataLabel = new DataLabel();
            dataLabel.put(metricsValues.getLabel());
            for (Map.Entry<String, String> label : dataLabel.entrySet()) {
                mqeValues.getMetric().getLabels().add(new KeyValue(label.getKey(), label.getValue()));
            }
            //Sort labels by key in natural order by default
            mqeValues.getMetric().sortLabelsByKey(Comparator.naturalOrder());
            mqeValues.setValues(mqeValueList);
            result.getResults().add(mqeValues);
        });
        result.setType(ExpressionResultType.TIME_SERIES_VALUES);
        result.setLabeledResult(true);
    }

    private Duration getTrendQueryDuration(int stepRange) {
        Duration duration = new Duration();
        duration.setStep(this.duration.getStep());
        duration.setEnd(this.duration.getEnd());
        DateTime startDT = new DateTime(this.duration.getStartTimestamp());

        switch (duration.getStep()) {
            case DAY:
                duration.setStart(startDT.minusDays(stepRange).toString(DurationUtils.YYYY_MM_DD));
                break;
            case HOUR:
                duration.setStart(startDT.minusHours(stepRange).toString(DurationUtils.YYYY_MM_DD_HH));
                break;
            case MINUTE:
                duration.setStart(startDT.minusMinutes(stepRange).toString(DurationUtils.YYYY_MM_DD_HHMM));
                break;
            case SECOND:
                duration.setStart(startDT.minusSeconds(stepRange).toString(DurationUtils.YYYY_MM_DD_HHMMSS));
                break;
            default:
                throw new IllegalArgumentException("Unsupported query step: " + duration.getStep());
        }
        return duration;
    }
}
