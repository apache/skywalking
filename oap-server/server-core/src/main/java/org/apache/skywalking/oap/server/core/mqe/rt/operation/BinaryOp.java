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

package org.apache.skywalking.oap.server.core.mqe.rt.operation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.oap.server.core.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.server.core.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.oap.server.core.mqe.rt.type.ExpressionResultType;
import org.apache.skywalking.oap.server.core.mqe.rt.type.MQEValue;
import org.apache.skywalking.oap.server.core.mqe.rt.type.MQEValues;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;

public class BinaryOp {
    public static ExpressionResult doBinaryOp(ExpressionResult left,
                                              ExpressionResult right,
                                              int opType) throws IllegalExpressionException {
        if (left.getType() == ExpressionResultType.SINGLE_VALUE && right.getType() == ExpressionResultType.SINGLE_VALUE) {
            return single2SingleBinaryOp(left, right, opType);
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
            return seriesBinaryOp(left, right, opType);
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
        ExpressionResult result = new ExpressionResult();
        if (!seriesLeft.isLabeledResult() && !seriesRight.isLabeledResult()) { // no labeled with no labeled
            result = seriesNoLabeled(seriesLeft, seriesRight, opType);
        } else if (seriesLeft.isLabeledResult() && !seriesRight.isLabeledResult()) { // labeled with no labeled
            result = seriesLabeledWithNoLabeled(seriesLeft, seriesRight, opType);
        } else if (!seriesLeft.isLabeledResult() && seriesRight.isLabeledResult()) { // no labeled with labeled
            result = seriesLabeledWithNoLabeled(seriesRight, seriesLeft, opType);
        } else if (seriesLeft.isLabeledResult() && seriesRight.isLabeledResult()) { // labeled with labeled
            result = seriesLabeledWithLabeled(seriesLeft, seriesRight, opType);
        } else {
            throw new IllegalExpressionException("Binary operation don't support these expression.");
        }

        return result;
    }

    private static ExpressionResult single2SingleBinaryOp(ExpressionResult singleLeft,
                                                          ExpressionResult singleRight,
                                                          int opType) {
        ExpressionResult result = new ExpressionResult();
        MQEValue mqeValue = new MQEValue();
        MQEValues mqeValues = new MQEValues();
        mqeValues.getValues().add(mqeValue);
        result.getResults().add(mqeValues);
        result.setType(ExpressionResultType.SINGLE_VALUE);

        MQEValue left = singleLeft.getResults().get(0).getValues().get(0);
        MQEValue right = singleRight.getResults().get(0).getValues().get(0);
        //return null if one of them is empty
        if (left.isEmptyValue() || right.isEmptyValue()) {
            mqeValue.setEmptyValue(true);
        } else {
            double value = scalarBinaryOp(left.getDoubleValue(), right.getDoubleValue(), opType);
            mqeValue.setDoubleValue(value);
            mqeValue.setEmptyValue(false);
        }
        return result;
    }

    //series or list with scalar
    private static ExpressionResult many2OneBinaryOp(ExpressionResult manyResult,
                                                     ExpressionResult singleResult,
                                                     int opType) {
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

    private static ExpressionResult seriesNoLabeled(ExpressionResult seriesLeft,
                                                    ExpressionResult seriesRight,
                                                    int opType) {
        MQEValues mqeValuesL = seriesLeft.getResults().get(0);
        MQEValues mqeValuesR = seriesRight.getResults().get(0);
        mqeValuesL.setMetric(null);
        for (int i = 0; i < mqeValuesL.getValues().size(); i++) {
            //clean metric info
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

        return seriesLeft;
    }

    private static ExpressionResult seriesLabeledWithNoLabeled(ExpressionResult seriesLeft,
                                                               ExpressionResult seriesRight,
                                                               int opType) {
        MQEValues mqeValuesR = seriesRight.getResults().get(0);
        seriesLeft.getResults().forEach(mqeValuesL -> {
            for (int i = 0; i < mqeValuesL.getValues().size(); i++) {
                //reserve left metric info
                MQEValue valueL = mqeValuesL.getValues().get(i);
                MQEValue valueR = mqeValuesR.getValues().get(i);
                if (valueL.isEmptyValue() || valueR.isEmptyValue()) {
                    valueL.setEmptyValue(true);
                    valueL.setDoubleValue(0);
                    continue;
                }
                double newValue = scalarBinaryOp(valueL.getDoubleValue(), valueR.getDoubleValue(), opType);
                mqeValuesL.getValues().get(i).setDoubleValue(newValue);
            }
        });

        return seriesLeft;
    }

    private static ExpressionResult seriesLabeledWithLabeled(ExpressionResult seriesLeft,
                                                             ExpressionResult seriesRight,
                                                             int opType) throws IllegalExpressionException {
        Map<KeyValue, List<MQEValue>> labelMapR = new HashMap<>();
        if (seriesLeft.getResults().size() != seriesRight.getResults().size()) {
            throw new IllegalExpressionException(
                "Binary operation between labeled metrics should have the same label.");
        }
        seriesRight.getResults().forEach(mqeValuesR -> {
            // For now, we only have a single label named `label`
            labelMapR.put(mqeValuesR.getMetric().getLabels().get(0), mqeValuesR.getValues());
        });
        for (MQEValues mqeValuesL : seriesLeft.getResults()) {
            for (int i = 0; i < mqeValuesL.getValues().size(); i++) {
                //reserve left metric info
                MQEValue valueL = mqeValuesL.getValues().get(i);
                List<MQEValue> mqeValuesR = labelMapR.get(mqeValuesL.getMetric().getLabels().get(0));
                if (mqeValuesR == null) {
                    throw new IllegalExpressionException(
                        "Binary operation between labeled metrics should have the same label.");
                }
                MQEValue valueR = mqeValuesR.get(i);
                if (valueL.isEmptyValue() || valueR.isEmptyValue()) {
                    valueL.setEmptyValue(true);
                    valueL.setDoubleValue(0);
                    continue;
                }
                double newValue = scalarBinaryOp(valueL.getDoubleValue(), valueR.getDoubleValue(), opType);
                mqeValuesL.getValues().get(i).setDoubleValue(newValue);
            }
        }

        return seriesLeft;
    }
}
