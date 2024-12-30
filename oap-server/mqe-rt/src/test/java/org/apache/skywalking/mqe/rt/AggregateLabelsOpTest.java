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

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.operation.AggregateLabelsOp;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResultType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AggregateLabelsOpTest {

    private final MockData mockData = new MockData();

    @Test
    public void seriesLabeledTest() throws Exception {
        ExpressionResult avgReduce = AggregateLabelsOp.doAggregateLabelsOp(mockData.newSeriesLabeledResult(),
                                                                           MQEParser.AVG,
                                                                           new ArrayList<>());
        // aggregate no label
        assertEquals(1, avgReduce.getResults().size());
        assertEquals(0, avgReduce.getResults().get(0).getMetric().getLabels().size());
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, avgReduce.getType());
        assertEquals((100f + 101f) / 2, avgReduce.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals((300f + 301f) / 2, avgReduce.getResults().get(0).getValues().get(1).getDoubleValue());

        // sum label
        ExpressionResult sumReduce = AggregateLabelsOp.doAggregateLabelsOp(mockData.newSeriesLabeledResult(),
                                                                           MQEParser.SUM,
                                                                           List.of("label")
        );
        assertEquals(2, sumReduce.getResults().size());
        assertEquals(1, sumReduce.getResults().get(0).getMetric().getLabels().size());
        assertEquals("label", sumReduce.getResults().get(0).getMetric().getLabels().get(0).getKey());
        assertEquals(1, sumReduce.getResults().get(1).getMetric().getLabels().size());
        assertEquals("label", sumReduce.getResults().get(1).getMetric().getLabels().get(0).getKey());
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, sumReduce.getType());
        assertEquals(100, sumReduce.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(300, sumReduce.getResults().get(0).getValues().get(1).getDoubleValue());
        assertEquals(101, sumReduce.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals(301, sumReduce.getResults().get(1).getValues().get(1).getDoubleValue());

        // sum label2
        sumReduce = AggregateLabelsOp.doAggregateLabelsOp(mockData.newSeriesLabeledResult(),
                                                                           MQEParser.SUM,
                                                                           List.of("label2")
        );
        assertEquals(1, sumReduce.getResults().size());
        assertEquals(1, sumReduce.getResults().get(0).getMetric().getLabels().size());
        assertEquals("label2", sumReduce.getResults().get(0).getMetric().getLabels().get(0).getKey());
        assertEquals("21", sumReduce.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, sumReduce.getType());
        assertEquals(100f + 101f, sumReduce.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(300f + 301f, sumReduce.getResults().get(0).getValues().get(1).getDoubleValue());

        // sum label and label2
        sumReduce = AggregateLabelsOp.doAggregateLabelsOp(mockData.newSeriesLabeledResult(),
                                                          MQEParser.SUM,
                                                          List.of("label", "label2")
        );
        assertEquals(2, sumReduce.getResults().size());
        assertEquals(2, sumReduce.getResults().get(0).getMetric().getLabels().size());
        assertEquals("label", sumReduce.getResults().get(0).getMetric().getLabels().get(0).getKey());
        assertEquals("1", sumReduce.getResults().get(0).getMetric().getLabels().get(0).getValue());
        assertEquals("label2", sumReduce.getResults().get(0).getMetric().getLabels().get(1).getKey());
        assertEquals("21", sumReduce.getResults().get(0).getMetric().getLabels().get(1).getValue());
        assertEquals("label", sumReduce.getResults().get(1).getMetric().getLabels().get(0).getKey());
        assertEquals("2", sumReduce.getResults().get(1).getMetric().getLabels().get(0).getValue());
        assertEquals("label2", sumReduce.getResults().get(1).getMetric().getLabels().get(1).getKey());
        assertEquals("21", sumReduce.getResults().get(1).getMetric().getLabels().get(1).getValue());
        assertEquals(2, sumReduce.getResults().get(1).getMetric().getLabels().size());
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, sumReduce.getType());
        assertEquals(100, sumReduce.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(300, sumReduce.getResults().get(0).getValues().get(1).getDoubleValue());
        assertEquals(101, sumReduce.getResults().get(1).getValues().get(0).getDoubleValue());
        assertEquals(301, sumReduce.getResults().get(1).getValues().get(1).getDoubleValue());

        // min label2
        ExpressionResult minReduce = AggregateLabelsOp.doAggregateLabelsOp(mockData.newSeriesLabeledResult(),
                                                                           MQEParser.MIN,
                                                                           List.of("label2")
        );
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, minReduce.getType());
        assertEquals(100f, minReduce.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(300f, minReduce.getResults().get(0).getValues().get(1).getDoubleValue());

        // max label2
        ExpressionResult maxReduce = AggregateLabelsOp.doAggregateLabelsOp(mockData.newSeriesLabeledResult(),
                                                                           MQEParser.MAX,
                                                                           List.of("label2")
        );
        assertEquals(ExpressionResultType.TIME_SERIES_VALUES, maxReduce.getType());
        assertEquals(101f, maxReduce.getResults().get(0).getValues().get(0).getDoubleValue());
        assertEquals(301f, maxReduce.getResults().get(0).getValues().get(1).getDoubleValue());
    }
}
