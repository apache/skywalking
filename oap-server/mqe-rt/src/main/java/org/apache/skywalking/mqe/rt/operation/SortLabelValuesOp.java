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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValues;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Slf4j
public class SortLabelValuesOp {
    public static ExpressionResult doSortLabelValuesOp(ExpressionResult expResult,
                                                      int order,
                                                      List<String> labelNames) throws IllegalExpressionException {
        if (CollectionUtils.isNotEmpty(labelNames)) {
            labelNames = labelNames.stream().distinct().collect(toList());
            if (MQEParser.ASC == order) {
                expResult.setResults(
                    sort(expResult.getResults(), labelNames, labelNames.get(0), Comparator.naturalOrder()));
            } else if (MQEParser.DES == order) {
                expResult.setResults(
                    sort(expResult.getResults(), labelNames, labelNames.get(0), Comparator.reverseOrder()));
            } else {
                throw new IllegalExpressionException("Unsupported sort order.");
            }
        }
        return expResult;
    }

    private static List<MQEValues> sort(List<MQEValues> results,
                                        List<String> sortLabels,
                                        String currentSortLabel,
                                        Comparator<String> comparator) {
        if (!sortLabels.contains(currentSortLabel)) {
            log.error("Current sort label {} not found in the sort labels {} ", currentSortLabel, sortLabels);
            return results;
        }
        if (sortLabels.indexOf(
            currentSortLabel) == sortLabels.size() - 1) { //only one label or the latest label no need to group
            results.sort(Comparator.comparing(mqeValues -> mqeValues.getMetric()
                                                                    .getLabels()
                                                                    .stream()
                                                                    .filter(kv -> kv.getKey().equals(currentSortLabel))
                                                                    .findFirst()
                                                                    .orElse(new KeyValue(currentSortLabel, ""))
                                                                    .getValue(), comparator));
        } else {
            LinkedHashMap<KeyValue, List<MQEValues>> groupResult = group(results, currentSortLabel);
            LinkedHashMap<KeyValue, List<MQEValues>> sortedGroup = new LinkedHashMap<>(groupResult.size());
            for (Map.Entry<KeyValue, List<MQEValues>> entry : groupResult.entrySet()) {
                //sort by next label
                List<MQEValues> sortedResult = sort(
                    entry.getValue(), sortLabels, sortLabels.get(sortLabels.indexOf(currentSortLabel) + 1), comparator);
                sortedGroup.put(entry.getKey(), sortedResult);
            }
            //combine the sorted group
            results = sortedGroup.keySet()
                                 .stream()
                                 .sorted(Comparator.comparing(KeyValue::getValue, comparator))
                                 .flatMap(keyValue -> sortedGroup.get(keyValue).stream())
                                 .collect(toList());
        }
        return results;
    }

    //group by current label for sub sorting
    private static LinkedHashMap<KeyValue, List<MQEValues>> group(List<MQEValues> results, String labelName) {
        return results
            .stream()
            .collect(groupingBy(
                mqeValues -> mqeValues.getMetric().getLabels()
                                      .stream()
                                      .filter(kv -> kv.getKey().equals(labelName))
                                      .findFirst().orElse(new KeyValue(labelName, "")),
                LinkedHashMap::new,
                toList()
            ));
    }
}

