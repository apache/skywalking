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
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;

public class BoolOp {
    public static ExpressionResult doBoolOp(ExpressionResult left,
                                            ExpressionResult right,
                                            int opType) throws IllegalExpressionException {
        if (!checkExpression(left)) {
            throw new IllegalExpressionException(
                "Bool Operation: The result of the left expression is not a compare result.");
        }

        if (!checkExpression(right)) {
            throw new IllegalExpressionException(
                "Bool Operation: The result of the right expression is not a compare result.");
        }

        try {
            return LROp.doLROp(left, right, opType, BoolOp::boolOp);
        } catch (IllegalExpressionException e) {
            throw new IllegalExpressionException("Unsupported bool operation: " + e.getMessage());
        }
    }

    //bool with bool
    private static double boolOp(double leftValue, double rightValue, int opType) throws IllegalExpressionException {
        // this should not happen, but just in case
        if (!checkBool(leftValue)) {
            throw new IllegalExpressionException("Bool Operation: The result of the left expression is not 1 or 0.");
        }
        if (!checkBool(rightValue)) {
            throw new IllegalExpressionException("Bool Operation: The result of the right expression is not 1 or 0.");
        }
        switch (opType) {
            case MQEParser.AND:
                if (leftValue == 1 && rightValue == 1) {
                    return 1;
                } else {
                    return 0;
                }
            case MQEParser.OR:
                if (leftValue == 1 || rightValue == 1) {
                    return 1;
                } else {
                    return 0;
                }
            default:
                throw new IllegalExpressionException("Unsupported bool operation.");
        }
    }

    private static boolean checkExpression(ExpressionResult result) {
        return result.isBoolResult();
    }

    private static boolean checkBool(double v) {
        int i = (int) v;
        return i == 0 || i == 1;
    }
}
