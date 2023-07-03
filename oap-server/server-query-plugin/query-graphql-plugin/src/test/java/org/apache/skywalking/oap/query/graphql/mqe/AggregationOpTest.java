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

package org.apache.skywalking.oap.query.graphql.mqe;

import java.util.Collections;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.oap.query.graphql.mqe.rt.operation.AggregationOp;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResult;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResultType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AggregationOpTest {
    private final MockData mockData = new MockData();
    @Mock
    TerminalNode avgTerminalNode;
    @Mock
    MQEParser.ParameterContext avgParameterContext;
    @Mock
    TerminalNode sumTerminalNode;
    @Mock
    MQEParser.ParameterContext sumParameterContext;

    @BeforeEach
    public void before() {
        when(avgTerminalNode.getText()).thenReturn("avg");
        when(avgParameterContext.STRING_PARAM()).thenReturn(avgTerminalNode);
        when(sumTerminalNode.getText()).thenReturn("sum");
        when(sumParameterContext.STRING_PARAM()).thenReturn(sumTerminalNode);
    }

    @Test
    public void seriesNoLabeledTest() throws Exception {
        ExpressionResult avg = AggregationOp.doAggregationOp(
            mockData.newSeriesNoLabeledResult(), MQEParser.AVG, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        assertNull(avg.getResults().get(0).getValues().get(0).getId());
        assertEquals(200, avg.getResults().get(0).getValues().get(0).getDoubleValue());

        ExpressionResult count = AggregationOp.doAggregationOp(
            mockData.newSeriesNoLabeledResult(), MQEParser.COUNT, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, count.getType());
        assertNull(count.getResults().get(0).getValues().get(0).getId());
        assertEquals(2, count.getResults().get(0).getValues().get(0).getDoubleValue());

        ExpressionResult sum = AggregationOp.doAggregationOp(
            mockData.newSeriesNoLabeledResult(), MQEParser.SUM, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, sum.getType());
        assertNull(sum.getResults().get(0).getValues().get(0).getId());
        assertEquals(400, sum.getResults().get(0).getValues().get(0).getDoubleValue());

        ExpressionResult latest = AggregationOp.doAggregationOp(
            mockData.newSeriesNoLabeledResult(), MQEParser.LATEST, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, latest.getType());
        assertEquals("300", latest.getResults().get(0).getValues().get(0).getId());
        assertEquals(300, latest.getResults().get(0).getValues().get(0).getDoubleValue());

        ExpressionResult max = AggregationOp.doAggregationOp(
            mockData.newSeriesNoLabeledResult(), MQEParser.MAX, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, max.getType());
        assertEquals("300", max.getResults().get(0).getValues().get(0).getId());
        assertEquals(300, max.getResults().get(0).getValues().get(0).getDoubleValue());

        ExpressionResult min = AggregationOp.doAggregationOp(
            mockData.newSeriesNoLabeledResult(), MQEParser.MIN, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, min.getType());
        assertEquals("100", min.getResults().get(0).getValues().get(0).getId());
        assertEquals(100, min.getResults().get(0).getValues().get(0).getDoubleValue());

        ExpressionResult avgReduce = AggregationOp.doAggregationOp(
            mockData.newSeriesNoLabeledResult(), MQEParser.REDUCE, Collections.singletonList(avgParameterContext));
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, avgReduce.getType());
        assertEquals(100, avgReduce.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(300, avgReduce.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult sumReduce = AggregationOp.doAggregationOp(
            mockData.newSeriesNoLabeledResult(), MQEParser.REDUCE, Collections.singletonList(sumParameterContext));
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, sumReduce.getType());
        assertEquals(100, sumReduce.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(300, sumReduce.getResults().get(0).getValues().get(1).getDoubleValue());
    }

    @Test
    public void seriesLabeledTest() throws Exception {
        ExpressionResult avg = AggregationOp.doAggregationOp(
            mockData.newSeriesLabeledResult(), MQEParser.AVG, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertNull(avg.getResults().get(0).getValues().get(0).getId());
        assertEquals(200, avg.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertNull(avg.getResults().get(1).getValues().get(0).getId());
        assertEquals(201, avg.getResults().get(1).getValues().get(0).getDoubleValue());

        ExpressionResult count = AggregationOp.doAggregationOp(
            mockData.newSeriesLabeledResult(), MQEParser.COUNT, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertNull(count.getResults().get(0).getValues().get(0).getId());
        assertEquals(2, count.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertNull(count.getResults().get(1).getValues().get(0).getId());
        assertEquals(2, count.getResults().get(1).getValues().get(0).getDoubleValue());

        ExpressionResult sum = AggregationOp.doAggregationOp(
            mockData.newSeriesLabeledResult(), MQEParser.SUM, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertNull(sum.getResults().get(0).getValues().get(0).getId());
        assertEquals(400, sum.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertNull(sum.getResults().get(1).getValues().get(0).getId());
        assertEquals(402, sum.getResults().get(1).getValues().get(0).getDoubleValue());

        ExpressionResult latest = AggregationOp.doAggregationOp(
            mockData.newSeriesLabeledResult(), MQEParser.LATEST, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertEquals("300", latest.getResults().get(0).getValues().get(0).getId());
        assertEquals(300, latest.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertEquals("300", latest.getResults().get(1).getValues().get(0).getId());
        assertEquals(301, latest.getResults().get(1).getValues().get(0).getDoubleValue());

        ExpressionResult max = AggregationOp.doAggregationOp(
            mockData.newSeriesLabeledResult(), MQEParser.MAX, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertEquals("300", max.getResults().get(0).getValues().get(0).getId());
        assertEquals(300, max.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertEquals("300", max.getResults().get(1).getValues().get(0).getId());
        assertEquals(301, max.getResults().get(1).getValues().get(0).getDoubleValue());

        ExpressionResult min = AggregationOp.doAggregationOp(
            mockData.newSeriesLabeledResult(), MQEParser.MIN, Collections.emptyList());
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertEquals("100", min.getResults().get(0).getValues().get(0).getId());
        assertEquals(100, min.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertEquals("100", min.getResults().get(1).getValues().get(0).getId());
        assertEquals(101, min.getResults().get(1).getValues().get(0).getDoubleValue());

        ExpressionResult avgReduce = AggregationOp.doAggregationOp(
            mockData.newSeriesLabeledResult(), MQEParser.REDUCE, Collections.singletonList(avgParameterContext));
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, avgReduce.getType());
        assertEquals((100f + 101f) / 2, avgReduce.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals((300f + 301f) / 2, avgReduce.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult sumReduce = AggregationOp.doAggregationOp(
            mockData.newSeriesLabeledResult(), MQEParser.REDUCE, Collections.singletonList(sumParameterContext));
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, sumReduce.getType());
        assertEquals(100f + 101f, sumReduce.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(300f + 301f, sumReduce.getResults().get(0).getValues().get(1).getDoubleValue());
    }
}
