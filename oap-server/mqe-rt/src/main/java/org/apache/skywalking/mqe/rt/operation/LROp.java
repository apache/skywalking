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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResultType;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValue;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValues;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;

@FunctionalInterface
public interface LROp {

    double apply(double left, double right, int opType);

    static ExpressionResult doLROp(ExpressionResult left,
                                          ExpressionResult right,
                                          int opType, LROp calculate) throws IllegalExpressionException {
        if (left.getType() == ExpressionResultType.SINGLE_VALUE && right.getType() == ExpressionResultType.SINGLE_VALUE) {
            return single2SingleBinaryOp(left, right, opType, calculate);
        } else if ((left.getType() == ExpressionResultType.TIME_SERIES_VALUES ||
            left.getType() == ExpressionResultType.SORTED_LIST ||
            left.getType() == ExpressionResultType.RECORD_LIST)
            && right.getType() == ExpressionResultType.SINGLE_VALUE) {
            return many2OneBinaryOp(left, right, opType, calculate);
        } else if (left.getType() == ExpressionResultType.SINGLE_VALUE &&
            (right.getType() == ExpressionResultType.TIME_SERIES_VALUES ||
                right.getType() == ExpressionResultType.SORTED_LIST ||
                right.getType() == ExpressionResultType.RECORD_LIST)) {
            return one2ManyBinaryOp(left, right, opType, calculate);
        } else if (left.getType() == ExpressionResultType.TIME_SERIES_VALUES && right.getType() == ExpressionResultType.TIME_SERIES_VALUES) {
            return seriesBinaryOp(left, right, opType, calculate);
        }

        throw new IllegalExpressionException("Unsupported operation.");
    }

    //series with series
    private static ExpressionResult seriesBinaryOp(ExpressionResult seriesLeft,
                                                   ExpressionResult seriesRight,
                                                   int opType, LROp calculate) throws IllegalExpressionException {
        ExpressionResult result = new ExpressionResult();
        if (!seriesLeft.isLabeledResult() && !seriesRight.isLabeledResult()) { // no labeled with no labeled
            result = seriesNoLabeled(seriesLeft, seriesRight, opType, calculate);
        } else if (seriesLeft.isLabeledResult() && !seriesRight.isLabeledResult()) { // labeled with no labeled
            result = seriesLabeledWithNoLabeled(seriesLeft, seriesRight, opType, calculate);
        } else if (!seriesLeft.isLabeledResult() && seriesRight.isLabeledResult()) { // no labeled with labeled
            result = seriesNoLabeledWithLabeled(seriesLeft, seriesRight, opType, calculate);
        } else { // labeled with labeled
            result = seriesLabeledWithLabeled(seriesLeft, seriesRight, opType, calculate);
        }

        return result;
    }

    private static ExpressionResult single2SingleBinaryOp(ExpressionResult singleLeft,
                                                          ExpressionResult singleRight,
                                                          int opType, LROp calculate) throws IllegalExpressionException {
        if (!singleLeft.isLabeledResult() && !singleRight.isLabeledResult()) { // no labeled with no labeled
            return single2SingleNoLabeled(singleLeft, singleRight, opType, calculate);
        } else if (singleLeft.isLabeledResult() && !singleRight.isLabeledResult()) { // labeled with no labeled
            return many2OneBinaryOp(singleLeft, singleRight, opType, calculate);
        } else if (!singleLeft.isLabeledResult() && singleRight.isLabeledResult()) { // no labeled with labeled
            return one2ManyBinaryOp(singleLeft, singleRight, opType, calculate);
        } else { // labeled with labeled
            return single2SingleLabeled(singleLeft, singleRight, opType, calculate);
        }
    }

    private static ExpressionResult single2SingleNoLabeled(ExpressionResult singleLeft,
                                                          ExpressionResult singleRight,
                                                          int opType, LROp calculate) throws IllegalExpressionException {
        if (singleLeft.getResults().isEmpty() ||
            singleLeft.getResults().get(0).getValues().size() != 1) {
            throw new IllegalExpressionException("Single to Single, left result is empty or has more than one value.");
        }

        if (singleRight.getResults().isEmpty() ||
            singleRight.getResults().get(0).getValues().size() != 1) {
            throw new IllegalExpressionException("Single to Single, right result is empty or has more than one value.");
        }

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
            double value = calculate.apply(left.getDoubleValue(), right.getDoubleValue(), opType);
            mqeValue.setDoubleValue(value);
        }
        return result;
    }

    private static ExpressionResult single2SingleLabeled(ExpressionResult singleLeft,
                                                           ExpressionResult singleRight,
                                                           int opType, LROp calculate) throws IllegalExpressionException {
        Map<Set<KeyValue>, List<MQEValue>> labelMapR = new HashMap<>();
        singleRight.getResults().forEach(mqeValuesR -> {
            labelMapR.put(new HashSet<>(mqeValuesR.getMetric().getLabels()), mqeValuesR.getValues());
        });
        for (MQEValues mqeValuesL : singleLeft.getResults()) {
            if (mqeValuesL.getValues().size() != 1) {
                throw new IllegalExpressionException("Single Labeled to Single Labeled, left labeled result is empty or has more than one value.");
            }
            //reserve left metric info
            MQEValue valueL = mqeValuesL.getValues().get(0);
            List<MQEValue> mqeValuesR = labelMapR.get(new HashSet<>(mqeValuesL.getMetric().getLabels()));
            if (mqeValuesR == null) {
                valueL.setEmptyValue(true);
            } else {
                if (mqeValuesR.size() != 1) {
                    throw new IllegalExpressionException("Single Labeled to Single Labeled, right labeled result has more than one value.");
                }
                MQEValue valueR = mqeValuesR.get(0);
                if (valueL.isEmptyValue() || valueR.isEmptyValue()) {
                    valueL.setEmptyValue(true);
                    continue;
                }
                double value = calculate.apply(valueL.getDoubleValue(), valueR.getDoubleValue(), opType);
                valueL.setDoubleValue(value);
            }
        }

        return singleLeft;
    }

    //series or list or labeled single value with scalar
    private static ExpressionResult many2OneBinaryOp(ExpressionResult manyResult,
                                                     ExpressionResult singleResult,
                                                     int opType, LROp calculate) throws IllegalExpressionException {
        if (singleResult.getResults().isEmpty() ||
            singleResult.getResults().get(0).getValues().size() != 1) {
            throw new IllegalExpressionException("Many to One, single result is empty or has more than one value.");
        }
        manyResult.getResults().forEach(mqeValues -> {
            mqeValues.getValues().forEach(mqeValue -> {
                if (!mqeValue.isEmptyValue()) {
                    double newValue = calculate.apply(
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

    //scalar with series or list or labeled single value
    private static ExpressionResult one2ManyBinaryOp(ExpressionResult singleResult,
                                                     ExpressionResult manyResult,
                                                     int opType, LROp calculate) throws IllegalExpressionException {
        if (singleResult.getResults().isEmpty() ||
            singleResult.getResults().get(0).getValues().size() != 1) {
            throw new IllegalExpressionException("One to Many, single result is empty or has more than one value.");
        }
        manyResult.getResults().forEach(mqeValues -> {
            mqeValues.getValues().forEach(mqeValue -> {
                if (!mqeValue.isEmptyValue()) {
                    double newValue = calculate.apply(
                        singleResult.getResults()
                                    .get(0)
                                    .getValues()
                                    .get(0)
                                    .getDoubleValue(),
                        mqeValue.getDoubleValue(), opType
                    );
                    mqeValue.setDoubleValue(newValue);
                }
            });
        });
        return manyResult;
    }

    private static ExpressionResult seriesNoLabeled(ExpressionResult seriesLeft,
                                                    ExpressionResult seriesRight,
                                                    int opType, LROp calculate) throws IllegalExpressionException {
        if (seriesLeft.getResults().isEmpty() || seriesRight.getResults().isEmpty()) {
            throw new IllegalExpressionException("Series No Labeled, left or right result is empty.");
        }
        MQEValues mqeValuesL = seriesLeft.getResults().get(0);
        MQEValues mqeValuesR = seriesRight.getResults().get(0);
        if (mqeValuesL.getValues().size() != mqeValuesR.getValues().size()) {
            throw new IllegalExpressionException("Series No Labeled, left and right series value size not equal.");
        }
        for (int i = 0; i < mqeValuesL.getValues().size(); i++) {
            //clean metric info
            MQEValue valueL = mqeValuesL.getValues().get(i);
            MQEValue valueR = mqeValuesR.getValues().get(i);
            if (valueL.isEmptyValue() || valueR.isEmptyValue()) {
                valueL.setEmptyValue(true);
                continue;
            }
            //time should be mapped
            double newValue = calculate.apply(valueL.getDoubleValue(), valueR.getDoubleValue(), opType);
            mqeValuesL.getValues().get(i).setDoubleValue(newValue);
        }

        return seriesLeft;
    }

    private static ExpressionResult seriesLabeledWithNoLabeled(ExpressionResult seriesLeft,
                                                               ExpressionResult seriesRight,
                                                               int opType, LROp calculate) throws IllegalExpressionException {
        if (seriesRight.getResults().isEmpty()) {
            throw new IllegalExpressionException("Series Labeled with No Labeled, no labeled result is empty.");
        }
        MQEValues mqeValuesR = seriesRight.getResults().get(0);
        for (MQEValues mqeValuesL : seriesLeft.getResults()) {
            if (mqeValuesL.getValues().size() != mqeValuesR.getValues().size()) {
                throw new IllegalExpressionException("Series Labeled with No Labeled, left and right series value size not equal.");
            }
            for (int i = 0; i < mqeValuesL.getValues().size(); i++) {
                //reserve left metric info
                MQEValue valueL = mqeValuesL.getValues().get(i);
                MQEValue valueR = mqeValuesR.getValues().get(i);
                if (valueL.isEmptyValue() || valueR.isEmptyValue()) {
                    valueL.setEmptyValue(true);
                    continue;
                }
                double newValue = calculate.apply(valueL.getDoubleValue(), valueR.getDoubleValue(), opType);
                mqeValuesL.getValues().get(i).setDoubleValue(newValue);
            }
        }

        return seriesLeft;
    }

    private static ExpressionResult seriesNoLabeledWithLabeled(ExpressionResult seriesLeft,
                                                               ExpressionResult seriesRight,
                                                               int opType, LROp calculate) throws IllegalExpressionException {
        if (seriesLeft.getResults().isEmpty()) {
            throw new IllegalExpressionException("Series No Labeled with Labeled, no labeled result is empty.");
        }
        MQEValues mqeValuesL = seriesLeft.getResults().get(0);
        for (MQEValues mqeValuesR : seriesRight.getResults()) {
            if (mqeValuesL.getValues().size() != mqeValuesR.getValues().size()) {
                throw new IllegalExpressionException("Series No Labeled with Labeled, left and right series value size not equal.");
            }
            for (int i = 0; i < mqeValuesL.getValues().size(); i++) {
                //reserve left metric info
                MQEValue valueL = mqeValuesL.getValues().get(i);
                MQEValue valueR = mqeValuesR.getValues().get(i);
                if (valueL.isEmptyValue() || valueR.isEmptyValue()) {
                    valueL.setEmptyValue(true);
                    continue;
                }
                double newValue = calculate.apply(valueL.getDoubleValue(), valueR.getDoubleValue(), opType);
                mqeValuesR.getValues().get(i).setDoubleValue(newValue);
            }
        }

        return seriesRight;
    }

    private static ExpressionResult seriesLabeledWithLabeled(ExpressionResult seriesLeft,
                                                             ExpressionResult seriesRight,
                                                             int opType, LROp calculate) throws IllegalExpressionException {
        Map<Set<KeyValue>, List<MQEValue>> labelMapR = new HashMap<>();
        seriesRight.getResults().forEach(mqeValuesR -> {
            labelMapR.put(new HashSet<>(mqeValuesR.getMetric().getLabels()), mqeValuesR.getValues());
        });
        for (MQEValues mqeValuesL : seriesLeft.getResults()) {
            for (int i = 0; i < mqeValuesL.getValues().size(); i++) {
                //reserve left metric info, if right metric not exist, set empty value
                MQEValue valueL = mqeValuesL.getValues().get(i);
                List<MQEValue> mqeValuesR = labelMapR.get(new HashSet<>(mqeValuesL.getMetric().getLabels()));
                if (mqeValuesR == null) {
                    valueL.setEmptyValue(true);
                } else {
                    if (mqeValuesR.size() != mqeValuesL.getValues().size()) {
                        throw new IllegalExpressionException("Series Labeled with Labeled, left and right series value size not equal.");
                    }
                    MQEValue valueR = mqeValuesR.get(i);
                    if (valueL.isEmptyValue() || valueR.isEmptyValue()) {
                        valueL.setEmptyValue(true);
                        continue;
                    }
                    double newValue = calculate.apply(valueL.getDoubleValue(), valueR.getDoubleValue(), opType);
                    mqeValuesL.getValues().get(i).setDoubleValue(newValue);
                }
            }
        }

        return seriesLeft;
    }
}
