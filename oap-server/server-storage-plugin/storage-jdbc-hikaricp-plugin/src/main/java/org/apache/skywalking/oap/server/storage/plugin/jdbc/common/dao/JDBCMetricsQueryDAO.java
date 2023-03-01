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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JDBCMetricsQueryDAO extends JDBCSQLExecutor implements IMetricsQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public long readMetricsValue(final MetricsCondition condition,
                                String valueColumnName,
                                final Duration duration) {
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
        final var defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        final var function = ValueColumnMetadata.INSTANCE.getValueFunction(condition.getName());
        if (function == Function.Latest) {
            return readMetricsValues(condition, valueColumnName, duration).getValues().latestValue(defaultValue);
        }
        String op;
        switch (function) {
            case Avg:
                op = "avg";
                break;
            default:
                op = "sum";
        }

        final var results = new ArrayList<Long>();
        for (String table : tables) {
            final var sql = buildMetricsValueSql(op, valueColumnName, table);
            final var parameters = new ArrayList<>();
            if (entityId != null) {
                sql.append(Metrics.ENTITY_ID + " = ? and ");
                parameters.add(entityId);
            }
            sql.append("id in ");
            sql.append(ids.stream().map(it -> "?").collect(Collectors.joining(", ", "(", ")")));
            parameters.addAll(ids);
            sql.append(" and ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            parameters.add(condition.getName());
            sql.append(" group by " + Metrics.ENTITY_ID);

            results.add(jdbcClient.executeQuery(
                sql.toString(),
                resultSet -> {
                    if (resultSet.next()) {
                        return resultSet.getLong("result");
                    }
                    return (long) defaultValue;
                },
                parameters.toArray(new Object[0])
            ));
        }
        if (op.equals("avg")) {
            return results.stream().mapToLong(it -> it).sum() / results.size();
        }
        return results.stream().mapToLong(it -> it).sum();
    }

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

            buildShardingCondition(sql, ids, entityId);

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
                                                        final List<String> labels,
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

            buildShardingCondition(sql, ids, entityId);

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
            Util.composeLabelValue(condition, labels, ids, idMap),
            ids,
            ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName())
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
                    .map(pointOfTime -> TableHelper.generateId(condition.getName(), pointOfTime.id(entityId))                    )
                    .collect(Collectors.toList());

            final var sql = new StringBuilder("select id, " + valueColumnName + " dataset, id from " + table)
                .append(" where id in ")
                .append(
                    ids.stream()
                       .map(it -> "?")
                       .collect(Collectors.joining(", ", "(", ")"))
                );

            buildShardingCondition(sql, ids, entityId);

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

    protected void buildShardingCondition(StringBuilder sql, List<String> parameters, String entityId) {
    }
}
