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
import org.apache.skywalking.mqe.rt.operation.BinaryOp;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResultType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BinaryOpTest {
    private final MockData mockData = new MockData();

    //DIV/MUL/MOD/SUB... are the same logic and tested in here, the others only test ADD is enough.
    @Test
    public void seriesNoLabeledTest() throws Exception {
        assertThrows(IllegalExpressionException.class, () -> BinaryOp.doBinaryOp(
            mockData.newEmptyResult(ExpressionResultType.TIME_SERIES_VALUES, false),
            mockData.newEmptyResult(ExpressionResultType.TIME_SERIES_VALUES, false), MQEParser.ADD
        ));
        ExpressionResult add = BinaryOp.doBinaryOp(
            mockData.newSeriesNoLabeledResult(), mockData.newSeriesNoLabeledResult(), MQEParser.ADD);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, add.getType());
        assertEquals("100", add.getResults().get(0).getValues().get(0).getId());
        assertEquals(200, add.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", add.getResults().get(0).getValues().get(1).getId());
        assertEquals(600, add.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult sub = BinaryOp.doBinaryOp(
            mockData.newSeriesNoLabeledResult(), mockData.newSeriesNoLabeledResult(), MQEParser.SUB);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, sub.getType());
        assertEquals("100", sub.getResults().get(0).getValues().get(0).getId());
        assertEquals(0, sub.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", sub.getResults().get(0).getValues().get(1).getId());
        assertEquals(0, sub.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult mul = BinaryOp.doBinaryOp(
            mockData.newSeriesNoLabeledResult(), mockData.newSeriesNoLabeledResult(), MQEParser.MUL);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, mul.getType());
        assertEquals("100", mul.getResults().get(0).getValues().get(0).getId());
        assertEquals(10000, mul.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", mul.getResults().get(0).getValues().get(1).getId());
        assertEquals(90000, mul.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult div = BinaryOp.doBinaryOp(
            mockData.newSeriesNoLabeledResult(), mockData.newSeriesNoLabeledResult(), MQEParser.DIV);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, div.getType());
        assertEquals("100", div.getResults().get(0).getValues().get(0).getId());
        assertEquals(1, div.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", div.getResults().get(0).getValues().get(1).getId());
        assertEquals(1, div.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult mod = BinaryOp.doBinaryOp(
            mockData.newSeriesNoLabeledResult(), mockData.newSeriesNoLabeledResult(), MQEParser.MOD);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, mod.getType());
        assertEquals("100", mod.getResults().get(0).getValues().get(0).getId());
        assertEquals(0, mod.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", mod.getResults().get(0).getValues().get(1).getId());
        assertEquals(0, mod.getResults().get(0).getValues().get(1).getDoubleValue());
    }

    @Test
    public void seriesLabeledTest() throws Exception {
        assertThrows(IllegalExpressionException.class, () -> BinaryOp.doBinaryOp(
            mockData.newSeriesLabeledResult(),
            mockData.newEmptyResult(ExpressionResultType.TIME_SERIES_VALUES, false), MQEParser.ADD
        ));
        //seriesLabeled + seriesNoLabeled
        ExpressionResult add = BinaryOp.doBinaryOp(mockData.newSeriesLabeledResult(), mockData.newSeriesNoLabeledResult(), MQEParser.ADD);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, add.getType());
        //label=1, label2=21
        assertEquals("1", add.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals("100", add.getResults().get(0).getValues().get(0).getId());
        assertEquals(200, add.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", add.getResults().get(0).getValues().get(1).getId());
        assertEquals(600, add.getResults().get(0).getValues().get(1).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", add.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals("100", add.getResults().get(1).getValues().get(0).getId());
        assertEquals(201, add.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("300", add.getResults().get(1).getValues().get(1).getId());
        assertEquals(601, add.getResults().get(1).getValues().get(1).getDoubleValue());

        //seriesLabeled + seriesLabeled
        add = BinaryOp.doBinaryOp(mockData.newSeriesLabeledResult(), mockData.newSeriesLabeledResult(), MQEParser.ADD);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, add.getType());
        //label=1, label2=21
        assertEquals("1", add.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals("100", add.getResults().get(0).getValues().get(0).getId());
        assertEquals(200, add.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", add.getResults().get(0).getValues().get(1).getId());
        assertEquals(600, add.getResults().get(0).getValues().get(1).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", add.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals("100", add.getResults().get(1).getValues().get(0).getId());
        assertEquals(202, add.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("300", add.getResults().get(1).getValues().get(1).getId());
        assertEquals(602, add.getResults().get(1).getValues().get(1).getDoubleValue());

        //seriesNoLabeled - seriesLabeled
        add = BinaryOp.doBinaryOp(mockData.newSeriesNoLabeledResult(), mockData.newSeriesLabeledResult(), MQEParser.SUB);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, add.getType());
        //label=1, label2=21
        assertEquals("1", add.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals("100", add.getResults().get(0).getValues().get(0).getId());
        assertEquals(0, add.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", add.getResults().get(0).getValues().get(1).getId());
        assertEquals(0, add.getResults().get(0).getValues().get(1).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", add.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals("100", add.getResults().get(1).getValues().get(0).getId());
        assertEquals(-1, add.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("300", add.getResults().get(1).getValues().get(1).getId());
        assertEquals(-1, add.getResults().get(1).getValues().get(1).getDoubleValue());
    }

    @Test
    public void many2OneTest() throws Exception {
        assertThrows(IllegalExpressionException.class, () -> BinaryOp.doBinaryOp(
            mockData.newEmptyResult(ExpressionResultType.TIME_SERIES_VALUES, false),
            mockData.newEmptyResult(ExpressionResultType.SINGLE_VALUE, false), MQEParser.ADD
        ));
        assertThrows(IllegalExpressionException.class, () -> BinaryOp.doBinaryOp(
            mockData.newEmptyResult(ExpressionResultType.TIME_SERIES_VALUES, true),
            mockData.newEmptyResult(ExpressionResultType.SINGLE_VALUE, false), MQEParser.ADD
        ));
        assertThrows(IllegalExpressionException.class, () -> BinaryOp.doBinaryOp(
            mockData.newEmptyResult(ExpressionResultType.TIME_SERIES_VALUES, false),
            mockData.newEmptyResult(ExpressionResultType.SINGLE_VALUE, true), MQEParser.ADD
        ));
        //sort_list + single
        ExpressionResult add = BinaryOp.doBinaryOp(
            mockData.newListResult(), mockData.newSingleResult(1000), MQEParser.ADD);
        assertEquals(ExpressionResultType.SORTED_LIST, add.getType());
        assertEquals("service_A", add.getResults().get(0).getValues().get(0).getId());
        assertEquals(1100, add.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("service_B", add.getResults().get(0).getValues().get(1).getId());
        assertEquals(1300, add.getResults().get(0).getValues().get(1).getDoubleValue());

        //seriesNoLabeled + single
        add = BinaryOp.doBinaryOp(mockData.newSeriesNoLabeledResult(), mockData.newSingleResult(1000), MQEParser.ADD);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, add.getType());
        assertEquals("100", add.getResults().get(0).getValues().get(0).getId());
        assertEquals(1100, add.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", add.getResults().get(0).getValues().get(1).getId());
        assertEquals(1300, add.getResults().get(0).getValues().get(1).getDoubleValue());

        //seriesLabeled + single
        add = BinaryOp.doBinaryOp(mockData.newSeriesLabeledResult(), mockData.newSingleResult(1000), MQEParser.ADD);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, add.getType());
        //label=1, label2=21
        assertEquals("1", add.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals("100", add.getResults().get(0).getValues().get(0).getId());
        assertEquals(1100, add.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", add.getResults().get(0).getValues().get(1).getId());
        assertEquals(1300, add.getResults().get(0).getValues().get(1).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", add.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals("100", add.getResults().get(1).getValues().get(0).getId());
        assertEquals(1101, add.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("300", add.getResults().get(1).getValues().get(1).getId());
        assertEquals(1301, add.getResults().get(1).getValues().get(1).getDoubleValue());
    }

    @Test
    public void one2ManyTest() throws Exception {
        assertThrows(IllegalExpressionException.class, () -> BinaryOp.doBinaryOp(
            mockData.newEmptyResult(ExpressionResultType.SINGLE_VALUE, false),
            mockData.newEmptyResult(ExpressionResultType.TIME_SERIES_VALUES, false), MQEParser.ADD
        ));
        assertThrows(IllegalExpressionException.class, () -> BinaryOp.doBinaryOp(
            mockData.newEmptyResult(ExpressionResultType.SINGLE_VALUE, false),
            mockData.newEmptyResult(ExpressionResultType.TIME_SERIES_VALUES, true), MQEParser.ADD
        ));
        // single - sort_list
        ExpressionResult sub = BinaryOp.doBinaryOp(
            mockData.newSingleResult(1000), mockData.newListResult(), MQEParser.SUB);
        assertEquals(ExpressionResultType.SORTED_LIST, sub.getType());
        assertEquals("service_A", sub.getResults().get(0).getValues().get(0).getId());
        assertEquals(900, sub.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("service_B", sub.getResults().get(0).getValues().get(1).getId());
        assertEquals(700, sub.getResults().get(0).getValues().get(1).getDoubleValue());

        //single - seriesNoLabeled
        sub = BinaryOp.doBinaryOp(mockData.newSingleResult(1000), mockData.newSeriesNoLabeledResult(), MQEParser.SUB);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, sub.getType());
        assertEquals("100", sub.getResults().get(0).getValues().get(0).getId());
        assertEquals(900, sub.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", sub.getResults().get(0).getValues().get(1).getId());
        assertEquals(700, sub.getResults().get(0).getValues().get(1).getDoubleValue());

        //single - seriesLabeled
        sub = BinaryOp.doBinaryOp(mockData.newSingleResult(1000), mockData.newSeriesLabeledResult(), MQEParser.SUB);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, sub.getType());
        //label=1, label2=21
        assertEquals("1", sub.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", sub.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals("100", sub.getResults().get(0).getValues().get(0).getId());
        assertEquals(900, sub.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", sub.getResults().get(0).getValues().get(1).getId());
        assertEquals(700, sub.getResults().get(0).getValues().get(1).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", sub.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", sub.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals("100", sub.getResults().get(1).getValues().get(0).getId());
        assertEquals(899, sub.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("300", sub.getResults().get(1).getValues().get(1).getId());
        assertEquals(699, sub.getResults().get(1).getValues().get(1).getDoubleValue());
    }

    @Test
    public void single2SingleTest() throws IllegalExpressionException {
        assertThrows(IllegalExpressionException.class, () -> BinaryOp.doBinaryOp(
            mockData.newEmptyResult(ExpressionResultType.SINGLE_VALUE, false),
            mockData.newEmptyResult(ExpressionResultType.SINGLE_VALUE, false), MQEParser.ADD
        ));
        assertThrows(IllegalExpressionException.class, () -> BinaryOp.doBinaryOp(
            mockData.newEmptyResult(ExpressionResultType.SINGLE_VALUE, false),
            mockData.newEmptyResult(ExpressionResultType.SINGLE_VALUE, true), MQEParser.ADD
        ));

        //noLabeled + noLabeled
        ExpressionResult add = BinaryOp.doBinaryOp(
            mockData.newSingleResult(100), mockData.newSingleResult(200), MQEParser.ADD);
        assertEquals(ExpressionResultType.SINGLE_VALUE, add.getType());
        assertEquals(300, add.getResults().get(0).getValues().get(0).getDoubleValue());

        //labeled + noLabeled
        add = BinaryOp.doBinaryOp(
            mockData.newSingleLabeledResult(100, 200), mockData.newSingleResult(100), MQEParser.ADD);
        assertEquals(ExpressionResultType.SINGLE_VALUE, add.getType());
        //label=1, label2=21
        assertEquals("1", add.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals(200, add.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", add.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals(300, add.getResults().get(1).getValues().get(0).getDoubleValue());

        //nolabeled + labeled
        add = BinaryOp.doBinaryOp(
            mockData.newSingleResult(100), mockData.newSingleLabeledResult(100, 200), MQEParser.ADD);
        assertEquals(ExpressionResultType.SINGLE_VALUE, add.getType());
        //label=1, label2=21
        assertEquals("1", add.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals(200, add.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", add.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", add.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals(300, add.getResults().get(1).getValues().get(0).getDoubleValue());

        //labeled + labeled
        add = BinaryOp.doBinaryOp(
            mockData.newSingleLabeledResult(100, 102), mockData.newSingleLabeledResult(100, 200), MQEParser.ADD);
        assertEquals(ExpressionResultType.SINGLE_VALUE, add.getType());
        //label=1, label2=21
        assertEquals("1", add.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals(200, add.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", add.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals(302, add.getResults().get(1).getValues().get(0).getDoubleValue());
    }
}
