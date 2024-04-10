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

import java.util.LinkedHashMap;
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
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class AggregateLabelsOp {

    public static ExpressionResult doAggregateLabelsOp(ExpressionResult result,
                                                       int funcType,
                                                       List<String> labelNames) throws IllegalExpressionException {
        switch (funcType) {
            case MQEParser.AVG:
                return aggregateLabeledValueResult(result, labelNames, AvgAggregateLabelsFunc::new);
            case MQEParser.SUM:
                return aggregateLabeledValueResult(result, labelNames, SumAggregateLabelsFunc::new);
            case MQEParser.MAX:
                return aggregateLabeledValueResult(result, labelNames, MaxAggregateLabelsFunc::new);
            case MQEParser.MIN:
                return aggregateLabeledValueResult(result, labelNames, MinAggregateLabelsFunc::new);
            default:
                throw new IllegalExpressionException("Unsupported aggregateLabels function.");
        }
    }

    private static ExpressionResult aggregateLabeledValueResult(ExpressionResult expResult,
                                                                List<String> labelNames,
                                                                AggregateLabelsFuncFactory factory) {
        List<MQEValues> results = expResult.getResults();
        if (CollectionUtils.isEmpty(results)) {
            return expResult;
        }

        LinkedHashMap<List<KeyValue>, List<MQEValues>> groupedResult = results.stream().collect(groupingBy(mqeValues -> getLabels(labelNames, mqeValues),
                                                                                                          LinkedHashMap::new,
                                                                                                          toList()));
        if (groupedResult.size() == 1 && groupedResult.keySet().iterator().next().isEmpty()) {
            expResult.setLabeledResult(false);
        }
        expResult.getResults().clear();
        groupedResult.forEach((labels, mqeValuesList) -> {
            if (mqeValuesList.isEmpty()) {
                return;
            }
            List<MQEValue> combineTo = mqeValuesList.get(0).getValues();
            for (int i = 0; i < combineTo.size(); i++) {
                AggregateLabelsFunc aggregateLabelsFunc = factory.getAggregateLabelsFunc();
                for (MQEValues mqeValues : mqeValuesList) {
                    MQEValue toCombine = mqeValues.getValues().get(i);
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
            Metadata metadata = new Metadata();
            metadata.setLabels(labels);
            mqeValues.setMetric(metadata);
            mqeValues.setValues(combineTo);
            expResult.getResults().add(mqeValues);
        });
        return expResult;
    }

    private static List<KeyValue> getLabels(final List<String> labelNames, final MQEValues mqeValues) {
        List<KeyValue> a =
         labelNames.stream().map(labelName -> mqeValues.getMetric().getLabels().stream().filter(label -> labelName.equals(label.getKey()))
                                                           .findAny().orElseGet(() -> new KeyValue(labelName, ""))).collect(toList());
        return a;
    }

}
