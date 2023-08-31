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
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.mqe.rt.MQEVisitorBase;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.ExpressionResultType;
import org.apache.skywalking.mqe.rt.type.MQEValue;
import org.apache.skywalking.mqe.rt.type.MQEValues;

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
        this.includeMetrics.add(metricName);

        if (ctx.parent instanceof MQEParser.TopNOPContext) {
            result.setType(ExpressionResultType.UNKNOWN);
            result.setError("Unsupported operation: [top_n] in alarm expression.");
            return result;
        }

        MQEValues mqeValues = new MQEValues();
        MQEValue mqeValue = new MQEValue();
        mqeValue.setEmptyValue(true);
        mqeValues.getValues().add(mqeValue);
        result.getResults().add(mqeValues);
        result.setType(ExpressionResultType.TIME_SERIES_VALUES);
        return result;
    }
}
