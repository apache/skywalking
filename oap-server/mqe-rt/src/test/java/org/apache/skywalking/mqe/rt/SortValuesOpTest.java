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

import java.util.Optional;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.operation.SortValuesOp;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SortValuesOpTest {
    private final MockData mockData = new MockData();

    @Test
    public void sortValueTest() throws IllegalExpressionException {
        //no label
        ExpressionResult des = SortValuesOp.doSortValuesOp(mockData.newSeriesNoLabeledResult(), Optional.of(3),
                                                          MQEParser.DES);
        Assertions.assertEquals(300, des.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(100, des.getResults().get(0).getValues().get(1).getDoubleValue());
        ExpressionResult asc = SortValuesOp.doSortValuesOp(mockData.newSeriesNoLabeledResult(), Optional.of(3),
                                                          MQEParser.ASC);
        Assertions.assertEquals(100, asc.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(300, asc.getResults().get(0).getValues().get(1).getDoubleValue());

        //labeled
        ExpressionResult desLabeled = SortValuesOp.doSortValuesOp(mockData.newSeriesLabeledResult(), Optional.of(3),
                                                                 MQEParser.DES);
        Assertions.assertEquals(300, desLabeled.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(100, desLabeled.getResults().get(0).getValues().get(1).getDoubleValue());
        Assertions.assertEquals(301, desLabeled.getResults().get(1).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(101, desLabeled.getResults().get(1).getValues().get(1).getDoubleValue());
        ExpressionResult ascLabeled = SortValuesOp.doSortValuesOp(mockData.newSeriesLabeledResult(), Optional.of(2),
                                                                 MQEParser.ASC);
        Assertions.assertEquals(100, ascLabeled.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(300, ascLabeled.getResults().get(0).getValues().get(1).getDoubleValue());
        Assertions.assertEquals(101, ascLabeled.getResults().get(1).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(301, ascLabeled.getResults().get(1).getValues().get(1).getDoubleValue());

        //limit
        ExpressionResult desLabeledLimit = SortValuesOp.doSortValuesOp(mockData.newSeriesLabeledResult(), Optional.of(1),
                                                                      MQEParser.DES);
        Assertions.assertEquals(1, desLabeledLimit.getResults().get(0).getValues().size());
        Assertions.assertEquals(1, desLabeledLimit.getResults().get(1).getValues().size());
        Assertions.assertEquals(300, desLabeledLimit.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals(301, desLabeledLimit.getResults().get(1).getValues().get(0).getDoubleValue());
    }
}
