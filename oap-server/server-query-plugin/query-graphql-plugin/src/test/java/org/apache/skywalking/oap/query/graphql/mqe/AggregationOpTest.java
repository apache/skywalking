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
import org.apache.skywalking.oap.query.graphql.mqe.rt.operation.AggregationOp;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResult;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResultType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AggregationOpTest {
    private final MockData mockData = new MockData();

    @Test
    public void seriesNoLabeledTest() throws Exception {
        ExpressionResult avg = AggregationOp.doAggregationOp(mockData.newSeriesNoLabeledResult(), MQEParser.AVG);
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        assertNull(avg.getResults().get(0).getValues().get(0).getId());
        assertEquals(200, avg.getResults().get(0).getValues().get(0).getDoubleValue());

        ExpressionResult count = AggregationOp.doAggregationOp(mockData.newSeriesNoLabeledResult(), MQEParser.COUNT);
        assertEquals(ExpressionResultType.SINGLE_VALUE, count.getType());
        assertNull(count.getResults().get(0).getValues().get(0).getId());
        assertEquals(2, count.getResults().get(0).getValues().get(0).getDoubleValue());

        ExpressionResult sum = AggregationOp.doAggregationOp(mockData.newSeriesNoLabeledResult(), MQEParser.SUM);
        assertEquals(ExpressionResultType.SINGLE_VALUE, sum.getType());
        assertNull(sum.getResults().get(0).getValues().get(0).getId());
        assertEquals(400, sum.getResults().get(0).getValues().get(0).getDoubleValue());

        ExpressionResult latest = AggregationOp.doAggregationOp(mockData.newSeriesNoLabeledResult(), MQEParser.LATEST);
        assertEquals(ExpressionResultType.SINGLE_VALUE, latest.getType());
        assertEquals("300", latest.getResults().get(0).getValues().get(0).getId());
        assertEquals(300, latest.getResults().get(0).getValues().get(0).getDoubleValue());

        ExpressionResult max = AggregationOp.doAggregationOp(mockData.newSeriesNoLabeledResult(), MQEParser.MAX);
        assertEquals(ExpressionResultType.SINGLE_VALUE, max.getType());
        assertEquals("300", max.getResults().get(0).getValues().get(0).getId());
        assertEquals(300, max.getResults().get(0).getValues().get(0).getDoubleValue());

        ExpressionResult min = AggregationOp.doAggregationOp(mockData.newSeriesNoLabeledResult(), MQEParser.MIN);
        assertEquals(ExpressionResultType.SINGLE_VALUE, min.getType());
        assertEquals("100", min.getResults().get(0).getValues().get(0).getId());
        assertEquals(100, min.getResults().get(0).getValues().get(0).getDoubleValue());
    }

    @Test
    public void seriesLabeledTest() throws Exception {
        ExpressionResult avg = AggregationOp.doAggregationOp(mockData.newSeriesLabeledResult(), MQEParser.AVG);
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertNull(avg.getResults().get(0).getValues().get(0).getId());
        assertEquals(200, avg.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertNull(avg.getResults().get(1).getValues().get(0).getId());
        assertEquals(201, avg.getResults().get(1).getValues().get(0).getDoubleValue());

        ExpressionResult count = AggregationOp.doAggregationOp(mockData.newSeriesLabeledResult(), MQEParser.COUNT);
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertNull(count.getResults().get(0).getValues().get(0).getId());
        assertEquals(2, count.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertNull(count.getResults().get(1).getValues().get(0).getId());
        assertEquals(2, count.getResults().get(1).getValues().get(0).getDoubleValue());

        ExpressionResult sum = AggregationOp.doAggregationOp(mockData.newSeriesLabeledResult(), MQEParser.SUM);
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertNull(sum.getResults().get(0).getValues().get(0).getId());
        assertEquals(400, sum.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertNull(sum.getResults().get(1).getValues().get(0).getId());
        assertEquals(402, sum.getResults().get(1).getValues().get(0).getDoubleValue());

        ExpressionResult latest = AggregationOp.doAggregationOp(mockData.newSeriesLabeledResult(), MQEParser.LATEST);
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertEquals("300", latest.getResults().get(0).getValues().get(0).getId());
        assertEquals(300, latest.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertEquals("300", latest.getResults().get(1).getValues().get(0).getId());
        assertEquals(301, latest.getResults().get(1).getValues().get(0).getDoubleValue());

        ExpressionResult max = AggregationOp.doAggregationOp(mockData.newSeriesLabeledResult(), MQEParser.MAX);
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertEquals("300", max.getResults().get(0).getValues().get(0).getId());
        assertEquals(300, max.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertEquals("300", max.getResults().get(1).getValues().get(0).getId());
        assertEquals(301, max.getResults().get(1).getValues().get(0).getDoubleValue());

        ExpressionResult min = AggregationOp.doAggregationOp(mockData.newSeriesLabeledResult(), MQEParser.MIN);
        assertEquals(ExpressionResultType.SINGLE_VALUE, avg.getType());
        //label=1
        assertEquals("100", min.getResults().get(0).getValues().get(0).getId());
        assertEquals(100, min.getResults().get(0).getValues().get(0).getDoubleValue());
        //label=2
        assertEquals("100", min.getResults().get(1).getValues().get(0).getId());
        assertEquals(101, min.getResults().get(1).getValues().get(0).getDoubleValue());
    }
}
