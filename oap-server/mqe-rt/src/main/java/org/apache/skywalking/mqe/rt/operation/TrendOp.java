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

package org.apache.skywalking.mqe.rt.operation;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.MQEValue;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;

public class TrendOp {
    public static ExpressionResult doTrendOp(ExpressionResult expResult,
                                             int opType,
                                             int trendRange,
                                             Step step) throws IllegalExpressionException {
        switch (opType) {
            case MQEParser.INCREASE:
                return TrendOp.calculateIncrease(expResult, trendRange);
            case MQEParser.RATE:
                return TrendOp.calculateRate(expResult, trendRange, step);
        }

        throw new IllegalExpressionException("Unsupported function.");
    }

    private static ExpressionResult calculateIncrease(ExpressionResult expResult, int trendRange) {
        expResult.getResults().forEach(resultValues -> {
            List<MQEValue> mqeValues = resultValues.getValues();
            List<MQEValue> newMqeValues = new ArrayList<>();
            for (int i = trendRange; i < mqeValues.size(); i++) {
                MQEValue mqeValue = mqeValues.get(i);
                //if the current value is empty, then the trend value is empty
                if (mqeValue.isEmptyValue()) {
                    newMqeValues.add(mqeValue);
                    continue;
                }
                MQEValue newMqeValue = new MQEValue();
                newMqeValue.setId(mqeValue.getId());

                //if the previous value is empty, then the trend value is empty
                if (mqeValues.get(i - trendRange).isEmptyValue()) {
                    newMqeValue.setEmptyValue(true);
                    newMqeValues.add(newMqeValue);
                    continue;
                }

                newMqeValue.setEmptyValue(mqeValue.isEmptyValue());
                newMqeValue.setId(mqeValue.getId());
                newMqeValue.setTraceID(mqeValue.getTraceID());
                double newValue = mqeValue.getDoubleValue() - mqeValues.get(i - trendRange).getDoubleValue();
                newMqeValue.setDoubleValue(newValue);
                newMqeValues.add(newMqeValue);
            }
            resultValues.setValues(newMqeValues);
        });
        return expResult;
    }

    private static ExpressionResult calculateRate(ExpressionResult expResult, int trendRange, Step step) {
        ExpressionResult result = calculateIncrease(expResult, trendRange);
        long rangeSeconds;
        switch (step) {
            case SECOND:
                rangeSeconds = trendRange;
                break;
            case MINUTE:
                rangeSeconds = trendRange * 60;
                break;
            case HOUR:
                rangeSeconds = trendRange * 3600;
                break;
            case DAY:
                rangeSeconds = trendRange * 86400;
                break;
            default:
                throw new IllegalArgumentException("Unsupported step: " + step);
        }
        result.getResults().forEach(resultValues -> {
            resultValues.getValues().forEach(mqeValue -> {
                if (!mqeValue.isEmptyValue()) {
                    double newValue = mqeValue.getDoubleValue() / rangeSeconds;
                    mqeValue.setDoubleValue(newValue);
                }
            });
        });
        return result;
    }
}
