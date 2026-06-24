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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.banyandb.v1.client.DataPoint;
import org.apache.skywalking.library.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQueryResponse;
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

@Slf4j
public class BanyanDBMetricsQueryDAO extends AbstractBanyanDBDAO implements IMetricsQueryDAO {
    public BanyanDBMetricsQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public MetricsValues readMetricsValues(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        String modelName = condition.getName();
        // findMetricMetadata is overlay-aware: for a foreign metric (the admin inspect value path) it
        // synthesizes a read-only schema from InspectQueryContext; a local metric returns the registered
        // one. So this read path no longer special-cases foreign metrics.
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(modelName, duration.getStep());
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
        final boolean isColdStage = duration != null && duration.isColdStage();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(metricsName, duration.getStep());
        if (schema == null) {
            throw new IOException("schema is not registered");
        }
        MeasureQueryResponse resp = query(
            isColdStage, schema, ImmutableSet.of(Metrics.ENTITY_ID), ImmutableSet.of(valueColumnName), getTimestampRange(duration),
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
    public List<String> listEntityIdsInRange(final String metricName,
                                             final String valueColumnName,
                                             final String valueType,
                                             final Duration duration,
                                             final int limit) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final MetadataRegistry.Schema schema;
        if (valueType != null) {
            // Foreign metric: no local schema. Synthesize a read-only measure schema from the
            // deterministic name → measure/group mapping plus the caller's value column / type.
            schema = MetadataRegistry.INSTANCE.synthesizeForeignMetricSchema(
                metricName, duration.getStep(), valueColumnName, valueType);
            if (schema == null) {
                throw new IOException(
                    "cannot inspect foreign metric " + metricName + " on BanyanDB: this node has no "
                        + "registered measure to resolve the namespace/group from");
            }
        } else {
            schema = MetadataRegistry.INSTANCE.findMetricMetadata(metricName, duration.getStep());
            if (schema == null) {
                throw new IOException("schema is not registered");
            }
        }
        final MeasureQueryResponse resp = query(
            isColdStage, schema,
            ImmutableSet.of(Metrics.ENTITY_ID), ImmutableSet.of(valueColumnName),
            getTimestampRange(duration),
            new QueryBuilder<MeasureQuery>() {
                @Override
                protected void apply(final MeasureQuery query) {
                    // Most-recent-first ordering must be explicit — without it BanyanDB returns
                    // arbitrary order and a hot entity that ingested late can be dropped before
                    // the LIMIT cap is reached.
                    query.setOrderBy(new AbstractQuery.OrderBy(AbstractQuery.Sort.DESC));
                    query.limit(limit);
                }
            }
        );
        if (resp.size() == 0) {
            return Collections.emptyList();
        }
        // BanyanDB returns one DataPoint per (timestamp, entity_id); if the same entity has rows
        // in multiple time buckets within the range, we'd see it more than once. LinkedHashSet
        // dedups while preserving the server-side timestamp-DESC ordering.
        final LinkedHashSet<String> entityIds = new LinkedHashSet<>();
        for (final DataPoint dp : resp.getDataPoints()) {
            entityIds.add(dp.getTagValue(Metrics.ENTITY_ID));
        }
        return new ArrayList<>(entityIds);
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
        final MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(condition.getName(), duration.getStep());
        if (schema == null) {
            throw new IOException("schema is not registered");
        }
        return queryByEntityID(schema, valueColumnName, duration, condition.getEntity().buildId());
    }

    private Map<Long, DataPoint> queryByEntityID(MetadataRegistry.Schema schema, String valueColumnName, Duration duration, String entityID) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        Map<Long, DataPoint> map = new HashMap<>();
        MeasureQueryResponse resp = queryDebuggable(isColdStage, schema, ImmutableSet.of(Metrics.ENTITY_ID), ImmutableSet.of(valueColumnName), getTimestampRange(duration), new QueryBuilder<MeasureQuery>() {
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
