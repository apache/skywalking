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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.mqe.rt.MQEVisitorBase;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.ExpressionResultType;
import org.apache.skywalking.mqe.rt.type.MQEValue;
import org.apache.skywalking.mqe.rt.type.MQEValues;
import org.apache.skywalking.mqe.rt.type.Metadata;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Used for verify the alarm expression and get the metrics name when read the alarm rules.
 */
@Getter
@Slf4j
public class AlarmMQEVerifyVisitor extends MQEVisitorBase {
    private final Set<String> includeMetrics = new HashSet<>();

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

        this.includeMetrics.add(metricName);

        if (ctx.parent instanceof MQEParser.TopNOPContext) {
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError("Unsupported operation: [top_n] in alarm expression.");
            return result;
        }
        Column.ValueDataType dataType = valueColumn.get().getDataType();

        MQEValues mockMqeValues = new MQEValues();
        MQEValue mqeValue = new MQEValue();
        mqeValue.setEmptyValue(true);
        mockMqeValues.getValues().add(mqeValue);
        if (dataType == Column.ValueDataType.COMMON_VALUE) {
            result.getResults().add(mockMqeValues);
            result.setType(ExpressionResultType.TIME_SERIES_VALUES);
            return result;
        } else if (dataType == Column.ValueDataType.LABELED_VALUE) {
            List<String> labelValues = Collections.emptyList();
            if (ctx.label() != null) {
                String labelValue = ctx.label().labelValue().getText();
                String labelValueTrim = labelValue.substring(1, labelValue.length() - 1);
                if (StringUtil.isNotBlank(labelValueTrim)) {
                    labelValues = Arrays.asList(labelValueTrim.split(Const.COMMA));
                }
            }
            ArrayList<MQEValues> mqeValuesList = new ArrayList<>();
            if (CollectionUtils.isEmpty(labelValues)) {
                KeyValue label = new KeyValue(GENERAL_LABEL_NAME, GENERAL_LABEL_NAME);
                Metadata metadata = new Metadata();
                metadata.getLabels().add(label);
                mockMqeValues.setMetric(metadata);
                mqeValuesList.add(mockMqeValues);
            } else {
                for (String value : labelValues) {
                    Metadata metadata = new Metadata();
                    KeyValue label = new KeyValue(GENERAL_LABEL_NAME, value);
                    metadata.getLabels().add(label);
                    mockMqeValues.setMetric(metadata);
                    mqeValuesList.add(mockMqeValues);
                }
            }
            result.setType(ExpressionResultType.TIME_SERIES_VALUES);
            result.setResults(mqeValuesList);
            result.setLabeledResult(true);
            return result;
        } else {
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError("Metric dose not supported in alarm, metric: [" + metricName + "] is not a common or labeled metric.");
            return result;
        }
    }
}
