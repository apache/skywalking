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

package org.apache.skywalking.oap.query.graphql.mqe.rt.operation;

import java.util.Collections;
import java.util.List;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.oap.query.graphql.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.query.graphql.mqe.rt.operation.aggregatelabel.AvgAggregateLabelFunc;
import org.apache.skywalking.oap.query.graphql.mqe.rt.operation.aggregatelabel.MaxAggregateLabelFunc;
import org.apache.skywalking.oap.query.graphql.mqe.rt.operation.aggregatelabel.MinAggregateLabelFunc;
import org.apache.skywalking.oap.query.graphql.mqe.rt.operation.aggregatelabel.SumAggregateLabelFunc;
import org.apache.skywalking.oap.query.graphql.mqe.rt.operation.aggregatelabel.AggregateLabelFunc;
import org.apache.skywalking.oap.query.graphql.mqe.rt.operation.aggregatelabel.AggregateLabelFuncFactory;
import org.apache.skywalking.oap.query.graphql.type.mql.ExpressionResult;
import org.apache.skywalking.oap.query.graphql.type.mql.MQEValue;
import org.apache.skywalking.oap.query.graphql.type.mql.MQEValues;

public class AggregateLabelOp {

    public static ExpressionResult doAggregateLabelOp(ExpressionResult result,
                                                      int opType) throws IllegalExpressionException {
        switch (opType) {
            case MQEParser.AVG:
                return aggregateLabeledValueResult(result, AvgAggregateLabelFunc::new);
            case MQEParser.SUM:
                return aggregateLabeledValueResult(result, SumAggregateLabelFunc::new);
            case MQEParser.MAX:
                return aggregateLabeledValueResult(result, MaxAggregateLabelFunc::new);
            case MQEParser.MIN:
                return aggregateLabeledValueResult(result, MinAggregateLabelFunc::new);
            default:
                throw new IllegalExpressionException("Unsupported aggregateLabel function.");
        }
    }

    private static ExpressionResult aggregateLabeledValueResult(ExpressionResult expResult,
                                                                AggregateLabelFuncFactory factory) {
        List<MQEValues> results = expResult.getResults();

        List<MQEValue> combineTo = results.get(0).getValues();
        for (int i = 0; i < combineTo.size(); i++) {
            AggregateLabelFunc aggregateLabelFunc = factory.getAggregateLabelFunc();
            for (MQEValues result : results) {
                aggregateLabelFunc.combine(result.getValues().get(i).getDoubleValue());
            }
            combineTo.get(i).setDoubleValue(aggregateLabelFunc.getResult());
        }

        MQEValues mqeValues = new MQEValues();
        mqeValues.setValues(combineTo);
        expResult.setResults(Collections.singletonList(mqeValues));
        return expResult;
    }
}
