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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import java.util.ArrayList;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JDBCMetricsQueryDAO extends JDBCSQLExecutor implements IMetricsQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    protected StringBuilder buildMetricsValueSql(String op, String valueColumnName, String conditionName) {
        return new StringBuilder(
                "select " + Metrics.ENTITY_ID + " id, " + op + "(" + valueColumnName + ") result from " + conditionName + " where ");
    }

    @Override
    @SneakyThrows
    public MetricsValues readMetricsValues(final MetricsCondition condition,
                                           final String valueColumnName,
                                           final Duration duration) {
        final var metricsValues = new MetricsValues();
        // Label is null, because in readMetricsValues, no label parameter.
        final var intValues = metricsValues.getValues();

        final var tables = tableHelper.getTablesForRead(
            condition.getName(),
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );

        final var pointOfTimes = duration.assembleDurationPoints();
        final var entityId = condition.getEntity().buildId();
        final var ids =
            pointOfTimes
                .stream()
                .map(pointOfTime -> TableHelper.generateId(condition.getName(), pointOfTime.id(entityId)))
                .collect(Collectors.toList());

        for (final var table : tables) {
            final var sql = new StringBuilder("select id, " + valueColumnName + " from " + table)
                .append(" where id in ")
                .append(
                    ids.stream()
                       .map(it -> "?")
                       .collect(Collectors.joining(", ", "(", ")"))
                );

            jdbcClient.executeQuery(
                sql.toString(),
                resultSet -> {
                    while (resultSet.next()) {
                        final var kv = new KVInt();
                        kv.setId(resultSet.getString("id"));
                        kv.setValue(resultSet.getLong(valueColumnName));
                        intValues.addKVInt(kv);
                    }
                    return null;
                },
                ids.toArray(new Object[0]));
        }

        metricsValues.setValues(
            Util.sortValues(intValues, ids, ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()))
        );
        return metricsValues;
    }

    @Override
    @SneakyThrows
    public List<MetricsValues> readLabeledMetricsValues(final MetricsCondition condition,
                                                        final String valueColumnName,
                                                        final List<KeyValue> labels,
                                                        final Duration duration) {
        final var idMap = new HashMap<String, DataTable>();
        final var tables = tableHelper.getTablesForRead(
            condition.getName(),
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );

        final var pointOfTimes = duration.assembleDurationPoints();
        final var entityId = condition.getEntity().buildId();
        final var ids =
            pointOfTimes
                .stream()
                .map(pointOfTime -> TableHelper.generateId(condition.getName(), pointOfTime.id(entityId)))
                .collect(Collectors.toList());

        for (final var table : tables) {
            final var sql = new StringBuilder("select id, " + valueColumnName + " from " + table)
                .append(" where id in ")
                .append(
                    ids.stream().map(it -> "?")
                       .collect(Collectors.joining(", ", "(", ")"))
                );

            jdbcClient.executeQuery(
                sql.toString(),
                resultSet -> {
                    while (resultSet.next()) {
                        String id = resultSet.getString("id");

                        DataTable multipleValues = new DataTable(5);
                        multipleValues.toObject(resultSet.getString(valueColumnName));

                        idMap.put(id, multipleValues);
                    }
                    return null;
                },
                ids.toArray(new Object[0]));
        }
        return Util.sortValues(
            Util.composeLabelValue(condition.getName(), labels, ids, idMap),
            ids,
            ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName())
        );
    }

    @SneakyThrows
    public List<MetricsValues> readLabeledMetricsValuesWithoutEntity(final String metricName,
                                                    final String valueColumnName,
                                                    final List<KeyValue> labels,
                                                    final Duration duration) {
        final var idMap = new HashMap<String, DataTable>();
        final var tables = tableHelper.getTablesForRead(
            metricName,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );

        for (final var table : tables) {
            final var sql = new StringBuilder("select id, " + valueColumnName + " from " + table)
                .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ")
                .append(" and ")
                .append(Metrics.TIME_BUCKET).append(" >= ? ")
                .append(" and ")
                .append(Metrics.TIME_BUCKET).append(" <= ?")
                // Limit the number of results to avoid OOM
                .append(" limit ").append(METRICS_VALUES_WITHOUT_ENTITY_LIMIT);

            jdbcClient.executeQuery(
                sql.toString(),
                resultSet -> {
                    while (resultSet.next()) {
                        String id = resultSet.getString("id");
                        DataTable multipleValues = new DataTable(resultSet.getString(valueColumnName));
                        idMap.put(id, multipleValues);
                    }
                    return null;
                },
                metricName, duration.getStartTimeBucket(), duration.getEndTimeBucket());
        }
        final var result = idMap.entrySet()
                                .stream()
                                .limit(METRICS_VALUES_WITHOUT_ENTITY_LIMIT)
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        final var ids = new ArrayList<>(result.keySet());
        return Util.sortValues(
            Util.composeLabelValue(metricName, labels, ids, result),
            ids,
            ValueColumnMetadata.INSTANCE.getDefaultValue(metricName)
        );
    }

    @Override
    @SneakyThrows
    public HeatMap readHeatMap(final MetricsCondition condition,
                               final String valueColumnName,
                               final Duration duration) {
        final var tables = tableHelper.getTablesForRead(
            condition.getName(),
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );
        final var heatMap = new HeatMap();

        for (String table : tables) {
            final var pointOfTimes = duration.assembleDurationPoints();
            final var entityId = condition.getEntity().buildId();
            final var ids =
                pointOfTimes
                    .stream()
                    .map(pointOfTime -> TableHelper.generateId(condition.getName(), pointOfTime.id(entityId)))
                    .collect(Collectors.toList());

            final var sql = new StringBuilder("select id, " + valueColumnName + " dataset, id from " + table)
                .append(" where id in ")
                .append(
                    ids.stream()
                       .map(it -> "?")
                       .collect(Collectors.joining(", ", "(", ")"))
                );

            final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());

            jdbcClient.executeQuery(
                sql.toString(),
                resultSet -> {
                    while (resultSet.next()) {
                        heatMap.buildColumn(
                            resultSet.getString("id"), resultSet.getString("dataset"), defaultValue);
                    }
                    heatMap.fixMissingColumns(ids, defaultValue);

                    return null;
                },
                ids.toArray(new Object[0]));
        }

        return heatMap;
    }
}
