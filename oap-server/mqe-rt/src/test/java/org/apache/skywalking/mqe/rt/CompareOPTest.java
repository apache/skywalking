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
import org.apache.skywalking.mqe.rt.operation.CompareOp;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.ExpressionResultType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompareOPTest {
    private final MockData mockData = new MockData();

    //GT/GTE/LT/LTE... are the same logic and tested in here, the others only test GT is enough.
    @Test
    public void seriesNoLabeledTest() throws Exception {
        ExpressionResult gt = CompareOp.doCompareOP(
            mockData.newSeriesNoLabeledResult(100, 200), mockData.newSeriesNoLabeledResult(200, 100), MQEParser.GT);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, gt.getType());
        assertEquals(0, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(1, gt.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult gte = CompareOp.doCompareOP(
            mockData.newSeriesNoLabeledResult(200, 200), mockData.newSeriesNoLabeledResult(200, 100), MQEParser.GTE);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, gte.getType());
        assertEquals(1, gte.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(1, gte.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult lt = CompareOp.doCompareOP(
            mockData.newSeriesNoLabeledResult(200, 200), mockData.newSeriesNoLabeledResult(200, 300), MQEParser.LT);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, lt.getType());
        assertEquals(0, lt.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(1, lt.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult lte = CompareOp.doCompareOP(
            mockData.newSeriesNoLabeledResult(200, 200), mockData.newSeriesNoLabeledResult(200, 100), MQEParser.LTE);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, lte.getType());
        assertEquals(1, lte.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(0, lte.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult neq = CompareOp.doCompareOP(
            mockData.newSeriesNoLabeledResult(200, 200), mockData.newSeriesNoLabeledResult(200, 100), MQEParser.NEQ);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, neq.getType());
        assertEquals(0, neq.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(1, neq.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult deq = CompareOp.doCompareOP(
            mockData.newSeriesNoLabeledResult(200, 200), mockData.newSeriesNoLabeledResult(200, 100), MQEParser.DEQ);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, neq.getType());
        assertEquals(1, deq.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(0, deq.getResults().get(0).getValues().get(1).getDoubleValue());
    }

    @Test
    public void seriesLabeledTest() throws Exception {
        //seriesLabeled > seriesNoLabeled
        ExpressionResult gt = CompareOp.doCompareOP(mockData.newSeriesLabeledResult(100, 300, 101, 200),
                                                    mockData.newSeriesNoLabeledResult(100, 200), MQEParser.GT
        );
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, gt.getType());
        //label=1, label2=21
        assertEquals("1", gt.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals("100", gt.getResults().get(0).getValues().get(0).getId());
        assertEquals(0, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(0).getValues().get(1).getId());
        assertEquals(1, gt.getResults().get(0).getValues().get(1).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", gt.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals("100", gt.getResults().get(1).getValues().get(0).getId());
        assertEquals(1, gt.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(1).getValues().get(1).getId());
        assertEquals(0, gt.getResults().get(1).getValues().get(1).getDoubleValue());

        //seriesLabeled > seriesLabeled
        gt = CompareOp.doCompareOP(mockData.newSeriesLabeledResult(101, 300, 100, 201),
                                   mockData.newSeriesLabeledResult(100, 300, 101, 200), MQEParser.GT
        );
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, gt.getType());
        //label=1, label2=21
        assertEquals("1", gt.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals("100", gt.getResults().get(0).getValues().get(0).getId());
        assertEquals(1, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(0).getValues().get(1).getId());
        assertEquals(0, gt.getResults().get(0).getValues().get(1).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", gt.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals("100", gt.getResults().get(1).getValues().get(0).getId());
        assertEquals(0, gt.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(1).getValues().get(1).getId());
        assertEquals(1, gt.getResults().get(1).getValues().get(1).getDoubleValue());

        //seriesNoLabeled > seriesLabeled
        gt = CompareOp.doCompareOP(mockData.newSeriesNoLabeledResult(101, 202),
                                   mockData.newSeriesLabeledResult(100, 300, 101, 201), MQEParser.GT
        );
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, gt.getType());
        //label=1, label2=21
        assertEquals("1", gt.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals("100", gt.getResults().get(0).getValues().get(0).getId());
        assertEquals(1, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(0).getValues().get(1).getId());
        assertEquals(0, gt.getResults().get(0).getValues().get(1).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", gt.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals("100", gt.getResults().get(1).getValues().get(0).getId());
        assertEquals(0, gt.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(1).getValues().get(1).getId());
        assertEquals(1, gt.getResults().get(1).getValues().get(1).getDoubleValue());
    }

    @Test
    public void many2OneTest() throws Exception {
        //sort_list > single
        ExpressionResult gt = CompareOp.doCompareOP(
            mockData.newListResult(), mockData.newSingleResult(200), MQEParser.GT);
        assertEquals(ExpressionResultType.SORTED_LIST, gt.getType());
        assertEquals("service_A", gt.getResults().get(0).getValues().get(0).getId());
        assertEquals(0, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("service_B", gt.getResults().get(0).getValues().get(1).getId());
        assertEquals(1, gt.getResults().get(0).getValues().get(1).getDoubleValue());

        //seriesNoLabeled > single
        gt = CompareOp.doCompareOP(
            mockData.newSeriesNoLabeledResult(100, 200), mockData.newSingleResult(101), MQEParser.GT);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, gt.getType());
        assertEquals("100", gt.getResults().get(0).getValues().get(0).getId());
        assertEquals(0, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(0).getValues().get(1).getId());
        assertEquals(1, gt.getResults().get(0).getValues().get(1).getDoubleValue());

        //seriesLabeled > single
        gt = CompareOp.doCompareOP(mockData.newSeriesLabeledResult(100, 300, 101, 200),
                                   mockData.newSingleResult(101), MQEParser.GT
        );
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, gt.getType());
        //label=1, label2=21
        assertEquals("1", gt.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals("100", gt.getResults().get(0).getValues().get(0).getId());
        assertEquals(0, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(0).getValues().get(1).getId());
        assertEquals(1, gt.getResults().get(0).getValues().get(1).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", gt.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals("100", gt.getResults().get(1).getValues().get(0).getId());
        assertEquals(0, gt.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(1).getValues().get(1).getId());
        assertEquals(1, gt.getResults().get(1).getValues().get(1).getDoubleValue());
    }

    @Test
    public void one2ManyTest() throws Exception {
        // single > sort_list
        ExpressionResult gt = CompareOp.doCompareOP(
            mockData.newSingleResult(200), mockData.newListResult(), MQEParser.GT);
        assertEquals(ExpressionResultType.SORTED_LIST, gt.getType());
        assertEquals("service_A", gt.getResults().get(0).getValues().get(0).getId());
        assertEquals(1, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("service_B", gt.getResults().get(0).getValues().get(1).getId());
        assertEquals(0, gt.getResults().get(0).getValues().get(1).getDoubleValue());

        //single > seriesNoLabeled
        gt = CompareOp.doCompareOP(
            mockData.newSingleResult(101), mockData.newSeriesNoLabeledResult(100, 200), MQEParser.GT);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, gt.getType());
        assertEquals("100", gt.getResults().get(0).getValues().get(0).getId());
        assertEquals(1, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(0).getValues().get(1).getId());
        assertEquals(0, gt.getResults().get(0).getValues().get(1).getDoubleValue());

        //single > seriesLabeled
        gt = CompareOp.doCompareOP(mockData.newSingleResult(101),
                                   mockData.newSeriesLabeledResult(100, 200, 200, 100), MQEParser.GT);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, gt.getType());
        //label=1, label2=21
        assertEquals("1", gt.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals("100", gt.getResults().get(0).getValues().get(0).getId());
        assertEquals(1, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(0).getValues().get(1).getId());
        assertEquals(0, gt.getResults().get(0).getValues().get(1).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", gt.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals("100", gt.getResults().get(1).getValues().get(0).getId());
        assertEquals(0, gt.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("300", gt.getResults().get(1).getValues().get(1).getId());
        assertEquals(1, gt.getResults().get(1).getValues().get(1).getDoubleValue());
    }

    @Test
    public void single2SingleTest() throws IllegalExpressionException {
        //noLabeled > noLabeled
        ExpressionResult gt = CompareOp.doCompareOP(
            mockData.newSingleResult(100), mockData.newSingleResult(200), MQEParser.GT);
        assertEquals(ExpressionResultType.SINGLE_VALUE, gt.getType());
        assertEquals(0, gt.getResults().get(0).getValues().get(0).getDoubleValue());

        //labeled > noLabeled
        gt = CompareOp.doCompareOP(
            mockData.newSingleLabeledResult(100, 200), mockData.newSingleResult(100), MQEParser.GT);
        assertEquals(ExpressionResultType.SINGLE_VALUE, gt.getType());
        //label=1, label2=21
        assertEquals("1", gt.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals(0, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", gt.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals(1, gt.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("21", gt.getResults().get(1).getMetric().getLabels().get(1).getValue());

        //nolabeled > labeled
        gt = CompareOp.doCompareOP(
            mockData.newSingleResult(101), mockData.newSingleLabeledResult(100, 200), MQEParser.GT);
        assertEquals(ExpressionResultType.SINGLE_VALUE, gt.getType());
        //label=1, label2=21
        assertEquals("1", gt.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals(1, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", gt.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals(0, gt.getResults().get(1).getValues().get(0).getDoubleValue());

        //labeled > labeled
        gt = CompareOp.doCompareOP(
            mockData.newSingleLabeledResult(100, 202), mockData.newSingleLabeledResult(100, 200), MQEParser.GT);
        assertEquals(ExpressionResultType.SINGLE_VALUE, gt.getType());
        //label=1, label2=21
        assertEquals("1", gt.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals(0, gt.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2, label2=21
        assertEquals("2", gt.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("21", gt.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals(1, gt.getResults().get(1).getValues().get(0).getDoubleValue());
    }
}
