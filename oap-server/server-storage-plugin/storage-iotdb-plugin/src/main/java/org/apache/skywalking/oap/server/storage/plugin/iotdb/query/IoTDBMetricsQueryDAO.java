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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
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
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class IoTDBMetricsQueryDAO implements IMetricsQueryDAO {
    private IoTDBClient client;

    public IoTDBMetricsQueryDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public long readMetricsValue(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        // TODO adopt entity_id index, and use "group by level"
        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        final Function function = ValueColumnMetadata.INSTANCE.getValueFunction(condition.getName());
        if (function == Function.Latest) {
            return readMetricsValues(condition, valueColumnName, duration).getValues().latestValue(defaultValue);
        }
        final String entityId = condition.getEntity().buildId();

        StringBuilder query = new StringBuilder();
        String op;
        if (function == Function.Avg) {
            op = "avg";
        } else {
            op = "sum";
        }
        query.append(String.format("select %s(%s) from ", op, valueColumnName))
                .append(client.getStorageGroup()).append(IoTDBClient.DOT).append(condition.getName())
                .append(" where ");
        if (entityId != null) {
            query.append(Metrics.ENTITY_ID).append(" = ").append(entityId).append(" and ");
        }
        //TODO this solution isn't implement "order by entity_id", maybe has some bugs
        query.append(String.format("%s >= %s and %s <= %s",
                Metrics.TIME_BUCKET, duration.getStartTimestamp(), Metrics.TIME_BUCKET, duration.getEndTimestamp()));

        long result = client.queryWithAgg(condition.getName(), query.toString());
        if (result == Integer.MIN_VALUE) {
            return defaultValue;
        }
        return result;
    }

    @Override
    public MetricsValues readMetricsValues(final MetricsCondition condition,
                                           final String valueColumnName,
                                           final Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        final List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> ids.add(pointOfTime.id(condition.getEntity().buildId())));

        StringBuilder query = new StringBuilder();
        query.append("select ").append(IoTDBClient.ID_COLUMN).append(", ").append(valueColumnName).append(" from ")
                .append(client.getStorageGroup()).append(IoTDBClient.DOT).append(condition.getName())
                .append(" where ").append(IoTDBClient.ID_COLUMN).append(" in (");
        for (int i = 0; i < ids.size(); i++) {
            if (i == 0) {
                query.append("'").append(ids.get(i)).append("'");
            } else {
                query.append(", '").append(ids.get(i)).append("'");
            }
        }
        query.append(")");

        MetricsValues metricsValues = new MetricsValues();
        // Label is null, because in readMetricsValues, no label parameter.
        final IntValues intValues = metricsValues.getValues();

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        try {
            if (sessionPool.checkTimeseriesExists(client.getStorageGroup() + IoTDBClient.DOT + condition.getName())) {
                return metricsValues;
            }
            wrapper = sessionPool.executeQueryStatement(query.toString());
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", query, wrapper);
            }
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                KVInt kv = new KVInt();
                kv.setId(fields.get(1).getStringValue());
                kv.setValue(fields.get(2).getLongV());
                intValues.addKVInt(kv);
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e);
        } finally {
            sessionPool.closeResultSet(wrapper);
        }
        metricsValues.setValues(
                Util.sortValues(intValues, ids, ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()))
        );
        return metricsValues;
    }

    @Override
    public List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition, String valueColumnName, List<String> labels, Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> ids.add(pointOfTime.id(condition.getEntity().buildId())));

        StringBuilder query = new StringBuilder();
        query.append("select ").append(IoTDBClient.ID_COLUMN).append(", ").append(valueColumnName).append(" from ")
                .append(client.getStorageGroup()).append(IoTDBClient.DOT).append(condition.getName())
                .append(" where ").append(IoTDBClient.ID_COLUMN).append(" in (");
        for (int i = 0; i < ids.size(); i++) {
            if (i == 0) {
                query.append("'").append(ids.get(i)).append("'");
            } else {
                query.append(", '").append(ids.get(i)).append("'");
            }
        }
        query.append(")");

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        Map<String, DataTable> idMap = new HashMap<>();
        try {
            if (sessionPool.checkTimeseriesExists(client.getStorageGroup() + IoTDBClient.DOT + condition.getName())) {
                return Collections.emptyList();
            }
            wrapper = sessionPool.executeQueryStatement(query.toString());
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", query, wrapper);
            }
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                String id = fields.get(1).getStringValue();
                DataTable multipleValues = new DataTable(5);
                multipleValues.toObject(fields.get(2).getStringValue());
                idMap.put(id, multipleValues);
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e);
        } finally {
            sessionPool.closeResultSet(wrapper);
        }
        return Util.composeLabelValue(condition, labels, ids, idMap);
    }

    @Override
    public HeatMap readHeatMap(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> ids.add(pointOfTime.id(condition.getEntity().buildId())));

        StringBuilder query = new StringBuilder();
        query.append("select ").append(IoTDBClient.ID_COLUMN).append(", ").append(valueColumnName).append(" from ")
                .append(client.getStorageGroup()).append(IoTDBClient.DOT).append(condition.getName())
                .append(" where ").append(IoTDBClient.ID_COLUMN).append(" in (");
        for (int i = 0; i < ids.size(); i++) {
            if (i == 0) {
                query.append("'").append(ids.get(i)).append("'");
            } else {
                query.append(", '").append(ids.get(i)).append("'");
            }
        }
        query.append(")");

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        HeatMap heatMap = new HeatMap();
        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        try {
            if (sessionPool.checkTimeseriesExists(client.getStorageGroup() + IoTDBClient.DOT + condition.getName())) {
                return heatMap;
            }
            wrapper = sessionPool.executeQueryStatement(query.toString());
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", query, wrapper);
            }
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                heatMap.buildColumn(fields.get(1).getStringValue(), fields.get(2).getStringValue(), defaultValue);
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e);
        } finally {
            sessionPool.closeResultSet(wrapper);
        }
        heatMap.fixMissingColumns(ids, defaultValue);
        return heatMap;
    }
}
