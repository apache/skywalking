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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import com.google.common.base.Joiner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
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
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.SelectionQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants.ID_COLUMN;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class MetricsQuery implements IMetricsQueryDAO {
    private final InfluxClient client;

    public MetricsQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public long readMetricsValue(final MetricsCondition condition,
                                final String valueColumnName,
                                final Duration duration) throws IOException {
        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        final Function function = ValueColumnMetadata.INSTANCE.getValueFunction(condition.getName());
        if (function == Function.Latest) {
            return readMetricsValues(condition, valueColumnName, duration).getValues().latestValue(defaultValue);
        }
        final String measurement = condition.getName();

        final SelectionQueryImpl query = select();
        if (function == Function.Avg) {
            query.mean(valueColumnName);
        } else {
            query.sum(valueColumnName);
        }
        final WhereQueryImpl<SelectQueryImpl> queryWhereQuery = query.from(client.getDatabase(), measurement).where();

        final String entityId = condition.getEntity().buildId();
        if (entityId != null) {
            queryWhereQuery.and(eq(InfluxConstants.TagName.ENTITY_ID, entityId));
        }

        queryWhereQuery
            .and(gte(InfluxClient.TIME, InfluxClient.timeIntervalTS(duration.getStartTimestamp())))
            .and(lte(InfluxClient.TIME, InfluxClient.timeIntervalTS(duration.getEndTimestamp())))
            .groupBy(InfluxConstants.TagName.ENTITY_ID);

        final List<QueryResult.Series> seriesList = client.queryForSeries(queryWhereQuery);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", queryWhereQuery.getCommand(), seriesList);
        }
        if (CollectionUtils.isNotEmpty(seriesList)) {
            for (QueryResult.Series series : seriesList) {
                Number value = (Number) series.getValues().get(0).get(1);
                return value.longValue();
            }
        }

        return defaultValue;
    }

    @Override
    public MetricsValues readMetricsValues(final MetricsCondition condition,
                                           final String valueColumnName,
                                           final Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        final List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> ids.add(pointOfTime.id(condition.getEntity().buildId())));

        final WhereQueryImpl<SelectQueryImpl> query = select()
            .column(ID_COLUMN)
            .column(valueColumnName)
            .from(client.getDatabase(), condition.getName())
            .where();

        if (CollectionUtils.isNotEmpty(ids)) {
            if (ids.size() == 1) {
                query.where(eq(ID_COLUMN, ids.get(0)));
            } else {
                query.where(contains(ID_COLUMN, Joiner.on("|").join(ids)));
            }
        }
        List<QueryResult.Series> seriesList = client.queryForSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), seriesList);
        }

        MetricsValues metricsValues = new MetricsValues();
        // Label is null, because in readMetricsValues, no label parameter.
        final IntValues intValues = metricsValues.getValues();

        if (CollectionUtils.isNotEmpty(seriesList)) {
            seriesList.get(0).getValues().forEach(values -> {
                KVInt kv = new KVInt();
                kv.setValue(((Number) values.get(2)).longValue());
                kv.setId((String) values.get(1));
                intValues.addKVInt(kv);
            });
        }
        metricsValues.setValues(
            Util.sortValues(intValues, ids, ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()))
        );
        return metricsValues;
    }

    @Override
    public List<MetricsValues> readLabeledMetricsValues(final MetricsCondition condition,
                                                        final String valueColumnName,
                                                        final List<String> labels,
                                                        final Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        final List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> ids.add(pointOfTime.id(condition.getEntity().buildId())));

        final WhereQueryImpl<SelectQueryImpl> query = select()
            .column(ID_COLUMN)
            .column(valueColumnName)
            .from(client.getDatabase(), condition.getName())
            .where();

        if (CollectionUtils.isNotEmpty(ids)) {
            if (ids.size() == 1) {
                query.where(eq(ID_COLUMN, ids.get(0)));
            } else {
                query.where(contains(ID_COLUMN, Joiner.on("|").join(ids)));
            }
        }
        final List<QueryResult.Series> series = client.queryForSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }

        final Map<String, DataTable> idMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(series)) {
            series.get(0).getValues().forEach(values -> {
                final String id = (String) values.get(1);
                DataTable multipleValues = new DataTable(5);
                multipleValues.toObject((String) values.get(2));
                idMap.put(id, multipleValues);
            });
        }
        return Util.composeLabelValue(condition, labels, ids, idMap);
    }

    @Override
    public HeatMap readHeatMap(final MetricsCondition condition,
                               final String valueColumnName,
                               final Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        final List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> ids.add(pointOfTime.id(condition.getEntity().buildId())));

        final WhereQueryImpl<SelectQueryImpl> query = select()
            .column(ID_COLUMN)
            .column(valueColumnName)
            .from(client.getDatabase(), condition.getName())
            .where(contains(ID_COLUMN, Joiner.on("|").join(ids)));

        final QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }

        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());

        final HeatMap heatMap = new HeatMap();
        if (series != null) {
            for (List<Object> values : series.getValues()) {
                heatMap.buildColumn(values.get(1).toString(), values.get(2).toString(), defaultValue);
            }
        }

        heatMap.fixMissingColumns(ids, defaultValue);
        return heatMap;
    }
}
