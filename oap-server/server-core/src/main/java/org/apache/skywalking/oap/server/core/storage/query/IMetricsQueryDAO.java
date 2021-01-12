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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.IntValues;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.DAO;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;

import static java.util.stream.Collectors.toList;

/**
 * Query metrics values in different ways.
 *
 * @since 8.0.0
 */
public interface IMetricsQueryDAO extends DAO {
    long readMetricsValue(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException;

    MetricsValues readMetricsValues(MetricsCondition condition,
                                    String valueColumnName,
                                    Duration duration) throws IOException;

    List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition,
                                                 String valueColumnName,
                                                 List<String> labels,
                                                 Duration duration) throws IOException;

    HeatMap readHeatMap(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException;

    class Util {
        /**
         * Make sure the order is same as the expected order, add defaultValue if absent.
         */
        public static IntValues sortValues(IntValues origin, List<String> expectedOrder, int defaultValue) {
            IntValues intValues = new IntValues();

            expectedOrder.forEach(id -> {
                KVInt e = new KVInt();
                e.setId(id);
                e.setValue(origin.findValue(id, defaultValue));
                intValues.addKVInt(e);
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
        public static List<MetricsValues> composeLabelValue(final MetricsCondition condition,
            final List<String> labels,
            final List<String> ids,
            final Map<String, DataTable> idMap) {
            List<String> allLabels;
            if (Objects.isNull(labels) || labels.size() < 1 || labels.stream().allMatch(Strings::isNullOrEmpty)) {
                allLabels = idMap.values().stream()
                    .flatMap(dataTable -> dataTable.keys().stream())
                    .distinct().collect(Collectors.toList());
            } else {
                allLabels = labels;
            }
            final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
            List<LabeledValue> labeledValues = new TreeSet<>(allLabels).stream()
                .flatMap(label -> ids.stream().map(id ->
                    new LabeledValue(
                        label,
                        id,
                        Optional.ofNullable(idMap.getOrDefault(id, new DataTable()).get(label)).orElse((long) defaultValue))))
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
    }

    class LabeledValue {
        private final String label;
        private final KVInt kv;

        public LabeledValue(String label, String id, long value) {
            this.label = label;
            KVInt kv = new KVInt();
            kv.setId(id);
            kv.setValue(value);
            this.kv = kv;
        }
    }
}
