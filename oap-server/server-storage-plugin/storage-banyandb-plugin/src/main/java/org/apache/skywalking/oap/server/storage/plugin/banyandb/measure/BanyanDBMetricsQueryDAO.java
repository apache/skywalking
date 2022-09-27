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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.measure;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.HistogramMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.IntValues;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.util.ByteUtil;

public class BanyanDBMetricsQueryDAO extends AbstractBanyanDBDAO implements IMetricsQueryDAO {
    public BanyanDBMetricsQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public long readMetricsValue(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        Function function = ValueColumnMetadata.INSTANCE.getValueFunction(condition.getName());
        if (function == Function.Latest) {
            return readMetricsValues(condition, valueColumnName, duration)
                    .getValues().latestValue(defaultValue);
        }

        final String modelName = condition.getName();
        TimestampRange timestampRange = new TimestampRange(duration.getStartTimestamp(), duration.getEndTimestamp());
        final MeasureQueryResponse resp = query(modelName,
                ImmutableSet.of(Metrics.ENTITY_ID),
                ImmutableSet.of(valueColumnName),
                timestampRange,
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        buildAggregationQuery(query, valueColumnName, function);
                        if (condition.getEntity().buildId() != null) {
                            query.and(eq(Metrics.ENTITY_ID, condition.getEntity().buildId()));
                        }
                    }
                });

        for (DataPoint dataPoint : resp.getDataPoints()) {
            return ((Number) dataPoint.getFieldValue(valueColumnName)).longValue();
        }
        return defaultValue;
    }

    private void buildAggregationQuery(MeasureQuery query, String valueColumnName, Function function) {
        switch (function) {
            case Sum:
                query.sumBy(valueColumnName, Collections.singleton(Metrics.ENTITY_ID));
                break;
            case Avg:
            default:
                query.meanBy(valueColumnName, Collections.singleton(Metrics.ENTITY_ID));
                break;
        }
    }

    @Override
    public MetricsValues readMetricsValues(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        String modelName = condition.getName();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(modelName);
        if (schema == null) {
            throw new IOException("schema is not registered");
        }
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        final List<String> ids = pointOfTimes.stream().map(pointOfTime -> {
            String id = pointOfTime.id(condition.getEntity().buildId());
            return id;
        }).collect(Collectors.toList());

        MetricsValues metricsValues = new MetricsValues();
        Map<String, DataPoint> idMap = queryIDs(modelName, valueColumnName, ids);
        if (!idMap.isEmpty()) {
            // Label is null, because in readMetricsValues, no label parameter.
            IntValues intValues = metricsValues.getValues();
            for (String id : ids) {
                KVInt kvInt = new KVInt();
                kvInt.setId(id);
                kvInt.setValue(0);
                if (idMap.containsKey(id)) {
                    DataPoint dataPoint = idMap.get(id);
                    kvInt.setValue(extractFieldValue(schema, valueColumnName, dataPoint));
                } else {
                    kvInt.setValue(ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()));
                }
                intValues.addKVInt(kvInt);
            }
        }

        metricsValues.setValues(
                Util.sortValues(
                        metricsValues.getValues(), ids, ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()))
        );

        return metricsValues;
    }

    private long extractFieldValue(MetadataRegistry.Schema schema, String fieldName, DataPoint dataPoint) throws IOException {
        MetadataRegistry.ColumnSpec spec = schema.getSpec(fieldName);
        if (spec == null) {
            throw new IOException("field is not registered");
        }
        if (double.class.equals(spec.getColumnClass())) {
            return ByteUtil.bytes2Double(dataPoint.getFieldValue(fieldName)).longValue();
        } else {
            return ((Number) dataPoint.getFieldValue(fieldName)).longValue();
        }
    }

    @Override
    public List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition, String valueColumnName, List<String> labels, Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        String modelName = condition.getName();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> {
            String id = pointOfTime.id(condition.getEntity().buildId());
            ids.add(id);
        });

        Map<String, DataPoint> idMap = queryIDs(modelName, valueColumnName, ids);
        Map<String, DataTable> dataTableMap = new HashMap<>(idMap.size());
        for (final Map.Entry<String, DataPoint> entry : idMap.entrySet()) {
            dataTableMap.put(
                    entry.getKey(),
                    new DataTable(entry.getValue().getFieldValue(valueColumnName))
            );
        }

        return Util.sortValues(
            Util.composeLabelValue(condition, labels, ids, dataTableMap),
            ids,
            ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName())
        );
    }

    @Override
    public HeatMap readHeatMap(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        String modelName = condition.getName();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> {
            String id = pointOfTime.id(condition.getEntity().buildId());
            ids.add(id);
        });

        HeatMap heatMap = new HeatMap();
        Map<String, DataPoint> idMap = queryIDs(modelName, valueColumnName, ids);

        if (idMap.isEmpty()) {
            return heatMap;
        }

        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        for (String id : ids) {
            DataPoint dataPoint = idMap.get(id);
            if (dataPoint != null) {
                String value = dataPoint.getFieldValue(HistogramMetrics.DATASET);
                heatMap.buildColumn(id, value, defaultValue);
            }
        }

        heatMap.fixMissingColumns(ids, defaultValue);

        return heatMap;
    }

    private Map<String, DataPoint> queryIDs(String modelName, String valueColumnName, List<String> measureIDs) throws IOException {
        Map<String, DataPoint> map = new HashMap<>(measureIDs.size());
        for (final String id : measureIDs) {
            MeasureQueryResponse resp = query(modelName, Collections.emptySet(), ImmutableSet.of(valueColumnName), new QueryBuilder<MeasureQuery>() {
                @Override
                protected void apply(MeasureQuery query) {
                    query.andWithID(id);
                }
            });
            if (resp.size() > 0) {
                map.putIfAbsent(resp.getDataPoints().get(0).getId(), resp.getDataPoints().get(0));
            }
        }
        return map;
    }
}
