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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
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
import org.apache.skywalking.oap.server.core.query.type.NullableValue;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.util.ByteUtil;

@Slf4j
public class BanyanDBMetricsQueryDAO extends AbstractBanyanDBDAO implements IMetricsQueryDAO {
    public BanyanDBMetricsQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public NullableValue readMetricsValue(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        String modelName = condition.getName();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(modelName, duration.getStep());
        if (schema == null) {
            throw new IOException("schema is not registered");
        }

        int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        Function function = ValueColumnMetadata.INSTANCE.getValueFunction(condition.getName());
        if (function == Function.Latest) {
            return readMetricsValues(condition, valueColumnName, duration)
                    .getValues().latestValue(defaultValue);
        }

        TimestampRange timestampRange = new TimestampRange(duration.getStartTimestamp(), duration.getEndTimestamp());
        final MeasureQueryResponse resp = query(schema,
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
            return new NullableValue(((Number) dataPoint.getFieldValue(valueColumnName)).longValue(), false);
        }
        return new NullableValue(defaultValue, true);
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
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(modelName, duration.getStep());
        if (schema == null) {
            throw new IOException("schema is not registered");
        }

        final String entityID = condition.getEntity().buildId();
        Map<Long, DataPoint> idMap = queryByEntityID(schema, valueColumnName, duration, entityID);

        List<PointOfTime> tsPoints = duration.assembleDurationPoints();

        MetricsValues metricsValues = new MetricsValues();
        // Label is null, because in readMetricsValues, no label parameter.
        IntValues intValues = metricsValues.getValues();
        for (PointOfTime ts : tsPoints) {
            String id = ts.id(entityID);
            KVInt kvInt = new KVInt();
            kvInt.setId(id);
            if (idMap.containsKey(ts.getPoint())) {
                DataPoint dataPoint = idMap.get(ts.getPoint());
                kvInt.setValue(extractFieldValue(schema, valueColumnName, dataPoint));
            } else {
                kvInt.setValue(ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()));
                kvInt.setEmptyValue(true);
            }
            intValues.addKVInt(kvInt);
        }

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
        Map<Long, DataPoint> idMap = queryByEntityID(condition, valueColumnName, duration);

        List<PointOfTime> tsPoints = duration.assembleDurationPoints();
        String entityID = condition.getEntity().buildId();
        List<String> ids = new ArrayList<>(tsPoints.size());

        Map<String, DataTable> dataTableMap = new HashMap<>(idMap.size());
        for (PointOfTime ts : tsPoints) {
            String id = ts.id(entityID);
            ids.add(id);
            if (idMap.containsKey(ts.getPoint())) {
                dataTableMap.put(
                        id,
                        new DataTable(idMap.get(ts.getPoint()).getFieldValue(valueColumnName))
                );
            }
        }

        return Util.sortValues(
                Util.composeLabelValue(condition, labels, dataTableMap),
                ids,
                ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName())
        );
    }

    @Override
    public HeatMap readHeatMap(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        Map<Long, DataPoint> idMap = queryByEntityID(condition, valueColumnName, duration);

        HeatMap heatMap = new HeatMap();
        if (idMap.isEmpty()) {
            return heatMap;
        }

        List<PointOfTime> tsPoints = duration.assembleDurationPoints();
        String entityID = condition.getEntity().buildId();
        List<String> ids = new ArrayList<>(tsPoints.size());

        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        for (PointOfTime ts : tsPoints) {
            String id = ts.id(entityID);
            ids.add(id);
            DataPoint dataPoint = idMap.get(ts.getPoint());
            if (dataPoint != null) {
                String value = dataPoint.getFieldValue(HistogramMetrics.DATASET);
                heatMap.buildColumn(id, value, defaultValue);
            }
        }

        heatMap.fixMissingColumns(ids, defaultValue);

        return heatMap;
    }

    private Map<Long, DataPoint> queryByEntityID(final MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        final MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(condition.getName(), duration.getStep());
        if (schema == null) {
            throw new IOException("schema is not registered");
        }
        return queryByEntityID(schema, valueColumnName, duration, condition.getEntity().buildId());
    }

    private Map<Long, DataPoint> queryByEntityID(MetadataRegistry.Schema schema, String valueColumnName, Duration duration, String entityID) throws IOException {
        TimestampRange timestampRange = new TimestampRange(duration.getStartTimestamp(), duration.getEndTimestamp());

        Map<Long, DataPoint> map = new HashMap<>();
        MeasureQueryResponse resp = query(schema, ImmutableSet.of(Metrics.ENTITY_ID), ImmutableSet.of(valueColumnName), timestampRange, new QueryBuilder<MeasureQuery>() {
            @Override
            protected void apply(MeasureQuery query) {
                query.and(eq(Metrics.ENTITY_ID, entityID));
            }
        });
        for (final DataPoint dp : resp.getDataPoints()) {
            long timeBucket = TimeBucket.getTimeBucket(dp.getTimestamp(), schema.getMetadata().getDownSampling());
            if (map.putIfAbsent(timeBucket, dp) != null) {
                log.warn("duplicated data point at " + timeBucket);
            }
        }

        return map;
    }
}
