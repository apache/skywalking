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

import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;

public class BinaryOp {
    public static ExpressionResult doBinaryOp(ExpressionResult left,
                                              ExpressionResult right,
                                              int opType) throws IllegalExpressionException {
        try {
            return LROp.doLROp(left, right, opType, BinaryOp::scalarBinaryOp);
        } catch (IllegalExpressionException e) {
            throw new IllegalExpressionException("Unsupported binary operation: " + e.getMessage());
        }
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
}
