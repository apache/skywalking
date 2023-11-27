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

import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.operation.TrendOp;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.ExpressionResultType;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TrendOpTest {
    private final MockData mockData = new MockData();

    @Test
    public void increaseNoLabeledTest() throws Exception {
        ExpressionResult increase = TrendOp.doTrendOp(
            mockData.newSeriesNoLabeledResult(100, 280), MQEParser.INCREASE, 1, Step.MINUTE);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, increase.getType());
        assertEquals("300", increase.getResults().get(0).getValues().get(0).getId());
        assertEquals(180, increase.getResults().get(0).getValues().get(0).getDoubleValue());
    }

    @Test
    public void increaseLabeledTest() throws Exception {
        ExpressionResult increase = TrendOp.doTrendOp(
            mockData.newSeriesLabeledResult(100, 280, 100, 220), MQEParser.INCREASE, 1, Step.MINUTE);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, increase.getType());
        assertEquals("300", increase.getResults().get(0).getValues().get(0).getId());
        assertEquals(180, increase.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", increase.getResults().get(1).getValues().get(0).getId());
        assertEquals(120, increase.getResults().get(1).getValues().get(0).getDoubleValue());
    }

    @Test
    public void rateNoLabeledTest() throws Exception {
        ExpressionResult rate = TrendOp.doTrendOp(
            mockData.newSeriesNoLabeledResult(100, 280), MQEParser.RATE, 1, Step.MINUTE);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, rate.getType());
        assertEquals("300", rate.getResults().get(0).getValues().get(0).getId());
        assertEquals(3, rate.getResults().get(0).getValues().get(0).getDoubleValue());
    }

    @Test
    public void rateLabeledTest() throws Exception {
        ExpressionResult rate = TrendOp.doTrendOp(
            mockData.newSeriesLabeledResult(100, 280, 100, 220), MQEParser.RATE, 1, Step.MINUTE);
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, rate.getType());
        assertEquals("300", rate.getResults().get(0).getValues().get(0).getId());
        assertEquals(3, rate.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals("300", rate.getResults().get(1).getValues().get(0).getId());
        assertEquals(2, rate.getResults().get(1).getValues().get(0).getDoubleValue());
    }
}
