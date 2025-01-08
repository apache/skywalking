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

package org.apache.skywalking.mqe.rt.operation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResultType;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValue;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValues;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public class TopNOfOp {
    public static ExpressionResult doMergeTopNResult(List<ExpressionResult> topNResults,
                                                     int limit,
                                                     int order) throws IllegalExpressionException {
        ExpressionResultType type = null;
        List<MQEValue> allValues = new ArrayList<>();
        for (ExpressionResult topNResult : topNResults) {
            if (StringUtil.isNotEmpty(topNResult.getError())) {
                return topNResult;
            }
            // check the type of topNResults
            if (type != null && type != topNResult.getType()) {
                throw new IllegalExpressionException("TopN type is not consistent, one is " + type + ", another is " +
                                                          topNResult.getType());
            }
            type = topNResult.getType();
            // topN result should have values without label
            allValues.addAll(topNResult.getResults().get(0).getValues());
        }
        if (limit > allValues.size()) {
            limit = allValues.size();
        }
        List<MQEValue> mergedValues = allValues.stream()
                                               // Filter out empty values
                                               .filter(mqeValue -> !mqeValue.isEmptyValue())
                                               .sorted(MQEParser.ASC == order ? Comparator.comparingDouble(
                                                   MQEValue::getDoubleValue) :
                                                           Comparator.comparingDouble(MQEValue::getDoubleValue)
                                                                     .reversed())
                                               .limit(limit).collect(Collectors.toList());

        ExpressionResult result = new ExpressionResult();
        MQEValues mqeValues = new MQEValues();
        mqeValues.setValues(mergedValues);
        result.getResults().add(mqeValues);
        result.setType(type);
        return result;
    }
}
