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

package org.apache.skywalking.oap.server.core.storage.query;

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.IntValues;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.DAO;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static java.util.stream.Collectors.toList;

/**
 * Query metrics values in different ways.
 *
 * @since 8.0.0
 */
public interface IMetricsQueryDAO extends DAO {
    int METRICS_VALUES_WITHOUT_ENTITY_LIMIT = 10;

    default MetricsValues readMetricsValuesDebuggable(final MetricsCondition condition,
                                                      final String valueColumnName,
                                                      final Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: readMetricsValues");
                span.setMsg(
                    "Condition: MetricsCondition: " + condition + ", ValueColumnName: " + valueColumnName + ", Duration: " + duration);
            }
            return readMetricsValues(condition, valueColumnName, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<MetricsValues> readLabeledMetricsValuesDebuggable(final MetricsCondition condition,
                                                                   final String valueColumnName,
                                                                   final List<KeyValue> labels,
                                                                   final Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: readLabeledMetricsValues");
                span.setMsg(
                    "Condition: MetricsCondition: " + condition + ", ValueColumnName: " + valueColumnName + ", Labels: " + labels + ", Duration: " + duration);
            }
            return readLabeledMetricsValues(condition, valueColumnName, labels, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    MetricsValues readMetricsValues(MetricsCondition condition,
                                    String valueColumnName,
                                    Duration duration) throws IOException;

    List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition,
                                                 String valueColumnName,
                                                 List<KeyValue> labels,
                                                 Duration duration) throws IOException;

    /**
     * Read metrics values without entity. Used for get the labels' metadata.
     */
    List<MetricsValues> readLabeledMetricsValuesWithoutEntity(String metricName,
                                                              String valueColumnName,
                                                              List<KeyValue> labels,
                                                              Duration duration) throws IOException;

    HeatMap readHeatMap(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException;

    class Util {
        /**
         * Make sure the order is same as the expected order, add defaultValue if absent.
         */
        public static IntValues sortValues(IntValues origin, List<String> expectedOrder, int defaultValue) {
            IntValues intValues = new IntValues();

            expectedOrder.forEach(id -> {
                intValues.addKVInt(origin.findValue(id, defaultValue));
            });

            return intValues;
        }

        /**
         * Make sure the order is same as the expected order, add defaultValue if absent.
         */
        public static List<MetricsValues> sortValues(List<MetricsValues> origin,
                                                     List<String> expectedOrder,
                                                     int defaultValue) {
            for (int i = 0; i < origin.size(); i++) {
                final MetricsValues metricsValues = origin.get(i);
                metricsValues.setValues(sortValues(metricsValues.getValues(), expectedOrder, defaultValue));
            }
            return origin;
        }

        /**
         * Compose the multiple metric result based on conditions.
         */
        public static List<MetricsValues> composeLabelValue(final String metricName,
                                                            final List<KeyValue> queryLabels,
                                                            final List<String> ids,
                                                            final Map<String, DataTable> idMap) {
            final Optional<ValueColumnMetadata.ValueColumn> valueColumn
                = ValueColumnMetadata.INSTANCE.readValueColumnDefinition(metricName);
            if (valueColumn.isEmpty()) {
                return Collections.emptyList();
            }
            //compatible with old version query
            if (valueColumn.get().isMultiIntValues()) {
                List<String> labelValues = buildLabelIndex(queryLabels, Const.COMMA).values()
                                                                                    .stream()
                                                                                    .flatMap(List::stream)
                                                                                    .collect(Collectors.toList());
                return composeLabelValueForMultiIntValues(metricName, labelValues, ids, idMap);
            }
            return composeLabelValueForMultipleLabels(metricName, queryLabels, ids, idMap);
        }

        public static List<String> composeLabelConditions(final List<KeyValue> queryLabels,
                                                          final Collection<DataTable> metricValues) {
            LinkedHashMap<String, List<String>> queryLabelIndex = buildLabelIndex(queryLabels, Const.COMMA);
            List<String> labelConditions = new ArrayList<>();
            if (queryLabelIndex.isEmpty()) {
                labelConditions = metricValues.stream()
                                      .flatMap(dataTable -> dataTable.keys().stream())
                                      .distinct()
                                      .filter(k -> k.startsWith(Const.LEFT_BRACE))
                                      .collect(Collectors.toList());
            } else {
                List<Set<String>> keySets = new ArrayList<>();
                for (Map.Entry<String, List<String>> queryLabel : queryLabelIndex.entrySet()) {
                    Set<String> keySet = new HashSet<>();
                    metricValues.forEach(dataTable -> {
                        var metricLabelIndex = dataTable.buildLabelIndex();
                        for (String labelValue : queryLabel.getValue()) {
                            //union labels
                            keySet.addAll(metricLabelIndex.getOrDefault(
                                queryLabel.getKey() + Const.EQUAL + labelValue,
                                new HashSet<>()
                            ));
                        }
                    });
                    if (!keySet.isEmpty()) {
                        keySets.add(keySet);
                    } else { // only all query labels match, can get result
                        keySets.clear();
                    }
                }
                //intersection labels
                keySets.stream().reduce((a, b) -> {
                    a.retainAll(b);
                    return a;
                }).ifPresent(labelConditions::addAll);
            }
            return labelConditions;
        }

        private static List<MetricsValues> composeLabelValueForMultiIntValues(final String metricName,
            final List<String> labels,
            final List<String> ids,
            final Map<String, DataTable> idMap) {
            List<String> allLabels;
            if (Objects.isNull(labels) || labels.isEmpty() || labels.stream().allMatch(Strings::isNullOrEmpty)) {
                allLabels = idMap.values().stream()
                    .flatMap(dataTable -> dataTable.keys().stream())
                    .distinct().filter(k -> !k.startsWith(Const.LEFT_BRACE)).collect(Collectors.toList());
            } else {
                allLabels = labels;
            }
            return buildMetricsValues(metricName, ids, idMap, allLabels);
        }

        private static List<MetricsValues> composeLabelValueForMultipleLabels(final String metricName,
                                                                              final List<KeyValue> queryLabels,
                                                                              final List<String> ids,
                                                                              final Map<String, DataTable> idMap) {
            List<String> labelConditions = composeLabelConditions(queryLabels, idMap.values());
            return buildMetricsValues(metricName, ids, idMap, labelConditions);
        }
    }

    private static List<MetricsValues> buildMetricsValues(final String metricName,
                                                          final List<String> ids,
                                                          final Map<String, DataTable> idMap,
                                                          final List<String> allLabels) {
        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(metricName);
        final var labeledValues = new TreeSet<>(allLabels).stream()
                                                          .flatMap(label -> ids.stream().map(id -> {
                                                              final var value = idMap.getOrDefault(id, new DataTable());

                                                              return Objects.nonNull(value.get(label)) ?
                                                                  new LabeledValue(
                                                                      label,
                                                                      id,
                                                                      value.get(label), false) :
                                                                  new LabeledValue(
                                                                      label,
                                                                      id,
                                                                      defaultValue, true);
                                                          }))
                                                          .collect(toList());
        MetricsValues current = new MetricsValues();
        List<MetricsValues> result = new ArrayList<>();
        for (LabeledValue each : labeledValues) {
            if (Objects.equals(current.getLabel(), each.label)) {
                current.getValues().addKVInt(each.kv);
            } else {
                current = new MetricsValues();
                current.setLabel(each.label);
                current.getValues().addKVInt(each.kv);
                result.add(current);
            }
        }
        return result;
    }

    private static LinkedHashMap<String, List<String>> buildLabelIndex(List<KeyValue> queryLabels, String separator) {
        LinkedHashMap<String, List<String>> labelIndex = new LinkedHashMap<>();
        if (null != queryLabels) {
            for (KeyValue keyValue : queryLabels) {
                String labelName = keyValue.getKey();
                String labelValue = keyValue.getValue();
                if (StringUtil.isNotBlank(labelValue)) {
                    String[] subValues = labelValue.split(separator);
                    for (String subValue : subValues) {
                        labelIndex.computeIfAbsent(labelName, key -> new ArrayList<>()).add(subValue);
                    }
                }
            }
        }
        return labelIndex;
    }

    class LabeledValue {
        private final String label;
        private final KVInt kv;

        public LabeledValue(String label, String id, long value, boolean isEmptyValue) {
            this.label = label;
            this.kv = new KVInt(id, value, isEmptyValue);
        }
    }
}
