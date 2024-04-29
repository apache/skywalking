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

import java.util.HashSet;
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
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;

/**
 * Used for verify the alarm expression and get the metrics name when read the alarm rules.
 */
@Getter
@Slf4j
public class AlarmMQEVerifyVisitor extends MQEVisitorBase {
    private final Set<String> includeMetrics = new HashSet<>();
    private int maxTrendRange = 0;

    public AlarmMQEVerifyVisitor() {
        super(Step.MINUTE);
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
        result.getResults().add(mockMqeValues);
        result.setType(ExpressionResultType.TIME_SERIES_VALUES);
        if (dataType == Column.ValueDataType.COMMON_VALUE) {
            return result;
        } else if (dataType == Column.ValueDataType.LABELED_VALUE) {
            result.setLabeledResult(true);
            return result;
        } else {
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError("Metric dose not supported in alarm, metric: [" + metricName + "] is not a common or labeled metric.");
            return result;
        }
    }

    @Override
    public ExpressionResult visitTrendOP(MQEParser.TrendOPContext ctx) {
        int trendRange = Integer.parseInt(ctx.INTEGER().getText());
        if (trendRange < 1) {
            ExpressionResult result = new ExpressionResult();
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError("The trend range must be greater than 0.");
            return result;
        }
        setMaxTrendRange(trendRange);
        return super.visitTrendOP(ctx);
    }

    private void setMaxTrendRange(int trendRange) {
        if (trendRange > maxTrendRange) {
            maxTrendRange = trendRange;
        }
    }
}
