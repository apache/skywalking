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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
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
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

public class H2MetricsQueryDAO extends H2SQLExecutor implements IMetricsQueryDAO {

    private JDBCHikariCPClient h2Client;

    public H2MetricsQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public long readMetricsValue(final MetricsCondition condition,
                                String valueColumnName,
                                final Duration duration) throws IOException {
        int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        final Function function = ValueColumnMetadata.INSTANCE.getValueFunction(condition.getName());
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
        StringBuilder sql = new StringBuilder(
            "select " + Metrics.ENTITY_ID + " id, " + op + "(" + valueColumnName + ") value from " + condition.getName() + " where ");
        final String entityId = condition.getEntity().buildId();
        List<Object> parameters = new ArrayList();
        if (entityId != null) {
            sql.append(Metrics.ENTITY_ID + " = ? and ");
            parameters.add(entityId);
        }
        sql.append(Metrics.TIME_BUCKET + ">= ? and " + Metrics.TIME_BUCKET + "<=?" + " group by " + Metrics.ENTITY_ID);
        parameters.add(duration.getStartTimeBucket());
        parameters.add(duration.getEndTimeBucket());

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection,
                sql.toString(),
                parameters.toArray(new Object[0])
            )) {
                while (resultSet.next()) {
                    return resultSet.getLong("value");
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return defaultValue;
    }

    @Override
    public MetricsValues readMetricsValues(final MetricsCondition condition,
                                           final String valueColumnName,
                                           final Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> {
            ids.add(pointOfTime.id(condition.getEntity().buildId()));
        });

        StringBuilder sql = new StringBuilder(
            "select id, " + valueColumnName + " from " + condition.getName() + " where id in (");
        List<Object> parameters = new ArrayList();
        for (int i = 0; i < ids.size(); i++) {
            if (i == 0) {
                sql.append("?");
            } else {
                sql.append(",?");
            }
            parameters.add(ids.get(i));
        }
        sql.append(")");

        MetricsValues metricsValues = new MetricsValues();
        // Label is null, because in readMetricsValues, no label parameter.
        final IntValues intValues = metricsValues.getValues();

        try (Connection connection = h2Client.getConnection()) {

            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), parameters.toArray(new Object[0]))) {
                while (resultSet.next()) {
                    KVInt kv = new KVInt();
                    kv.setId(resultSet.getString("id"));
                    kv.setValue(resultSet.getLong(valueColumnName));
                    intValues.addKVInt(kv);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
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
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> {
            ids.add(pointOfTime.id(condition.getEntity().buildId()));
        });

        StringBuilder sql = new StringBuilder(
            "select id, " + valueColumnName + " from " + condition.getName() + " where id in (");

        List<Object> parameters = new ArrayList();
        for (int i = 0; i < ids.size(); i++) {
            if (i == 0) {
                sql.append("?");
            } else {
                sql.append(",?");
            }
            parameters.add(ids.get(i));
        }
        sql.append(")");

        Map<String, DataTable> idMap = new HashMap<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), parameters.toArray(new Object[0]))) {
                while (resultSet.next()) {
                    String id = resultSet.getString("id");

                    DataTable multipleValues = new DataTable(5);
                    multipleValues.toObject(resultSet.getString(valueColumnName));

                   idMap.put(id, multipleValues);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return Util.composeLabelValue(condition, labels, ids, idMap);
    }

    @Override
    public HeatMap readHeatMap(final MetricsCondition condition,
                               final String valueColumnName,
                               final Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> {
            ids.add(pointOfTime.id(condition.getEntity().buildId()));
        });

        StringBuilder sql = new StringBuilder(
            "select id, " + valueColumnName + " dataset, id from " + condition.getName() + " where id in (");
        List<Object> parameters = new ArrayList();
        for (int i = 0; i < ids.size(); i++) {
            if (i == 0) {
                sql.append("?");
            } else {
                sql.append(",?");
            }
            parameters.add(ids.get(i));
        }
        sql.append(")");

        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());

        try (Connection connection = h2Client.getConnection()) {
            HeatMap heatMap = new HeatMap();
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), parameters.toArray(new Object[0]))) {

                while (resultSet.next()) {
                    heatMap.buildColumn(
                        resultSet.getString("id"), resultSet.getString("dataset"), defaultValue);
                }
            }

            heatMap.fixMissingColumns(ids, defaultValue);

            return heatMap;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
