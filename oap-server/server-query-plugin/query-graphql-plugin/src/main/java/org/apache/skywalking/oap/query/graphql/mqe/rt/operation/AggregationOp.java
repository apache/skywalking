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

import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.oap.query.graphql.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResult;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResultType;
import org.apache.skywalking.oap.query.graphql.type.mql.MQEValue;
import org.apache.skywalking.oap.query.graphql.type.mql.MQEValues;

public class AggregationOp {
    public static ExpressionResult doAggregationOp(ExpressionResult result,
                                                   int opType) throws IllegalExpressionException {
        switch (opType) {
            case MQEParser.AVG:
                return aggregateResult(result, mqeValues -> mqeValues.getValues()
                                                                     .stream()
                                                                     .filter(mqeValue -> !mqeValue.isEmptyValue())
                                                                     .flatMapToDouble(mqeValue -> DoubleStream.of(
                                                                         mqeValue.getDoubleValue()))
                                                                     .average());
            case MQEParser.COUNT:
                return aggregateResult(result, mqeValues -> OptionalDouble.of(
                    mqeValues.getValues().stream().filter(mqeValue -> !mqeValue.isEmptyValue()).count()));
            case MQEParser.LATEST:
                if (result.getType() != ExpressionResultType.TIME_SERIES_VALUES) {
                    throw new IllegalExpressionException("LATEST can only be used in time series result.");
                }
                return selectResult(result, mqeValues -> Streams.findLast(mqeValues.getValues()
                                                                                   .stream()
                                                                                   .filter(mqeValue -> !mqeValue.isEmptyValue())));
            case MQEParser.MAX:
                return selectResult(result, mqeValues -> mqeValues.getValues()
                                                                  .stream()
                                                                  .filter(mqeValue -> !mqeValue.isEmptyValue())
                                                                  .max(Comparator.comparingDouble(
                                                                      MQEValue::getDoubleValue)));
            case MQEParser.MIN:
                return selectResult(result, mqeValues -> mqeValues.getValues()
                                                                  .stream()
                                                                  .filter(mqeValue -> !mqeValue.isEmptyValue())
                                                                  .min(Comparator.comparingDouble(
                                                                      MQEValue::getDoubleValue)));
            case MQEParser.SUM:
                return aggregateResult(result, mqeValues -> OptionalDouble.of(mqeValues.getValues()
                                                                                       .stream()
                                                                                       .filter(
                                                                                           mqeValue -> !mqeValue.isEmptyValue())
                                                                                       .flatMapToDouble(
                                                                                           mqeValue -> DoubleStream.of(
                                                                                               mqeValue.getDoubleValue()))
                                                                                       .sum()));
            default:
                throw new IllegalExpressionException("Unsupported aggregation operation.");
        }
    }

    private static ExpressionResult aggregateResult(ExpressionResult result,
                                                    Function<MQEValues, OptionalDouble> aggregator) {
        for (MQEValues resultValues : result.getResults()) {
            OptionalDouble resultValue = aggregator.apply(resultValues);
            if (resultValue.isPresent()) {
                List<MQEValue> mqeValueList = new ArrayList<>(1);
                //no id
                MQEValue mqeValue = new MQEValue();
                mqeValue.setEmptyValue(false);
                mqeValue.setDoubleValue(resultValue.getAsDouble());
                mqeValueList.add(mqeValue);
                resultValues.setValues(mqeValueList);
            } else {
                resultValues.setValues(Collections.emptyList());
            }
        }
        result.setType(ExpressionResultType.SINGLE_VALUE);
        return result;
    }

    private static ExpressionResult selectResult(ExpressionResult result,
                                                 Function<MQEValues, Optional<MQEValue>> aggregator) {
        for (MQEValues resultValues : result.getResults()) {
            Optional<MQEValue> resultValue = aggregator.apply(resultValues);
            if (resultValue.isPresent()) {
                List<MQEValue> mqeValueList = new ArrayList<>(1);
                mqeValueList.add(resultValue.get());
                resultValues.setValues(mqeValueList);
            } else {
                resultValues.setValues(Collections.emptyList());
            }
        }
        result.setType(ExpressionResultType.SINGLE_VALUE);
        return result;
    }
}
