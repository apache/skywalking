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
import org.apache.skywalking.mqe.rt.operation.TopNOfOp;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResultType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TopNOfOpTest {

    @Test
    public void mergeTopNResultTest() throws IllegalExpressionException {
        MockData mockData = new MockData();
        List<ExpressionResult> topNResults = List.of(
            mockData.newListResult(1000, 100),
            mockData.newListResult(600, 500),
            mockData.newListResult(300, 2000)
        );
        ExpressionResult topNResult = TopNOfOp.doMergeTopNResult(topNResults, 2, MQEParser.DES);
        Assertions.assertEquals(ExpressionResultType.SORTED_LIST, topNResult.getType());
        Assertions.assertEquals(2, topNResult.getResults().get(0).getValues().size());
        Assertions.assertEquals(2000, topNResult.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals("service_B", topNResult.getResults().get(0).getValues().get(0).getId());
        Assertions.assertEquals(1000, topNResult.getResults().get(0).getValues().get(1).getDoubleValue());
        Assertions.assertEquals("service_A", topNResult.getResults().get(0).getValues().get(1).getId());

        ExpressionResult topNResultAsc = TopNOfOp.doMergeTopNResult(topNResults, 8, MQEParser.ASC);
        Assertions.assertEquals(6, topNResultAsc.getResults().get(0).getValues().size());
        Assertions.assertEquals(100, topNResultAsc.getResults().get(0).getValues().get(0).getDoubleValue());
        Assertions.assertEquals("service_B", topNResultAsc.getResults().get(0).getValues().get(0).getId());
        Assertions.assertEquals(2000, topNResultAsc.getResults().get(0).getValues().get(5).getDoubleValue());
        Assertions.assertEquals("service_B", topNResultAsc.getResults().get(0).getValues().get(5).getId());
        topNResults.get(2).setType(ExpressionResultType.RECORD_LIST);
        Assertions.assertThrows(IllegalExpressionException.class, () -> {
            TopNOfOp.doMergeTopNResult(topNResults, 2, MQEParser.DES);
        });
    }
}
