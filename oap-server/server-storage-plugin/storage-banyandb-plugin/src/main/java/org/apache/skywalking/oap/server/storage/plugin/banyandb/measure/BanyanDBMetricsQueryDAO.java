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
import java.util.Arrays;
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
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.IntValues;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.util.ByteUtil;

import static java.util.Objects.nonNull;

@Slf4j
public class BanyanDBMetricsQueryDAO extends AbstractBanyanDBDAO implements IMetricsQueryDAO {
    public BanyanDBMetricsQueryDAO(BanyanDBStorageClient client) {
        super(client);
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
    public List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition, String valueColumnName, List<KeyValue> labels, Duration duration) throws IOException {
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
                Util.composeLabelValue(condition.getName(), labels, ids, dataTableMap),
                ids,
                ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName())
        );
    }

    public List<MetricsValues> readLabeledMetricsValuesWithoutEntity(final String metricsName,
                                                    final String valueColumnName,
                                                    final List<KeyValue> labels,
                                                    final Duration duration) throws IOException {
        long startTB = 0;
        long endTB = 0;
        if (nonNull(duration)) {
            startTB = duration.getStartTimeBucketInSec();
            endTB = duration.getEndTimeBucketInSec();
        }
        TimestampRange timestampRange = null;
        if (startTB > 0 && endTB > 0) {
            timestampRange = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        }
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(metricsName, duration.getStep());
        if (schema == null) {
            throw new IOException("schema is not registered");
        }
        MeasureQueryResponse resp = query(
            schema, ImmutableSet.of(Metrics.ENTITY_ID), ImmutableSet.of(valueColumnName), timestampRange,
            new QueryBuilder<MeasureQuery>() {
                @Override
                protected void apply(MeasureQuery query) {
                    query.limit(METRICS_VALUES_WITHOUT_ENTITY_LIMIT);
                }
            }
        );
        if (resp.size() == 0) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>(resp.size());
        Map<String, DataTable> dataTableMap = new HashMap<>();
        for (final DataPoint dp : resp.getDataPoints()) {
            long timeBucket = TimeBucket.getTimeBucket(dp.getTimestamp(), schema.getMetadata().getDownSampling());
            String entityID = dp.getTagValue(Metrics.ENTITY_ID);
            PointOfTime pt = new PointOfTime(timeBucket);
            String id = pt.id(entityID);
            ids.add(id);
            dataTableMap.put(id, new DataTable(dp.getFieldValue(valueColumnName)));
        }
        return Util.sortValues(
            Util.composeLabelValue(metricsName, labels, ids, dataTableMap),
            ids,
            ValueColumnMetadata.INSTANCE.getDefaultValue(metricsName)
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
            DataPoint preDp = map.putIfAbsent(timeBucket, dp);
            if (preDp != null) {
                log.warn("{}:{} returns duplicated data point at {}, pre:{}, current:{}", entityID, schema.getMetadata(), timeBucket, preDp.getFields(), dp.getFields());
            }
        }

        return map;
    }
}
