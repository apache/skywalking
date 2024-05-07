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

import java.util.List;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.operation.SortLabelValuesOp;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SortLabelValuesOpTest {
    private final MockData mockData = new MockData();

    @Test
    public void sortLabelValueTest() throws IllegalExpressionException {
        //des
        ExpressionResult des = SortLabelValuesOp.doSortLabelValuesOp(mockData.newSeriesComplexLabeledResult(),
                                                                    MQEParser.DES, List.of("label", "label2", "label3")
        );
        assertDes(des);
        //asc
        ExpressionResult asc = SortLabelValuesOp.doSortLabelValuesOp(des,
                                                                    MQEParser.ASC, List.of("label", "label2", "label3")
        );
        assertAsc(asc);
        // distinct
        ExpressionResult distinctDes = SortLabelValuesOp.doSortLabelValuesOp(mockData.newSeriesComplexLabeledResult(),
                                                                            MQEParser.DES, List.of("label", "label2", "label3", "label")
        );
        assertDes(distinctDes);
        // ignore
        ExpressionResult ignoreDes = SortLabelValuesOp.doSortLabelValuesOp(mockData.newSeriesComplexLabeledResult(),
                                                                          MQEParser.DES, List.of("label", "labelIgnore", "label2", "label3")
        );
        assertDes(ignoreDes);
    }

    private void assertDes(ExpressionResult des) {
        //label
        Assertions.assertEquals("b", des.getResults().get(0).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("b", des.getResults().get(1).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("b", des.getResults().get(2).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("b", des.getResults().get(3).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("a", des.getResults().get(4).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("a", des.getResults().get(5).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("a", des.getResults().get(6).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("a", des.getResults().get(7).getMetric().getLabels().get(0).getValue());
        // label2
        Assertions.assertEquals("2b", des.getResults().get(0).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2b", des.getResults().get(1).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2a", des.getResults().get(2).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2a", des.getResults().get(3).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2b", des.getResults().get(4).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2b", des.getResults().get(5).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2a", des.getResults().get(6).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2a", des.getResults().get(7).getMetric().getLabels().get(1).getValue());
        // label3
        Assertions.assertEquals("32", des.getResults().get(0).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("31", des.getResults().get(1).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("32", des.getResults().get(2).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("31", des.getResults().get(3).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("32", des.getResults().get(4).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("31", des.getResults().get(5).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("32", des.getResults().get(6).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("31", des.getResults().get(7).getMetric().getLabels().get(2).getValue());
    }

    private void assertAsc(ExpressionResult asc) {
        //label
        Assertions.assertEquals("a", asc.getResults().get(0).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("a", asc.getResults().get(1).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("a", asc.getResults().get(2).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("a", asc.getResults().get(3).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("b", asc.getResults().get(4).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("b", asc.getResults().get(5).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("b", asc.getResults().get(6).getMetric().getLabels().get(0).getValue());
        Assertions.assertEquals("b", asc.getResults().get(7).getMetric().getLabels().get(0).getValue());
        // label2
        Assertions.assertEquals("2a", asc.getResults().get(0).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2a", asc.getResults().get(1).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2b", asc.getResults().get(2).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2b", asc.getResults().get(3).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2a", asc.getResults().get(4).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2a", asc.getResults().get(5).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2b", asc.getResults().get(6).getMetric().getLabels().get(1).getValue());
        Assertions.assertEquals("2b", asc.getResults().get(7).getMetric().getLabels().get(1).getValue());
        // label3
        Assertions.assertEquals("31", asc.getResults().get(0).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("32", asc.getResults().get(1).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("31", asc.getResults().get(2).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("32", asc.getResults().get(3).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("31", asc.getResults().get(4).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("32", asc.getResults().get(5).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("31", asc.getResults().get(6).getMetric().getLabels().get(2).getValue());
        Assertions.assertEquals("32", asc.getResults().get(7).getMetric().getLabels().get(2).getValue());
    }
}
