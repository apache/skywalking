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

package org.apache.skywalking.oap.query.graphql.resolver;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.query.graphql.type.BatchMetricConditions;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricCondition;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.Bucket;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.IntValues;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.Thermodynamic;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @since 8.0.0 This query is replaced by {@link MetricsQuery}
 */
@Deprecated
public class MetricQuery implements GraphQLQueryResolver {
    private MetricsQuery query;

    public MetricQuery(ModuleManager moduleManager) {
        query = new MetricsQuery(moduleManager);
    }

    public IntValues getValues(final BatchMetricConditions metrics, final Duration duration) throws IOException {
        IntValues values = new IntValues();
        if (metrics.getIds().size() == 0) {
            KVInt kv = new KVInt();

            MetricsCondition condition = new MetricsCondition();
            condition.setName(metrics.getName());
            condition.setEntity(new MockEntity(null));

            kv.setValue(query.readMetricsValue(condition, duration));
            values.addKVInt(kv);
        } else {
            for (final String id : metrics.getIds()) {
                KVInt kv = new KVInt();
                kv.setId(id);

                MetricsCondition condition = new MetricsCondition();
                condition.setName(metrics.getName());
                condition.setEntity(new MockEntity(id));

                kv.setValue(query.readMetricsValue(condition, duration));
                values.addKVInt(kv);
            }
        }

        return values;
    }

    public IntValues getLinearIntValues(final MetricCondition metrics,
                                        final Duration duration) throws IOException, ParseException {

        MetricsCondition condition = new MetricsCondition();
        condition.setName(metrics.getName());
        condition.setEntity(new MockEntity(metrics.getId()));

        final MetricsValues metricsValues = query.readMetricsValues(condition, duration);
        return metricsValues.getValues();
    }

    public List<IntValues> getMultipleLinearIntValues(final MetricCondition metrics, final int numOfLinear,
                                                      final Duration duration) throws IOException, ParseException {
        MetricsCondition condition = new MetricsCondition();
        condition.setName(metrics.getName());
        condition.setEntity(new MockEntity(metrics.getId()));

        List<String> labels = new ArrayList<>(numOfLinear);
        for (int i = 0; i < numOfLinear; i++) {
            labels.add(String.valueOf(i));
        }

        final List<MetricsValues> metricsValues = query.readLabeledMetricsValues(condition, labels, duration);
        List<IntValues> response = new ArrayList<>(metricsValues.size());
        labels.forEach(l -> metricsValues.stream()
                                         .filter(m -> m.getLabel().equals(l))
                                         .findAny()
                                         .ifPresent(values -> response.add(values.getValues())));
        return response;
    }

    public List<IntValues> getSubsetOfMultipleLinearIntValues(final MetricCondition metrics,
                                                              final List<Integer> linearIndex,
                                                              final Duration duration) throws IOException, ParseException {
        MetricsCondition condition = new MetricsCondition();
        condition.setName(metrics.getName());
        condition.setEntity(new MockEntity(metrics.getId()));

        List<String> labels = new ArrayList<>(linearIndex.size());
        linearIndex.forEach(i -> labels.add(String.valueOf(i)));

        final List<MetricsValues> metricsValues = query.readLabeledMetricsValues(condition, labels, duration);
        List<IntValues> response = new ArrayList<>(metricsValues.size());
        labels.forEach(l -> metricsValues.stream()
                                         .filter(m -> m.getLabel().equals(l))
                                         .findAny()
                                         .ifPresent(values -> response.add(values.getValues())));
        return response;
    }

    public Thermodynamic getThermodynamic(final MetricCondition metrics,
                                          final Duration duration) throws IOException, ParseException {
        MetricsCondition condition = new MetricsCondition();
        condition.setName(metrics.getName());
        condition.setEntity(new MockEntity(metrics.getId()));

        final HeatMap heatMap = query.readHeatMap(condition, duration);

        Thermodynamic thermodynamic = new Thermodynamic();
        final List<Bucket> buckets = heatMap.getBuckets();

        if (buckets.size() > 1) {
            // Use the first bucket size as the axis Y step, because in the previous(before 8.x),
            // We only use equilong bucket.
            // Use 1 to avoid `infinite-` as bucket#min
            thermodynamic.setAxisYStep(buckets.get(1).duration());
        } else {
            // Used to be a static config.
            thermodynamic.setAxisYStep(200);
        }

        for (int x = 0; x < heatMap.getValues().size(); x++) {
            final HeatMap.HeatMapColumn heatMapColumn = heatMap.getValues().get(x);
            for (int y = 0; y < heatMapColumn.getValues().size(); y++) {
                thermodynamic.addNodeValue(x, y, heatMapColumn.getValues().get(y));
            }
        }

        return thermodynamic;
    }

    @RequiredArgsConstructor
    private static class MockEntity extends Entity {
        private final String id;

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public String buildId() {
            return id;
        }
    }
}
