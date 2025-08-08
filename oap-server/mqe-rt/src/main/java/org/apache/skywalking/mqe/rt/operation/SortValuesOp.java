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
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResultType;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValues;
import org.apache.skywalking.oap.server.core.query.mqe.Metadata;

/**
 * When a result has multiple label value groups, it is often required to sort the values of these groups.
 * SortValuesOp is used to sort and pick the top N label value groups, which according to
 * the values of a given ExpressionResult and based on the specified order, limit and aggregation type.
 *
 * It first performs aggregation on the results, then sorts them according to the specified order (ascending or descending),
 * and finally limits the number of results if a limit is provided.
 *
 * for example, the following metrics in time series T1 and T2:
 * T1:
 * http_requests_total{service="api"}   160
 * http_requests_total{service="web"}   120
 * http_requests_total{service="auth"}  80
 *
 * T2:
 * http_requests_total{service="api"}   100
 * http_requests_total{service="web"}   180
 * http_requests_total{service="auth"}  10
 *
 * We can use SortValuesOp to pick the top 2 services with the most avg requests in descending order:
 * `sort_values(http_requests_total, 2, desc, avg)`
 * The result will be:
 * T1:
 * http_requests_total{service="web"}   120
 * http_requests_total{service="api"}   160
 *
 * T2:
 * http_requests_total{service="web"}   180
 * http_requests_total{service="api"}   100
 */
public class SortValuesOp {
    public static ExpressionResult doSortValuesOp(ExpressionResult expResult,
                                                  int limit,
                                                  int order,
                                                  int aggregationType) throws IllegalExpressionException {
        // no label result, no need to sort
        if (!expResult.isLabeledResult()) {
            return expResult;
        }
        // store the original results in a map to avoid losing data during aggregation
        Map<Metadata, MQEValues> resultMap = expResult.getResults()
                                                      .stream()
                                                      .collect(Collectors.toMap(
                                                          MQEValues::getMetric, v -> {
                                                              MQEValues newValues = new MQEValues();
                                                              newValues.setMetric(v.getMetric());
                                                              newValues.setValues(v.getValues());
                                                              return newValues;
                                                          }
                                                      ));
        // do aggregation first
        ExpressionResult aggResult = AggregationOp.doAggregationOp(expResult, aggregationType);

        List<MQEValues> sorted =
            aggResult.getResults().stream()
                     .sorted(getComparator(order))
                     .collect(Collectors.toList());
        if (limit < sorted.size()) {
            sorted = sorted.subList(0, limit);
        }
        List<MQEValues> results = new ArrayList<>();
        sorted.forEach(v -> {
                           MQEValues mqeValues = resultMap.get(v.getMetric());
                           if (mqeValues != null) {
                               results.add(mqeValues);
                           }
                       }
        );

        expResult.setResults(results);
        expResult.setType(ExpressionResultType.TIME_SERIES_VALUES);
        return expResult;
    }

    private static Comparator<MQEValues> getComparator(int order) {
        Comparator<MQEValues> comparator = Comparator.comparingDouble(
            mqeValues -> mqeValues.getValues().isEmpty()
                ? Double.NaN
                : mqeValues.getValues().get(0).getDoubleValue()
        );
        return order == MQEParser.ASC ? comparator : comparator.reversed();
    }
}
