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
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.query.promql.entity.MetricRangeData;
import org.apache.skywalking.oap.query.promql.entity.TimeValuePair;
import org.apache.skywalking.oap.query.promql.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResultType;
import org.apache.skywalking.oap.query.promql.rt.result.MetricsRangeResult;
import org.apache.skywalking.oap.query.promql.rt.result.ScalarResult;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.promql.rt.grammar.PromQLParser;
import org.joda.time.DateTime;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

public class PromOpUtils {
    //Adopt skywalking time step.
    public static Duration timestamp2Duration(long startTS, long endTS) {
        Duration duration = new Duration();
        if (endTS < startTS) {
            throw new IllegalArgumentException("End time must not be before start");
        }
        DateTime startDT = new DateTime(startTS);
        DateTime endDT = new DateTime(endTS);

        long durationValue = endTS - startTS;

        if (durationValue <= 3600000) {
            duration.setStep(Step.MINUTE);
            duration.setStart(startDT.toString(DurationUtils.YYYY_MM_DD_HHMM));
            duration.setEnd(endDT.toString(DurationUtils.YYYY_MM_DD_HHMM));
        } else if (durationValue <= 86400000) {
            duration.setStep(Step.HOUR);
            duration.setStart(startDT.toString(DurationUtils.YYYY_MM_DD_HH));
            duration.setEnd(endDT.toString(DurationUtils.YYYY_MM_DD_HH));
        } else {
            duration.setStep(Step.DAY);
            duration.setStart(startDT.toString(DurationUtils.YYYY_MM_DD));
            duration.setEnd(endDT.toString(DurationUtils.YYYY_MM_DD));
        }
        return duration;
    }

    static MetricsRangeResult matrixScalarBinaryOp(MetricsRangeResult matrix, ScalarResult scalar, int opType) {
        MetricsRangeResult result = new MetricsRangeResult();
        result.setResultType(ParseResultType.METRICS_RANGE);
        matrix.getMetricDataList().forEach(metricData -> {
            MetricRangeData newData = new MetricRangeData();
            result.getMetricDataList().add(newData);
            newData.setMetric(metricData.getMetric());
            List<TimeValuePair> newValues = metricData.getValues().stream().map(value -> {
                double v = Double.parseDouble(value.getValue());
                return new TimeValuePair(
                    value.getTime(),
                    formatDoubleValue(scalarBinaryOp(v, scalar.getValue(), opType))
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
        for (int i = 0; i < matrixLeft.getMetricDataList().size(); i++) {
            MetricRangeData dataLeft = matrixLeft.getMetricDataList().get(i);
            MetricRangeData dataRight = matrixRight.getMetricDataList().get(i);
            if (!dataLeft.getMetric().equals(dataRight.getMetric())) {
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

    static MetricsRangeResult matrixScalarCompareOp(MetricsRangeResult matrix, ScalarResult scalar, int opType) {
        MetricsRangeResult result = new MetricsRangeResult();
        result.setResultType(ParseResultType.METRICS_RANGE);
        matrix.getMetricDataList().forEach(metricData -> {
            MetricRangeData newData = new MetricRangeData();
            result.getMetricDataList().add(newData);
            newData.setMetric(metricData.getMetric());
            List<TimeValuePair> newValues = metricData.getValues()
                                                      .stream()
                                                      .filter(timeValuePair -> scalarCompareOp(
                                                          Double.parseDouble(timeValuePair.getValue()),
                                                          scalar.getValue(), opType
                                                      ) == 1)
                                                      .collect(
                                                          Collectors.toList());
            newData.setValues(newValues);
        });
        return result;
    }

    static MetricsRangeResult matrixCompareOp(MetricsRangeResult matrixLeft,
                                              MetricsRangeResult matrixRight,
                                              int opType) throws IllegalExpressionException {
        MetricsRangeResult result = new MetricsRangeResult();
        result.setResultType(ParseResultType.METRICS_RANGE);
        for (int i = 0; i < matrixLeft.getMetricDataList().size(); i++) {
            MetricRangeData dataLeft = matrixLeft.getMetricDataList().get(i);
            MetricRangeData dataRight = matrixRight.getMetricDataList().get(i);
            if (!dataLeft.getMetric().equals(dataRight.getMetric())) {
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
                if (scalarCompareOp(lv, rv, opType) == 1) {
                    newValues.add(dataLeft.getValues().get(j));
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
