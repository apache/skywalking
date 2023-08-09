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
import org.apache.skywalking.mqe.rt.grammar.MQEParserBaseVisitor;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

public class LogicalFunctionOp {

    public static ExpressionResult doOP(int opType, MQEParser.ExpressionListContext expressionListContext,
                                                       MQEParserBaseVisitor<ExpressionResult> visitor) throws IllegalExpressionException {
        switch (opType) {
            case MQEParser.VIEW_AS_SEQ:
                return viewAsSeq(expressionListContext, visitor);
        }

        throw new IllegalExpressionException("Unsupported function.");
    }

    private static ExpressionResult viewAsSeq(MQEParser.ExpressionListContext expressionListContext,
                                              MQEParserBaseVisitor<ExpressionResult> visitor) {
        ExpressionResult firstResult = null;
        for (MQEParser.ExpressionContext expContext : expressionListContext.expression()) {
            final ExpressionResult result = visitor.visit(expContext);
            if (firstResult == null) {
                firstResult = result;
            }
            if (result == null || CollectionUtils.isEmpty(result.getResults())) {
                continue;
            }
            final boolean isNotEmptyValue = result.getResults().stream()
                .filter(s -> s != null && CollectionUtils.isNotEmpty(s.getValues()))
                .flatMap(s -> s.getValues().stream()).anyMatch(s -> !s.isEmptyValue());
            if (isNotEmptyValue) {
                return result;
            }
        }
        return firstResult;
    }

}
