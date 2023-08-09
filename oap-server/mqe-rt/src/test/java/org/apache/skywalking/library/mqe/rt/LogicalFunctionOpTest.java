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

package org.apache.skywalking.library.mqe.rt;

import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.grammar.MQEParserBaseVisitor;
import org.apache.skywalking.mqe.rt.operation.LogicalFunctionOp;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class LogicalFunctionOpTest {
    private final MockData mockData = new MockData();

    @Test
    public void viewAsSeqTest() throws Exception {
        MQEParser.ExpressionListContext expressions = Mockito.mock(MQEParser.ExpressionListContext.class);
        MQEParser.ExpressionContext emptyExpression = Mockito.mock(MQEParser.ExpressionContext.class);
        MQEParser.ExpressionContext notEmptyExpression = Mockito.mock(MQEParser.ExpressionContext.class);
        Mockito.when(expressions.expression()).thenReturn(Arrays.asList(emptyExpression, notEmptyExpression));
        final MQEParserBaseVisitor<ExpressionResult> visitor = Mockito.mock(MQEParserBaseVisitor.class);
        final ExpressionResult emptyResult = mockData.newSeriesNoLabeledResult(0, 0);
        Mockito.when(visitor.visit(emptyExpression)).thenReturn(emptyResult);
        final ExpressionResult notEmptyResult = mockData.newSeriesNoLabeledResult(100, 200);
        Mockito.when(visitor.visit(notEmptyExpression)).thenReturn(notEmptyResult);

        ExpressionResult result;
        result = LogicalFunctionOp.doOP(MQEParser.VIEW_AS_SEQ, expressions, visitor);
        assertEquals(notEmptyResult, result);

        Mockito.when(expressions.expression()).thenReturn(Arrays.asList(emptyExpression, emptyExpression));
        result = LogicalFunctionOp.doOP(MQEParser.VIEW_AS_SEQ, expressions, visitor);
        assertEquals(emptyResult, result);
    }

}
