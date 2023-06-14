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

import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.oap.query.graphql.mqe.rt.operation.FunctionOp;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResult;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResultType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FunctionOpTest {
    private final MockData mockData = new MockData();

    //ABS/CEIL/FLOOR/ROUND... are the same logic and tested in here, the others only test ABS is enough.
    @Test
    public void seriesNoLabeledTest() throws Exception {
        ExpressionResult abs = FunctionOp.doFunction0Op(
            mockData.newSeriesNoLabeledResult(-100.111, -300), MQEParser.ABS);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, abs.getType());
        assertEquals("100", abs.getResults().get(0).getValues().get(0).getId());
        assertEquals(100.111, abs.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", abs.getResults().get(0).getValues().get(1).getId());
        assertEquals(300, abs.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult ceil = FunctionOp.doFunction0Op(
            mockData.newSeriesNoLabeledResult(100.111, 300.2), MQEParser.CEIL);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, ceil.getType());
        assertEquals("100", ceil.getResults().get(0).getValues().get(0).getId());
        assertEquals(101, ceil.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", ceil.getResults().get(0).getValues().get(1).getId());
        assertEquals(301, ceil.getResults().get(0).getValues().get(1).getDoubleValue());

        ExpressionResult floor = FunctionOp.doFunction0Op(
            mockData.newSeriesNoLabeledResult(100.111, 300.2), MQEParser.FLOOR);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, ceil.getType());
        assertEquals("100", floor.getResults().get(0).getValues().get(0).getId());
        assertEquals(100, floor.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", floor.getResults().get(0).getValues().get(1).getId());
        assertEquals(300, floor.getResults().get(0).getValues().get(1).getDoubleValue());

        MQEParser.ParameterContext parameterContext = new MQEParser.ParameterContext(null, 0);
        ExpressionResult round = FunctionOp.doFunction1Op(
            mockData.newSeriesNoLabeledResult(100.111, 300.222), MQEParser.ROUND, 2);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, ceil.getType());
        assertEquals("100", round.getResults().get(0).getValues().get(0).getId());
        assertEquals(100.11, round.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", round.getResults().get(0).getValues().get(1).getId());
        assertEquals(300.22, round.getResults().get(0).getValues().get(1).getDoubleValue());
    }

    @Test
    public void seriesLabeledTest() throws Exception {
        ExpressionResult abs = FunctionOp.doFunction0Op(
            mockData.newSeriesLabeledResult(-100.111, -300, -101.333, -301.666), MQEParser.ABS);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, abs.getType());
        //label=1
        assertEquals("1", abs.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("100", abs.getResults().get(0).getValues().get(0).getId());
        assertEquals(100.111, abs.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", abs.getResults().get(0).getValues().get(1).getId());
        assertEquals(300, abs.getResults().get(0).getValues().get(1).getDoubleValue());
        //label=2
        assertEquals("2", abs.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("100", abs.getResults().get(1).getValues().get(0).getId());
        assertEquals(101.333, abs.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals("300", abs.getResults().get(1).getValues().get(1).getId());
        assertEquals(301.666, abs.getResults().get(1).getValues().get(1).getDoubleValue());
    }

    @Test
    public void listTest() throws Exception {
        ExpressionResult abs = FunctionOp.doFunction0Op(mockData.newListResult(-100.111, -300), MQEParser.ABS);
        assertEquals(ExpressionResultType.SORTED_LIST, abs.getType());
        assertEquals("service_A", abs.getResults().get(0).getValues().get(0).getId());
        assertEquals(100.111, abs.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("service_B", abs.getResults().get(0).getValues().get(1).getId());
        assertEquals(300, abs.getResults().get(0).getValues().get(1).getDoubleValue());
    }
}
