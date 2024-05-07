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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.MQEValue;

public class SortValuesOp {
    public static ExpressionResult doSortValuesOp(ExpressionResult expResult,
                                                 Optional<Integer> limit,
                                                 int order) throws IllegalExpressionException {
        if (MQEParser.ASC == order || MQEParser.DES == order) {
            expResult.getResults().forEach(mqeValues -> {
                List<MQEValue> values = mqeValues.getValues()
                                                 .stream()
                                                 // Filter out empty values
                                                 .filter(mqeValue -> !mqeValue.isEmptyValue())
                                                 .sorted(MQEParser.ASC == order ? Comparator.comparingDouble(
                                                     MQEValue::getDoubleValue) :
                                                             Comparator.comparingDouble(MQEValue::getDoubleValue)
                                                                       .reversed())
                                                 .collect(
                                                     Collectors.toList());
                if (limit.isPresent() && limit.get() < values.size()) {
                    mqeValues.setValues(values.subList(0, limit.get()));
                } else {
                    mqeValues.setValues(values);
                }
            });
        } else {
            throw new IllegalExpressionException("Unsupported sort order.");
        }
        return expResult;
    }
}
