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
import org.apache.skywalking.mqe.rt.operation.SortValuesOp;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SortValuesOpTest {
    private final MockData mockData = new MockData();

    @Test
    public void sortValueTest() throws IllegalExpressionException {
        //no label
        ExpressionResult des = SortValuesOp.doSortValuesOp(
            mockData.newSeriesNoLabeledResult(), 3,
            MQEParser.DES, MQEParser.AVG
        );
        Assertions.assertEquals(100, des.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(300, des.getResults().get(0).getValues().get(1).getDoubleValue());
        ExpressionResult asc = SortValuesOp.doSortValuesOp(
            mockData.newSeriesNoLabeledResult(), 3,
            MQEParser.ASC, MQEParser.AVG
        );
        Assertions.assertEquals(100, asc.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(300, asc.getResults().get(0).getValues().get(1).getDoubleValue());

        //labeled
        ExpressionResult desLabeled = SortValuesOp.doSortValuesOp(
            mockData.newSeriesLabeledResult(), 3,
            MQEParser.DES, MQEParser.AVG
        );
        Assertions.assertEquals(101, desLabeled.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(301, desLabeled.getResults().get(0).getValues().get(1).getDoubleValue());
        Assertions.assertEquals("label", desLabeled.getResults().get(0).getMetric().getLabels().get(0).getKey());
        Assertions.assertEquals("2", desLabeled.getResults().get(0).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("label2", desLabeled.getResults().get(0).getMetric().getLabels().get(1).getKey());
        Assertions.assertEquals("21", desLabeled.getResults().get(0).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals(100, desLabeled.getResults().get(1).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(300, desLabeled.getResults().get(1).getValues().get(1).getDoubleValue());
        Assertions.assertEquals("label", desLabeled.getResults().get(1).getMetric().getLabels().get(0).getKey());
        Assertions.assertEquals("1", desLabeled.getResults().get(1).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("label2", desLabeled.getResults().get(1).getMetric().getLabels().get(1).getKey());
        Assertions.assertEquals("21", desLabeled.getResults().get(1).getMetric().getLabels().get(1).getValue());

        ExpressionResult ascLabeled = SortValuesOp.doSortValuesOp(
            mockData.newSeriesLabeledResult(), 3,
            MQEParser.ASC, MQEParser.AVG
        );
        Assertions.assertEquals(100, ascLabeled.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(300, ascLabeled.getResults().get(0).getValues().get(1).getDoubleValue());
        Assertions.assertEquals("label", ascLabeled.getResults().get(0).getMetric().getLabels().get(0).getKey());
        Assertions.assertEquals("1", ascLabeled.getResults().get(0).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("label2", ascLabeled.getResults().get(0).getMetric().getLabels().get(1).getKey());
        Assertions.assertEquals("21", ascLabeled.getResults().get(0).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals(101, ascLabeled.getResults().get(1).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(301, ascLabeled.getResults().get(1).getValues().get(1).getDoubleValue());
        Assertions.assertEquals("label", ascLabeled.getResults().get(1).getMetric().getLabels().get(0).getKey());
        Assertions.assertEquals("2", ascLabeled.getResults().get(1).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("label2", ascLabeled.getResults().get(1).getMetric().getLabels().get(1).getKey());
        Assertions.assertEquals("21", ascLabeled.getResults().get(1).getMetric().getLabels().get(1).getValue());

        //limit
        ExpressionResult desLabeledLimit = SortValuesOp.doSortValuesOp(
            mockData.newSeriesLabeledResult(), 1,
            MQEParser.DES, MQEParser.AVG
        );
        Assertions.assertEquals(101, desLabeledLimit.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(301, desLabeledLimit.getResults().get(0).getValues().get(1).getDoubleValue());
        Assertions.assertEquals("label", desLabeledLimit.getResults().get(0).getMetric().getLabels().get(0).getKey());
        Assertions.assertEquals("2", desLabeledLimit.getResults().get(0).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("label2", desLabeledLimit.getResults().get(0).getMetric().getLabels().get(1).getKey());
        Assertions.assertEquals("21", desLabeledLimit.getResults().get(0).getMetric().getLabels().get(1).getValue());
    }
}
