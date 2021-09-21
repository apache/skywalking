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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
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
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

@Slf4j
@RequiredArgsConstructor
public class IoTDBMetricsQueryDAO implements IMetricsQueryDAO {
    private final IoTDBClient client;

    @Override
    public long readMetricsValue(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        final Function function = ValueColumnMetadata.INSTANCE.getValueFunction(condition.getName());
        if (function == Function.Latest) {
            return readMetricsValues(condition, valueColumnName, duration).getValues().latestValue(defaultValue);
        }

        StringBuilder query = new StringBuilder();
        String op;
        if (function == Function.Avg) {
            op = "avg";
        } else {
            op = "sum";
        }
        query.append(String.format("select %s(%s) from ", op, valueColumnName));
        query = client.addModelPath(query, condition.getName());
        final String entityId = condition.getEntity().buildId();
        if (entityId != null) {
            Map<String, String> indexAndValueMap = new HashMap<>();
            indexAndValueMap.put(IoTDBClient.ENTITY_ID_IDX, entityId);
            query = client.addQueryIndexValue(condition.getName(), query, indexAndValueMap);
        } else {
            query = client.addQueryAsterisk(condition.getName(), query);
        }
        query.append(" where ").append(String.format("%s >= %s and %s <= %s",
                        IoTDBClient.TIME, duration.getStartTimestamp(), IoTDBClient.TIME, duration.getEndTimestamp()))
                .append(" group by level = 3");

        List<Double> results = client.queryWithAgg(condition.getName(), query.toString());
        if (results.size() > 0) {
            double result = results.get(0);
            return (long) result;
        } else {
            return defaultValue;
        }
    }

    @Override
    public MetricsValues readMetricsValues(final MetricsCondition condition,
                                           final String valueColumnName,
                                           final Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        final List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> ids.add(pointOfTime.id(condition.getEntity().buildId())));

        StringBuilder query = new StringBuilder();
        query.append("select ").append(valueColumnName).append(" from ");
        for (String id : ids) {
            query = client.addModelPath(query, condition.getName());
            Map<String, String> indexAndValueMap = new HashMap<>();
            indexAndValueMap.put(IoTDBClient.ID_IDX, id);
            query = client.addQueryIndexValue(condition.getName(), query, indexAndValueMap);
            query.append(", ");
        }
        String queryString = query.toString();
        if (ids.size() > 0) {
            queryString = queryString.substring(0, queryString.lastIndexOf(","));
        }
        queryString += IoTDBClient.ALIGN_BY_DEVICE;

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        MetricsValues metricsValues = new MetricsValues();
        // Label is null, because in readMetricsValues, no label parameter.
        final IntValues intValues = metricsValues.getValues();
        try {
            wrapper = sessionPool.executeQueryStatement(queryString);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", queryString, wrapper.getColumnNames());
            }

            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                String[] layerNames = fields.get(0).getStringValue().split("\\" + IoTDBClient.DOT + "\"");
                String id = client.layerName2IndexValue(layerNames[1]);
                long value = fields.get(1).getLongV();

                KVInt kv = new KVInt();
                kv.setId(id);
                kv.setValue(value);
                intValues.addKVInt(kv);
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e.getMessage() + System.lineSeparator() + "SQL Statement: " + queryString, e);
        } finally {
            sessionPool.closeResultSet(wrapper);
        }
        metricsValues.setValues(Util.sortValues(intValues, ids, ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName())));
        return metricsValues;
    }

    @Override
    public List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition, String valueColumnName, List<String> labels, Duration duration) throws IOException {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        pointOfTimes.forEach(pointOfTime -> ids.add(pointOfTime.id(condition.getEntity().buildId())));

        StringBuilder query = new StringBuilder();
        query.append("select ").append(valueColumnName).append(" from ");
        for (String id : ids) {
            query = client.addModelPath(query, condition.getName());
            Map<String, String> indexAndValueMap = new HashMap<>();
            indexAndValueMap.put(IoTDBClient.ID_IDX, id);
            query = client.addQueryIndexValue(condition.getName(), query, indexAndValueMap);
            query.append(", ");
        }
        String queryString = query.toString();
        if (ids.size() > 0) {
            queryString = queryString.substring(0, queryString.lastIndexOf(","));
        }
        queryString += IoTDBClient.ALIGN_BY_DEVICE;

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        Map<String, DataTable> idMap = new HashMap<>();
        try {
            wrapper = sessionPool.executeQueryStatement(queryString);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", queryString, wrapper.getColumnNames());
            }

            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                String[] layerNames = fields.get(0).getStringValue().split("\\" + IoTDBClient.DOT + "\"");
                String id = client.layerName2IndexValue(layerNames[1]);

                DataTable multipleValues = new DataTable(5);
                multipleValues.toObject(fields.get(1).getStringValue());
                idMap.put(id, multipleValues);
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e.getMessage() + System.lineSeparator() + "SQL Statement: " + queryString, e);
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
        query.append("select ").append(valueColumnName).append(" from ");
        for (String id : ids) {
            query = client.addModelPath(query, condition.getName());
            Map<String, String> indexAndValueMap = new HashMap<>();
            indexAndValueMap.put(IoTDBClient.ID_IDX, id);
            query = client.addQueryIndexValue(condition.getName(), query, indexAndValueMap);
            query.append(", ");
        }
        String queryString = query.toString();
        if (ids.size() > 0) {
            queryString = queryString.substring(0, queryString.lastIndexOf(","));
        }
        queryString += IoTDBClient.ALIGN_BY_DEVICE;

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        HeatMap heatMap = new HeatMap();
        final int defaultValue = ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName());
        try {
            wrapper = sessionPool.executeQueryStatement(queryString);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", queryString, wrapper.getColumnNames());
            }

            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                String[] layerNames = fields.get(0).getStringValue().split("\\" + IoTDBClient.DOT + "\"");
                String id = client.layerName2IndexValue(layerNames[1]);

                heatMap.buildColumn(id, fields.get(1).getStringValue(), defaultValue);
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e.getMessage() + System.lineSeparator() + "SQL Statement: " + queryString, e);
        } finally {
            sessionPool.closeResultSet(wrapper);
        }
        heatMap.fixMissingColumns(ids, defaultValue);
        return heatMap;
    }
}
