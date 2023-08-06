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

import java.util.Collections;
import java.util.List;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.MQEValue;
import org.apache.skywalking.mqe.rt.type.MQEValues;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.AggregateLabelsFunc;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.AggregateLabelsFuncFactory;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.AvgAggregateLabelsFunc;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.MaxAggregateLabelsFunc;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.MinAggregateLabelsFunc;
import org.apache.skywalking.mqe.rt.operation.aggregatelabels.SumAggregateLabelsFunc;
import org.apache.skywalking.mqe.rt.type.Metadata;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

public class AggregateLabelsOp {

    public static ExpressionResult doAggregateLabelsOp(ExpressionResult result,
                                                       int funcType) throws IllegalExpressionException {
        switch (funcType) {
            case MQEParser.AVG:
                return aggregateLabeledValueResult(result, AvgAggregateLabelsFunc::new);
            case MQEParser.SUM:
                return aggregateLabeledValueResult(result, SumAggregateLabelsFunc::new);
            case MQEParser.MAX:
                return aggregateLabeledValueResult(result, MaxAggregateLabelsFunc::new);
            case MQEParser.MIN:
                return aggregateLabeledValueResult(result, MinAggregateLabelsFunc::new);
            default:
                throw new IllegalExpressionException("Unsupported aggregateLabels function.");
        }
    }

    private static ExpressionResult aggregateLabeledValueResult(ExpressionResult expResult,
                                                                AggregateLabelsFuncFactory factory) {
        List<MQEValues> results = expResult.getResults();
        if (CollectionUtils.isEmpty(results)) {
            return expResult;
        }

        List<MQEValue> combineTo = results.get(0).getValues();
        for (int i = 0; i < combineTo.size(); i++) {
            AggregateLabelsFunc aggregateLabelsFunc = factory.getAggregateLabelsFunc();
            for (MQEValues result : results) {
                MQEValue toCombine = result.getValues().get(i);
                if (!toCombine.isEmptyValue()) {
                    aggregateLabelsFunc.combine(toCombine.getDoubleValue());
                }
            }

            MQEValue mqeValue = combineTo.get(i);
            mqeValue.setTraceID(null);
            mqeValue.setEmptyValue(true);
            mqeValue.setDoubleValue(0);

            Double result = aggregateLabelsFunc.getResult();
            if (result != null) {
                mqeValue.setEmptyValue(false);
                mqeValue.setDoubleValue(result);
            }
        }

        MQEValues mqeValues = new MQEValues();
        mqeValues.setMetric(new Metadata());
        mqeValues.setValues(combineTo);
        expResult.setResults(Collections.singletonList(mqeValues));
        expResult.setLabeledResult(false);
        return expResult;
    }
}
