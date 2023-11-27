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

package org.apache.skywalking.oap.server.core.alarm.provider.expr.rt;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.ExpressionResultType;
import org.apache.skywalking.mqe.rt.type.MQEValue;
import org.apache.skywalking.mqe.rt.type.MQEValues;
import org.apache.skywalking.mqe.rt.type.Metadata;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.DoubleValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.mqe.rt.MQEVisitorBase;
import org.apache.skywalking.oap.server.core.analysis.metrics.MultiIntValuesHolder;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.joda.time.LocalDateTime;

@Slf4j
public class AlarmMQEVisitor extends MQEVisitorBase {
    private final LinkedList<Map<String, Metrics>> metricsValues;
    private final Map<String, Map<String, Double>> commonValuesMap;
    private final Map<String, Map<String, DataTable>> labeledValuesMap;
    private final int windowSize;
    private final LocalDateTime endTime;
    private final ArrayList<String> windowTimes;
    private final int maxTrendRange;

    public AlarmMQEVisitor(final LinkedList<Map<String, Metrics>> metricsValues,
                           final LocalDateTime endTime,
                           final int maxTrendRange) {
        super(Step.MINUTE);
        this.metricsValues = metricsValues;
        this.commonValuesMap = new HashMap<>();
        this.labeledValuesMap = new HashMap<>();
        this.endTime = endTime;
        this.windowSize = metricsValues.size();
        this.windowTimes = initWindowTimes();
        this.maxTrendRange = maxTrendRange;
        this.initMetricsValues();
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

        //if no data, build empty value MQEValuesList for calculation
        List<MQEValues> mqeValuesList;
        if (dataType == Column.ValueDataType.COMMON_VALUE) {
            Map<String, Double> timeValues = commonValuesMap.get(metricName);
            if (CollectionUtils.isEmpty(timeValues)) {
                mqeValuesList = buildEmptyMQEValuesList();
            } else {
                mqeValuesList = buildMqeValuesList(timeValues);
            }
        } else if (dataType == Column.ValueDataType.LABELED_VALUE) {
            List<String> labelValues = Collections.emptyList();
            if (ctx.label() != null) {
                String labelValue = ctx.label().labelValue().getText();
                String labelValueTrim = labelValue.substring(1, labelValue.length() - 1);
                if (StringUtil.isNotBlank(labelValueTrim)) {
                    labelValues = Arrays.asList(labelValueTrim.split(Const.COMMA));
                }
            }
            Map<String, DataTable> timeValues = labeledValuesMap.get(metricName);
            if (CollectionUtils.isEmpty(timeValues)) {
                mqeValuesList = buildEmptyLabeledMQEValuesList(labelValues);
            } else {
                mqeValuesList = buildLabledMqeValuesList(timeValues, labelValues);
            }
            result.setLabeledResult(true);
        } else {
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError("Unsupported value type: " + dataType);
            return result;
        }
        if (!(ctx.parent instanceof MQEParser.TrendOPContext)) {
            //Trim the redundant data
            result.getResults().forEach(resultValues -> {
                List<MQEValue> mqeValues = resultValues.getValues();
                if (maxTrendRange > 0 && mqeValues.size() > maxTrendRange) {
                    resultValues.setValues(mqeValues.subList(maxTrendRange, mqeValues.size()));
                }
            });
        }
        result.setResults(mqeValuesList);
        result.setType(ExpressionResultType.TIME_SERIES_VALUES);
        return result;
    }

    @Override
    public ExpressionResult visitTrendOP(MQEParser.TrendOPContext ctx) {
        ExpressionResult result = super.visitTrendOP(ctx);
        int trendRange = Integer.parseInt(ctx.INTEGER().getText());
        //super.visitTrendOP only trim self trend range, trim more here due to all metrics window size is the same
        int trimIndex = maxTrendRange - trendRange;
        if (trimIndex > 0) {
            //Trim the redundant data
            result.getResults().forEach(resultValues -> {
                List<MQEValue> mqeValues = resultValues.getValues();
                if (mqeValues.size() > trimIndex) {
                    resultValues.setValues(mqeValues.subList(trimIndex, mqeValues.size()));
                }
            });
        }
        return result;
    }

    private ArrayList<String> initWindowTimes() {
        ArrayList<String> windowTimes = new ArrayList<>();
        for (int i = this.windowSize - 1; i >= 0; i--) {
            windowTimes.add(endTime.minusMinutes(i).toString("yyyyMMddHHmm"));
        }
        return windowTimes;
    }

    private void initMetricsValues() {
        for (Map<String, Metrics> metricsMap : metricsValues) {
            if (metricsMap == null) {
                continue;
            }
            for (Map.Entry<String, Metrics> entry : metricsMap.entrySet()) {
                String metricName = entry.getKey();
                Metrics metrics = entry.getValue();
                if (metrics instanceof LongValueHolder) {
                    initCommonMetricValues(metricName, ((LongValueHolder) metrics).getValue(), metrics.getTimeBucket());
                } else if (metrics instanceof IntValueHolder) {
                    initCommonMetricValues(metricName, ((IntValueHolder) metrics).getValue(), metrics.getTimeBucket());
                } else if (metrics instanceof DoubleValueHolder) {
                    initCommonMetricValues(metricName, ((DoubleValueHolder) metrics).getValue(), metrics.getTimeBucket());
                } else if (metrics instanceof MultiIntValuesHolder) {
                    int[] ivalueArray = ((MultiIntValuesHolder) metrics).getValues();
                    DataTable values = new DataTable();
                    for (int i = 0; i < ivalueArray.length; i++) {
                        values.put(String.valueOf(i), (long) ivalueArray[i]);
                    }
                    initLabeledMetricValues(metricName, values, metrics.getTimeBucket());
                } else if (metrics instanceof LabeledValueHolder) {
                    DataTable values = ((LabeledValueHolder) metrics).getValue();
                    initLabeledMetricValues(metricName, values, metrics.getTimeBucket());
                } else {
                    log.warn("Unsupported metrics {}", metricName);
                    return;
                }
            }
        }
    }

    private void initCommonMetricValues(String metricName, double value, long timeBucket) {
        Map<String, Double> timeValues = commonValuesMap.computeIfAbsent(
            metricName, v -> new HashMap<>());
        timeValues.put(String.valueOf(timeBucket), value);
    }

    private void initLabeledMetricValues(String metricName,
                                         DataTable values, long timeBucket) {
        Map<String, DataTable> timeValues = labeledValuesMap.computeIfAbsent(
            metricName, v -> new HashMap<>());
        timeValues.put(String.valueOf(timeBucket), values);
    }

    private List<MQEValues> buildMqeValuesList(Map<String, Double> timeValues) {
        List<MQEValues> mqeValuesList = new ArrayList<>();
            MQEValues mqeValues = new MQEValues();
            for (String time : windowTimes) {
                Metadata metadata = new Metadata();
                Double metricValue = timeValues.get(time);
                MQEValue mqeValue = new MQEValue();
                //use timeBucket as id here
                mqeValue.setId(time);
                if (metricValue != null) {
                    mqeValue.setDoubleValue(metricValue);
                } else {
                    mqeValue.setEmptyValue(true);
                }
                mqeValues.setMetric(metadata);
                mqeValues.getValues().add(mqeValue);
            }
            mqeValuesList.add(mqeValues);

        return mqeValuesList;
    }

    private List<MQEValues> buildLabledMqeValuesList(Map<String, DataTable> timeValues, List<String> labelValues) {
        List<MQEValues> mqeValuesList = new ArrayList<>();
        List<String> allLabelValues;
        if (CollectionUtils.isEmpty(labelValues) || labelValues.stream().allMatch(Strings::isNullOrEmpty)) {
            allLabelValues = timeValues.values().stream()
                             .flatMap(dataTable -> dataTable.keys().stream())
                             .distinct().collect(Collectors.toList());
        } else {
            allLabelValues = labelValues;
        }
        for (String label : allLabelValues) {
            MQEValues mqeValues = new MQEValues();
            for (String time : windowTimes) {
                Metadata metadata = new Metadata();
                DataTable dataTable = timeValues.getOrDefault(time, new DataTable());
                Long metricValue = dataTable.get(label);
                MQEValue mqeValue = new MQEValue();
                //use timeBucket as id here
                mqeValue.setId(time);
                if (metricValue != null) {
                    mqeValue.setDoubleValue(metricValue);
                } else {
                    mqeValue.setEmptyValue(true);
                }
                KeyValue labelValue = new KeyValue(GENERAL_LABEL_NAME, label);
                metadata.getLabels().add(labelValue);
                mqeValues.setMetric(metadata);
                mqeValues.getValues().add(mqeValue);
            }
            mqeValuesList.add(mqeValues);
        }
        return mqeValuesList;
    }

    //init MQEValues with empty value according to window size and end time
    private MQEValues initMQEValues() {
        MQEValues mqeValues = new MQEValues();
        for (String times : windowTimes) {
            MQEValue mqeValue = new MQEValue();
            mqeValue.setEmptyValue(true);
            mqeValue.setId(times);
            mqeValues.getValues().add(mqeValue);
        }
        return mqeValues;
    }

    private ArrayList<MQEValues> buildEmptyMQEValuesList() {
        ArrayList<MQEValues> mqeValuesList = new ArrayList<>();
        mqeValuesList.add(initMQEValues());
        return mqeValuesList;
    }

    private ArrayList<MQEValues> buildEmptyLabeledMQEValuesList(List<String> labelValues) {
        ArrayList<MQEValues> mqeValuesList = new ArrayList<>();
        if (CollectionUtils.isEmpty(labelValues)) {
            MQEValues mqeValues = initMQEValues();
            KeyValue label = new KeyValue(GENERAL_LABEL_NAME, GENERAL_LABEL_NAME);
            Metadata metadata = new Metadata();
            metadata.getLabels().add(label);
            mqeValues.setMetric(metadata);
            mqeValuesList.add(mqeValues);
        } else {
            for (String labelValue : labelValues) {
                MQEValues mqeValues = initMQEValues();
                Metadata metadata = new Metadata();
                KeyValue label = new KeyValue(GENERAL_LABEL_NAME, labelValue);
                metadata.getLabels().add(label);
                mqeValues.setMetric(metadata);
                mqeValuesList.add(mqeValues);
            }
        }
        return mqeValuesList;
    }
}
