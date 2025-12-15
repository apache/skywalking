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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.AggregateLabelsFunc;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.AggregateLabelsFuncFactory;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.AvgAggregateLabelsFunc;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.MaxAggregateLabelsFunc;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.MinAggregateLabelsFunc;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.SumAggregateLabelsFunc;
import org.apache.skywalking.oap.query.promql.entity.LabelValuePair;
import org.apache.skywalking.oap.query.promql.entity.MetricInfo;
import org.apache.skywalking.oap.query.promql.entity.MetricRangeData;
import org.apache.skywalking.oap.query.promql.entity.TimeValuePair;
import org.apache.skywalking.oap.query.promql.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.query.promql.rt.result.MetricsRangeResult;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResultType;
import org.apache.skywalking.oap.query.promql.rt.result.ScalarResult;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.promql.rt.grammar.PromQLParser;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class PromOpUtils {

    static MetricsRangeResult matrixScalarBinaryOp(MetricsRangeResult matrix, Function<Double, Double> rangeValueOp) {
        MetricsRangeResult result = new MetricsRangeResult();
        result.setResultType(ParseResultType.METRICS_RANGE);
        result.setRangeExpression(matrix.isRangeExpression());
        matrix.getMetricDataList().forEach(metricData -> {
            MetricRangeData newData = new MetricRangeData();
            result.getMetricDataList().add(newData);
            newData.setMetric(metricData.getMetric());
            List<TimeValuePair> newValues = metricData.getValues().stream().map(value -> {
                double v = Double.parseDouble(value.getValue());
                return new TimeValuePair(
                    value.getTime(), formatDoubleValue(rangeValueOp.apply(v))
                );

            }).collect(Collectors.toList());
            newData.setValues(newValues);
        });
        return result;
    }

    static MetricsRangeResult matrixBinaryOp(MetricsRangeResult matrixLeft,
                                             MetricsRangeResult matrixRight,
                                             int opType) throws IllegalExpressionException {
        MetricsRangeResult result = new MetricsRangeResult();
        result.setResultType(ParseResultType.METRICS_RANGE);
        result.setRangeExpression(matrixLeft.isRangeExpression());
        for (int i = 0; i < matrixLeft.getMetricDataList().size(); i++) {
            MetricRangeData dataLeft = matrixLeft.getMetricDataList().get(i);
            MetricRangeData dataRight = matrixRight.getMetricDataList().get(i);
            if (!dataLeft.getMetric().getLabels().equals(dataRight.getMetric().getLabels())) {
                throw new IllegalExpressionException(
                    "The metric info result left in conformity with right.");
            }
            if (dataLeft.getValues().size() != dataRight.getValues().size()) {
                throw new IllegalExpressionException(
                    "The metric value range left in conformity with right.");
            }
            MetricRangeData newData = new MetricRangeData();
            result.getMetricDataList().add(newData);
            newData.setMetric(dataLeft.getMetric());
            List<TimeValuePair> newValues = new ArrayList<>();
            newData.setValues(newValues);
            for (int j = 0; j < dataLeft.getValues().size(); j++) {
                double lv = Double.parseDouble(dataLeft.getValues().get(j).getValue());
                double rv = Double.parseDouble(dataRight.getValues().get(j).getValue());
                newValues.add(new TimeValuePair(
                    dataLeft.getValues().get(j).getTime(),
                    formatDoubleValue(scalarBinaryOp(lv, rv, opType))
                ));
            }
        }
        return result;
    }

    static MetricsRangeResult matrixAggregateOp(MetricsRangeResult result, int funcType, List<String> groupingBy) {
        List<MetricRangeData> metricDataList = result.getMetricDataList();
        Map<List<LabelValuePair>, List<MetricRangeData>> groupedResult = metricDataList
            .stream().collect(groupingBy(rangeData -> getLabels(groupingBy, rangeData), LinkedHashMap::new, toList()));

        MetricsRangeResult rangeResult = new MetricsRangeResult();
        rangeResult.setResultType(ParseResultType.METRICS_RANGE);
        rangeResult.setRangeExpression(result.isRangeExpression());
        AggregateLabelsFuncFactory factory = getAggregateFuncFactory(funcType);
        groupedResult.forEach((labels, dataList) -> {
            if (dataList.isEmpty()) {
                return;
            }
            List<TimeValuePair> combineTo = dataList.get(0).getValues();
            for (int i = 0; i < combineTo.size(); i++) {
                AggregateLabelsFunc aggregateLabelsFunc = factory.getAggregateLabelsFunc();
                for (MetricRangeData rangeData : dataList) {
                    TimeValuePair toCombine = rangeData.getValues().get(i);
                    if (StringUtil.isNotBlank(toCombine.getValue())) {
                        aggregateLabelsFunc.combine(Double.parseDouble(toCombine.getValue()));
                    }
                }

                TimeValuePair timeValuePair = combineTo.get(i);
                Double aggResult = aggregateLabelsFunc.getResult();
                if (aggResult != null) {
                    timeValuePair.setValue(aggResult.toString());
                }
            }
            MetricRangeData rangeData = new MetricRangeData();
            rangeData.setMetric(new MetricInfo(null));
            rangeData.getMetric().setLabels(labels);
            rangeData.setValues(combineTo);
            rangeResult.getMetricDataList().add(rangeData);
        });

        return rangeResult;
    }

    private static AggregateLabelsFuncFactory getAggregateFuncFactory(int funcType) {
        switch (funcType) {
            case PromQLParser.AVG:
                return AvgAggregateLabelsFunc::new;
            case PromQLParser.SUM:
                return SumAggregateLabelsFunc::new;
            case PromQLParser.MAX:
                return MaxAggregateLabelsFunc::new;
            case PromQLParser.MIN:
                return MinAggregateLabelsFunc::new;
            default:
                throw new IllegalArgumentException("Unsupported aggregate function type: " + funcType);
        }
    }

    private static List<LabelValuePair> getLabels(List<String> groupingBy, MetricRangeData data) {
        return groupingBy.stream()
                         .map(
                             labelName ->
                                 data.getMetric().getLabels()
                                           .stream().filter(label -> labelName.equals(label.getLabelName()))
                                           .findAny().orElseGet(() -> new LabelValuePair(labelName, ""))
                         )
                         .collect(toList());
    }

    static double scalarBinaryOp(double leftValue, double rightValue, int opType) {
        double calculatedResult = 0;
        switch (opType) {
            case PromQLParser.ADD:
                calculatedResult = leftValue + rightValue;
                break;
            case PromQLParser.SUB:
                calculatedResult = leftValue - rightValue;
                break;
            case PromQLParser.MUL:
                calculatedResult = leftValue * rightValue;
                break;
            case PromQLParser.DIV:
                calculatedResult = leftValue / rightValue;
                break;
            case PromQLParser.MOD:
                calculatedResult = leftValue % rightValue;
                break;
        }
        return calculatedResult;
    }

    static int scalarCompareOp(double leftValue, double rightValue, int opType) {
        int comparedResult = 0;
        switch (opType) {
            case PromQLParser.DEQ:
                comparedResult = boolToInt(leftValue == rightValue);
                break;
            case PromQLParser.NEQ:
                comparedResult = boolToInt(leftValue != rightValue);
                break;
            case PromQLParser.GT:
                comparedResult = boolToInt(leftValue > rightValue);
                break;
            case PromQLParser.LT:
                comparedResult = boolToInt(leftValue < rightValue);
                break;
            case PromQLParser.GTE:
                comparedResult = boolToInt(leftValue >= rightValue);
                break;
            case PromQLParser.LTE:
                comparedResult = boolToInt(leftValue <= rightValue);
                break;
        }
        return comparedResult;
    }

    private static int boolToInt(boolean v) {
        return v ? 1 : 0;
    }

    static MetricsRangeResult matrixScalarCompareOp(MetricsRangeResult matrix, ScalarResult scalar,
                                                    int opType, boolean boolModifier) {
        MetricsRangeResult result = new MetricsRangeResult();
        result.setResultType(ParseResultType.METRICS_RANGE);
        result.setRangeExpression(matrix.isRangeExpression());
        matrix.getMetricDataList().forEach(metricData -> {
            MetricRangeData newData = new MetricRangeData();
            result.getMetricDataList().add(newData);
            newData.setMetric(metricData.getMetric());

            List<TimeValuePair> newValues;
            if (boolModifier) {
                newValues = metricData.getValues()
                                      .stream()
                                      .map(timeValuePair -> {
                                          return new TimeValuePair(
                                              timeValuePair.getTime(),
                                              Integer.toString(scalarCompareOp(
                                                  Double.parseDouble(timeValuePair.getValue()),
                                                  scalar.getValue(), opType
                                              ))
                                          );
                                      })
                                      .collect(Collectors.toList());
            } else {
                newValues = metricData.getValues()
                                      .stream()
                                      .filter(timeValuePair ->
                                                  scalarCompareOp(
                                                      Double.parseDouble(timeValuePair.getValue()),
                                                      scalar.getValue(), opType
                                                  ) == 1
                                      )
                                      .collect(Collectors.toList());
            }
            newData.setValues(newValues);
        });
        return result;
    }

    static MetricsRangeResult matrixCompareOp(MetricsRangeResult matrixLeft,
                                              MetricsRangeResult matrixRight,
                                              int opType, boolean boolModifier) throws IllegalExpressionException {
        MetricsRangeResult result = new MetricsRangeResult();
        result.setResultType(ParseResultType.METRICS_RANGE);
        result.setRangeExpression(matrixLeft.isRangeExpression());
        for (int i = 0; i < matrixLeft.getMetricDataList().size(); i++) {
            MetricRangeData dataLeft = matrixLeft.getMetricDataList().get(i);
            MetricRangeData dataRight = matrixRight.getMetricDataList().get(i);
            if (!dataLeft.getMetric().getLabels().equals(dataRight.getMetric().getLabels())) {
                throw new IllegalExpressionException(
                    "The metric info result left in conformity with right.");
            }
            if (dataLeft.getValues().size() != dataRight.getValues().size()) {
                throw new IllegalExpressionException(
                    "The metric value range left in conformity with right.");
            }
            MetricRangeData newData = new MetricRangeData();
            result.getMetricDataList().add(newData);
            if (boolModifier) {
                // metric name should be removed between two matrix bool compare
                MetricInfo metricInfo = new MetricInfo(null);
                metricInfo.setLabels(dataLeft.getMetric().getLabels());
                newData.setMetric(metricInfo);
            } else {
                newData.setMetric(dataLeft.getMetric());
            }
            List<TimeValuePair> newValues = new ArrayList<>();
            newData.setValues(newValues);
            for (int j = 0; j < dataLeft.getValues().size(); j++) {
                double lv = Double.parseDouble(dataLeft.getValues().get(j).getValue());
                double rv = Double.parseDouble(dataRight.getValues().get(j).getValue());
                if (boolModifier) {
                    long time = dataLeft.getValues().get(j).getTime();
                    newValues.add(new TimeValuePair(time, Integer.toString(scalarCompareOp(lv, rv, opType))));
                } else {
                    if (scalarCompareOp(lv, rv, opType) == 1) {
                        newValues.add(dataLeft.getValues().get(j));
                    }
                }
            }
        }
        return result;
    }

    public static List<TimeValuePair> buildMatrixValues(Duration duration, String singleValue) {
        List<PointOfTime> times = duration.assembleDurationPoints();
        List<TimeValuePair> values = new ArrayList<>(times.size());
        for (PointOfTime time : times) {
            long retTimestampSec = DurationUtils.INSTANCE.parseToDateTime(
                                                    duration.getStep(), time.getPoint())
                                                         .getMillis() / 1000;
            TimeValuePair value = new TimeValuePair(
                retTimestampSec, singleValue);
            values.add(value);
        }
        return values;
    }

    static List<TimeValuePair> buildMatrixValues(Duration duration, MetricsValues metricsValues) {
        List<PointOfTime> times = duration.assembleDurationPoints();
        List<TimeValuePair> values = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            long retTimestampSec = DurationUtils.INSTANCE.parseToDateTime(
                                                    duration.getStep(), times.get(i).getPoint())
                                                         .getMillis() / 1000;
            KVInt kvInt = metricsValues.getValues().getValues().get(i);
            if (!kvInt.isEmptyValue()) {
                TimeValuePair value = new TimeValuePair(
                    retTimestampSec, Long.toString(kvInt.getValue()));
                values.add(value);
            }
        }
        return values;
    }

    public static String formatDoubleValue(double v) {
        DecimalFormat format = new DecimalFormat("#.##");
        return format.format(v);
    }

    /**
     * Format duration string to org.joda.time.Duration.
     * Don't support year and month because the days vary in length.
     * @param duration such as "5d", "30m", "5d30m, "1w, "1w5d"
     * @return org.joda.time.Duration
     */
    public static org.joda.time.Duration formatDuration(String duration) {
        PeriodFormatter f = new PeriodFormatterBuilder()
            .appendWeeks().appendSuffix("w")
            .appendDays().appendSuffix("d")
            .appendHours().appendSuffix("h")
            .appendMinutes().appendSuffix("m")
            .appendSeconds().appendSuffix("s")
            .appendMillis().appendSuffix("ms")
            .toFormatter();
        return f.parsePeriod(duration).toStandardDuration();
    }
}
