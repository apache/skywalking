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

package org.apache.skywalking.oap.query.graphql.mqe.rt.operation;

import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.oap.query.graphql.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResult;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResultType;
import org.apache.skywalking.oap.query.graphql.type.mql.MQEValue;
import org.apache.skywalking.oap.query.graphql.type.mql.MQEValues;

public class BinaryOp {
   public static ExpressionResult doBinaryOp(ExpressionResult left,
                                        ExpressionResult right,
                                        int opType) throws IllegalExpressionException {
        if (left.getType() == ExpressionResultType.SINGLE_VALUE && right.getType() == ExpressionResultType.SINGLE_VALUE) {
            double scalarLeft = left.getResults().get(0).getValues().get(0).getDoubleValue();
            double scalarRight = right.getResults().get(0).getValues().get(0).getDoubleValue();
            double value = scalarBinaryOp(scalarLeft, scalarRight, opType);
            ExpressionResult result = new ExpressionResult();
            MQEValue mqeValue = new MQEValue();
            mqeValue.setDoubleValue(value);
            mqeValue.setEmptyValue(false);
            MQEValues mqeValues = new MQEValues();
            mqeValues.getValues().add(mqeValue);
            result.getResults().add(mqeValues);
            result.setType(ExpressionResultType.SINGLE_VALUE);
            return result;
        } else if ((left.getType() == ExpressionResultType.TIME_SERIES_VALUES ||
            left.getType() == ExpressionResultType.SORTED_LIST ||
            left.getType() == ExpressionResultType.RECORD_LIST)
            && right.getType() == ExpressionResultType.SINGLE_VALUE) {
            return many2OneBinaryOp(left, right, opType);
        } else if (left.getType() == ExpressionResultType.SINGLE_VALUE &&
            (right.getType() == ExpressionResultType.TIME_SERIES_VALUES ||
                right.getType() == ExpressionResultType.SORTED_LIST ||
                right.getType() == ExpressionResultType.RECORD_LIST)) {
            return many2OneBinaryOp(right, left, opType);
        } else if (left.getType() == ExpressionResultType.TIME_SERIES_VALUES && right.getType() == ExpressionResultType.TIME_SERIES_VALUES) {
            return seriesBinaryOp(right, left, opType);
        }

        throw new IllegalExpressionException("Unsupported binary operation.");
    }

    //scalar with scalar
   private static double scalarBinaryOp(double leftValue, double rightValue, int opType) {
        double calculatedResult = 0;
        switch (opType) {
            case MQEParser.ADD:
                calculatedResult = leftValue + rightValue;
                break;
            case MQEParser.SUB:
                calculatedResult = leftValue - rightValue;
                break;
            case MQEParser.MUL:
                calculatedResult = leftValue * rightValue;
                break;
            case MQEParser.DIV:
                calculatedResult = leftValue / rightValue;
                break;
            case MQEParser.MOD:
                calculatedResult = leftValue % rightValue;
                break;
        }
        return calculatedResult;
    }

    //series with series
    private static ExpressionResult seriesBinaryOp(ExpressionResult seriesLeft,
                                           ExpressionResult seriesRight,
                                           int opType) throws IllegalExpressionException {
        if (seriesLeft.getResults().size() == 1 && seriesRight.getResults().size() == 1) {
            MQEValues mqeValuesL = seriesLeft.getResults().get(0);
            MQEValues mqeValuesR = seriesRight.getResults().get(0);
            for (int i = 0; i < mqeValuesL.getValues().size(); i++) {
                //clean metric info
                mqeValuesL.setMetric(null);
                MQEValue valueL = mqeValuesL.getValues().get(i);
                MQEValue valueR = mqeValuesR.getValues().get(i);
                if (valueL.isEmptyValue() || valueR.isEmptyValue()) {
                    valueL.setEmptyValue(true);
                    valueL.setDoubleValue(0);
                    continue;
                }
                //time should be mapped
                double newValue = scalarBinaryOp(valueL.getDoubleValue(), valueR.getDoubleValue(), opType);
                mqeValuesL.getValues().get(i).setDoubleValue(newValue);
            }

        } else {
            throw new IllegalExpressionException("Binary operation don't support labeled metrics.");
        }

        return seriesLeft;
    }

    //series or list with scalar
    private static ExpressionResult many2OneBinaryOp(ExpressionResult manyResult, ExpressionResult singleResult, int opType) {
        manyResult.getResults().forEach(mqeValues -> {
            mqeValues.getValues().forEach(mqeValue -> {
                if (!mqeValue.isEmptyValue()) {
                    double newValue = scalarBinaryOp(
                        mqeValue.getDoubleValue(), singleResult.getResults()
                                                               .get(0)
                                                               .getValues()
                                                               .get(0)
                                                               .getDoubleValue(), opType);
                    mqeValue.setDoubleValue(newValue);
                }
            });
        });
        return manyResult;
    }
}
