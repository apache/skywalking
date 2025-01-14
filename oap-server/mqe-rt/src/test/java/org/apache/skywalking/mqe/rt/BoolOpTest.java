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

package org.apache.skywalking.mqe.rt;

import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.operation.BoolOp;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResultType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BoolOpTest {
    private final MockData mockData = new MockData();

    @Test
    public void boolOpNoLabeledTest() throws IllegalExpressionException {
        ExpressionResult left = mockData.newSingleResult(1);
        left.setBoolResult(true);
        ExpressionResult right = mockData.newSingleResult(0);
        right.getResults().get(0).getValues().get(0).setEmptyValue(false);
        right.setBoolResult(true);
        ExpressionResult andResult = BoolOp.doBoolOp(left, right, MQEParser.AND);
        Assertions.assertEquals(ExpressionResultType.SINGLE_VALUE, andResult.getType());

        Assertions.assertEquals(0, andResult.getResults().get(0).getValues().get(0).getDoubleValue());
        left = mockData.newSingleResult(1);
        left.setBoolResult(true);
        right = mockData.newSingleResult(0);
        right.getResults().get(0).getValues().get(0).setEmptyValue(false);
        right.setBoolResult(true);
        ExpressionResult orResult = BoolOp.doBoolOp(left, right, MQEParser.OR);
        Assertions.assertEquals(1, orResult.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(ExpressionResultType.SINGLE_VALUE, orResult.getType());
    }

    @Test
    public void boolOpLabeledTest() throws IllegalExpressionException {
        ExpressionResult left = mockData.newSingleLabeledResult(1, 0);
        left.setBoolResult(true);
        left.getResults().get(1).getValues().get(0).setEmptyValue(false);

        ExpressionResult right = mockData.newSingleLabeledResult(0, 1);
        right.getResults().get(0).getValues().get(0).setEmptyValue(false);
        right.setBoolResult(true);

        ExpressionResult andResult = BoolOp.doBoolOp(left, right, MQEParser.AND);

        Assertions.assertEquals(0, andResult.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(0, andResult.getResults().get(1).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(ExpressionResultType.SINGLE_VALUE, andResult.getType());

        left = mockData.newSingleLabeledResult(1, 0);
        left.setBoolResult(true);
        left.getResults().get(1).getValues().get(0).setEmptyValue(false);

        right = mockData.newSingleLabeledResult(0, 1);
        right.getResults().get(0).getValues().get(0).setEmptyValue(false);
        right.setBoolResult(true);
        ExpressionResult orResult = BoolOp.doBoolOp(left, right, MQEParser.OR);
        Assertions.assertEquals(1, orResult.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(1, orResult.getResults().get(1).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(ExpressionResultType.SINGLE_VALUE, orResult.getType());
    }
}
