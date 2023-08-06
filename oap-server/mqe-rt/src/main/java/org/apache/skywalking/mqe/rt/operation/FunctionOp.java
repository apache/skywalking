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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Function;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;

public class FunctionOp {
    public static ExpressionResult doFunction0Op(ExpressionResult expResult,
                                                 int opType) throws IllegalExpressionException {
        switch (opType) {
            case MQEParser.ABS:
                return FunctionOp.transResult(expResult, Math::abs);
            case MQEParser.CEIL:
                return FunctionOp.transResult(expResult, Math::ceil);
            case MQEParser.FLOOR:
                return FunctionOp.transResult(expResult, Math::floor);
        }

        throw new IllegalExpressionException("Unsupported function.");
    }

    public static ExpressionResult doFunction1Op(ExpressionResult expResult,
                                                 int opType,
                                                 int scale) throws IllegalExpressionException {
        switch (opType) {
            case MQEParser.ROUND:
                return FunctionOp.transResult(expResult, aDouble -> {
                    BigDecimal bd = BigDecimal.valueOf(aDouble);
                    return bd.setScale(scale, RoundingMode.HALF_UP).doubleValue();
                });
        }

        throw new IllegalExpressionException("Unsupported function.");
    }

    private static ExpressionResult transResult(ExpressionResult expResult, Function<Double, Double> calculator) {
        expResult.getResults().forEach(resultValues -> {
            resultValues.getValues().forEach(mqeValue -> {
                if (!mqeValue.isEmptyValue()) {
                    double newValue = calculator.apply(mqeValue.getDoubleValue());
                    mqeValue.setDoubleValue(newValue);
                }
            });
        });
        return expResult;
    }
}
