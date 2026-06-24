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

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
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

    /**
     * List distinct {@code entity_id}s that have at least one row for the given metric in the
     * given time range, capped at {@code limit}. Used by the {@code /inspect/entities}
     * admin-server endpoint to enumerate the entities currently emitting values for a metric.
     *
     * <p>Order: most recent timestamp first within the range so callers see live entities ahead
     * of stale ones. Backends dedup by {@code entity_id} before returning. The {@code limit}
     * argument is a server-side cap on the rows scanned, not a guarantee on distinct entities
     * (300 rows ≈ 10 buckets × 30 entities).
     *
     * <p>Handles two cases through one path, mirroring the single {@code /inspect/entities}
     * endpoint:
     * <ul>
     *   <li><b>Locally-defined metric</b> — the model / {@code ValueColumnMetadata} entry exists,
     *       so the backend resolves the physical index/table/group from its registry as before and
     *       the {@code valueType} hint is unused.</li>
     *   <li><b>Foreign metric</b> — persisted by another OAP whose OAL/MAL/runtime-rule set this
     *       node never loaded, so there is no local model. The backend resolves the physical target
     *       from its OWN running configuration (the deterministic metric → storage mapping that
     *       merging has used for years) WITHOUT reading any storage schema/table metadata, using
     *       the caller-supplied {@code valueColumnName} + {@code valueType}. Existence is decided by
     *       the data probe itself (the merged-table discriminator {@code metric_table} /
     *       {@code table_name} on ES / JDBC, the synthesized measure on BanyanDB), so an empty
     *       result means "no rows in range", never a reliable "metric absent".</li>
     * </ul>
     *
     * <p>Abstract on purpose — any 3rd party storage backend that implements
     * {@code IMetricsQueryDAO} MUST provide this override. A default (empty list or thrown
     * exception) would let a missing override slip through compilation and surface as a runtime
     * "no entities" or 500 the first time the inspect API hit that backend; the breaking-at-compile
     * signal is the safer contract for the inspect storage path.
     *
     * @param metricName      metric (model) name; also the merged-table discriminator value.
     * @param valueColumnName the metric's value column (post-override physical name). Required for
     *                        the foreign-metric path (BanyanDB projects/defines the field with it);
     *                        ES / JDBC entity enumeration is value-column-agnostic.
     * @param valueType       value data type for a foreign metric — one of {@code LONG} /
     *                        {@code INT} / {@code DOUBLE} / {@code LABELED}; drives BanyanDB
     *                        field-type synthesis. {@code null} for a locally-defined metric (the
     *                        backend reads the type from its local model).
     * @param duration        query time range + step.
     * @param limit           server-side row cap.
     * @return distinct entity ids holding values for the metric in range, most-recent first.
     * @since 10.5.0
     */
    List<String> listEntityIdsInRange(String metricName,
                                      String valueColumnName,
                                      String valueType,
                                      Duration duration,
                                      int limit) throws IOException;

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
                    } else { // If any query label has no matches, clear all keySets so that no results are returned
                        keySets.clear();
                        break;
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
                    String[] subValues = Iterables.toArray(
                        Splitter.on(separator).omitEmptyStrings().trimResults().split(labelValue), String.class);
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
