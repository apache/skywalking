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

package org.apache.skywalking.oap.server.storage.plugin.iotdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.rpc.TSStatusCode;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.exception.NullFieldException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.util.HealthChecker;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;

@Slf4j
public class IoTDBClient implements Client, HealthCheckable {
    private final DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();
    private final IoTDBStorageConfig config;

    private SessionPool sessionPool;
    private final String storageGroup;

    public static final String DOT = ".";
    public static final String TIME_BUCKET = "time_bucket";
    public static final String TIME = "Time";
    public static final String TIMESTAMP = "timestamp";
    public static final String ALIGN_BY_DEVICE = " align by device";
    public static final String ID_IDX = "id";
    public static final String ENTITY_ID_IDX = "entity_id";
    public static final String NODE_TYPE_IDX = "node_type";
    public static final String GROUP_IDX = "service_group";
    public static final String SERVICE_ID_IDX = "service_id";
    public static final String TRACE_ID_IDX = "trace_id";

    public IoTDBClient(IoTDBStorageConfig config) throws IOException {
        this.config = config;
        storageGroup = config.getStorageGroup();
    }

    public final String getStorageGroup() {
        return storageGroup;
    }

    @Override
    public void connect() throws IoTDBConnectionException, StatementExecutionException {
        try {
            sessionPool = new SessionPool(config.getHost(), config.getRpcPort(), config.getUsername(),
                    config.getPassword(), config.getSessionPoolSize(), config.isRpcCompression());
            sessionPool.setStorageGroup(storageGroup);

            healthChecker.health();
        } catch (StatementExecutionException e) {
            if (e.getStatusCode() != TSStatusCode.PATH_ALREADY_EXIST_ERROR.getStatusCode()) {
                healthChecker.unHealth(e);
                throw e;
            }
        }
    }

    @Override
    public void shutdown() {
        sessionPool.close();
        this.healthChecker.health();
    }

    @Override
    public void registerChecker(HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
    }

    public SessionPool getSessionPool() {
        return sessionPool;
    }

    public IoTDBStorageConfig getConfig() {
        return config;
    }

    /**
     * Write data to IoTDB
     *
     * @param request an IoTDBInsertRequest
     * @throws IOException IoTDBConnectionException or StatementExecutionException
     */
    public void write(IoTDBInsertRequest request) throws IOException {
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(storageGroup).append(IoTDBClient.DOT).append(request.getModelName());
        try {
            // make an index value as a layer name of the storage path
            if (!request.getIndexes().isEmpty()) {
                request.getIndexValues().forEach(value -> devicePath.append(IoTDBClient.DOT)
                        .append(indexValue2LayerName(value)));
            }
            sessionPool.insertRecord(devicePath.toString(), request.getTime(),
                    request.getTimeseriesList(), request.getTimeseriesTypes(), request.getTimeseriesValues());
            healthChecker.health();
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            healthChecker.unHealth(e);
            throw new IOException(e);
        }
    }

    /**
     * Write a list of data into IoTDB
     *
     * @param requestList a list of IoTDBInsertRequest
     * @throws IOException IoTDBConnectionException or StatementExecutionException
     */
    public void write(List<IoTDBInsertRequest> requestList) throws IOException {
        List<String> devicePathList = new ArrayList<>();
        List<Long> timeList = new ArrayList<>();
        List<List<String>> timeseriesListList = new ArrayList<>();
        List<List<TSDataType>> typesList = new ArrayList<>();
        List<List<Object>> valuesList = new ArrayList<>();

        requestList.forEach(request -> {
            StringBuilder devicePath = new StringBuilder();
            devicePath.append(storageGroup).append(IoTDBClient.DOT).append(request.getModelName());
            // make an index value as a layer name of the storage path
            if (!request.getIndexes().isEmpty()) {
                request.getIndexValues().forEach(value -> devicePath.append(IoTDBClient.DOT)
                        .append(indexValue2LayerName(value)));
            }
            devicePathList.add(devicePath.toString());
            timeList.add(request.getTime());
            timeseriesListList.add(request.getTimeseriesList());
            typesList.add(request.getTimeseriesTypes());
            valuesList.add(request.getTimeseriesValues());
        });

        try {
            sessionPool.insertRecords(devicePathList, timeList, timeseriesListList, typesList, valuesList);
            healthChecker.health();
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            healthChecker.unHealth(e);
            throw new IOException(e);
        }
    }

    /**
     * Normal filter query for a list of data. querySQL must contain "align by device"
     *
     * @param modelName      model name
     * @param querySQL       the SQL for query which must contain "align by device"
     * @param storageBuilder storage builder for transforming storage result map to entity
     * @return a list of result data
     * @throws IOException IoTDBConnectionException or StatementExecutionException
     */
    public List<? super StorageData> filterQuery(String modelName, String querySQL,
                                                 StorageHashMapBuilder<? extends StorageData> storageBuilder)
            throws IOException {
        if (!querySQL.contains("align by device")) {
            throw new IOException("querySQL must contain \"align by device\"");
        }
        List<? super StorageData> storageDataList = new ArrayList<>();
        try {
            StringBuilder devicePath = new StringBuilder();
            devicePath.append(storageGroup).append(IoTDBClient.DOT).append(modelName);
            if (!sessionPool.checkTimeseriesExists(devicePath.toString())) {
                return storageDataList;
            }
            SessionDataSetWrapper wrapper = sessionPool.executeQueryStatement(querySQL);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", querySQL, wrapper.getColumnNames());
            }

            List<String> columnNames = wrapper.getColumnNames();
            IoTDBTableMetaInfo tableMetaInfo = IoTDBTableMetaInfo.get(modelName);
            List<String> indexes = tableMetaInfo.getIndexes();
            while (wrapper.hasNext()) {
                Map<String, Object> map = new HashMap<>();
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                // transform timestamp to time_bucket
                map.put(IoTDBClient.TIME_BUCKET, TimeBucket.getTimeBucket(rowRecord.getTimestamp(),
                        tableMetaInfo.getModel().getDownsampling()));
                // field.get(0) -> Device, transform layerName to indexValue
                String[] layerNames = fields.get(0).getStringValue().split("\\" + IoTDBClient.DOT + "\"");
                for (int i = 0; i < indexes.size(); i++) {
                    map.put(indexes.get(i), layerName2IndexValue(layerNames[i + 1]));
                }
                for (int i = 0; i < columnNames.size() - 2; i++) {
                    String columnName = columnNames.get(i + 2);
                    Field field = fields.get(i + 1);
                    if (field.getDataType() == null) {
                        continue;
                    }
                    if (field.getDataType().equals(TSDataType.TEXT)) {
                        map.put(columnName, field.getStringValue());
                    } else {
                        map.put(columnName, field.getObjectValue(field.getDataType()));
                    }
                }
                if (map.containsKey(IoTDBClient.NODE_TYPE_IDX)) {
                    String nodeType = (String) map.get(IoTDBClient.NODE_TYPE_IDX);
                    map.put(IoTDBClient.NODE_TYPE_IDX, Integer.valueOf(nodeType));
                }
                if (modelName.equals(BrowserErrorLogRecord.INDEX_NAME) || modelName.equals(LogRecord.INDEX_NAME)) {
                    map.put(IoTDBClient.TIMESTAMP, map.get("\"" + IoTDBClient.TIMESTAMP + "\""));
                }

                storageDataList.add(storageBuilder.storage2Entity(map));
            }
            sessionPool.closeResultSet(wrapper);
            healthChecker.health();
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            healthChecker.unHealth(e);
            throw new IOException(e.getMessage() + System.lineSeparator() + "SQL Statement: " + querySQL, e);
        }
        return storageDataList;
    }

    /**
     * query with "string_contains" function
     *
     * @param modelName      model name
     * @param querySQL       query sql
     * @param storageBuilder storageBuilder
     * @return a list of data which has been filtered by "string_contains" function
     * @throws IOException IoTDBConnectionException or StatementExecutionException
     */
    public List<? super StorageData> queryWithContains(String modelName, String querySQL,
                                                       StorageHashMapBuilder<? extends StorageData> storageBuilder)
            throws IOException {
        List<? super StorageData> storageDataList = new ArrayList<>();
        try {
            StringBuilder devicePath = new StringBuilder();
            devicePath.append(storageGroup).append(IoTDBClient.DOT).append(modelName);
            if (!sessionPool.checkTimeseriesExists(devicePath.toString())) {
                return storageDataList;
            }
            SessionDataSetWrapper wrapper = sessionPool.executeQueryStatement(querySQL);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", querySQL, wrapper.getColumnNames());
            }
            List<String> iotDBColumnNames = wrapper.getColumnNames();
            IoTDBTableMetaInfo tableMetaInfo = IoTDBTableMetaInfo.get(modelName);
            List<String> indexes = tableMetaInfo.getIndexes();

            // group columns by device
            Map<String, List<Integer>> deviceAndTimeseriesMap = new HashMap<>();
            Map<String, Integer> deviceAndStringContainsMap = new HashMap<>();
            List<String> timeseriesList = new ArrayList<>(iotDBColumnNames.size());
            final String stringContains = "string_contains";
            for (int i = 1; i < iotDBColumnNames.size(); i++) {
                String iotDBColumnName = iotDBColumnNames.get(i);
                timeseriesList.add(iotDBColumnName.substring(iotDBColumnName.lastIndexOf(IoTDBClient.DOT) + 1));
                if (iotDBColumnName.contains(stringContains)) {
                    String device = iotDBColumnName.substring(stringContains.length() + 1, iotDBColumnName.lastIndexOf(IoTDBClient.DOT));
                    deviceAndStringContainsMap.put(device, i);
                } else {
                    String device = iotDBColumnName.substring(0, iotDBColumnName.lastIndexOf(IoTDBClient.DOT));
                    if (deviceAndTimeseriesMap.containsKey(device)) {
                        deviceAndTimeseriesMap.get(device).add(i);
                    } else {
                        List<Integer> timeseriesIndex = new ArrayList<>(iotDBColumnNames.size());
                        timeseriesIndex.add(i);
                        deviceAndTimeseriesMap.put(device, timeseriesIndex);
                    }
                }
            }

            while (wrapper.hasNext()) {
                Map<String, Object> map = new HashMap<>();
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                // transform timestamp to time_bucket
                map.put(IoTDBClient.TIME_BUCKET, TimeBucket.getTimeBucket(rowRecord.getTimestamp(),
                        tableMetaInfo.getModel().getDownsampling()));
                boolean isStringContains = false;
                for (Map.Entry<String, Integer> entry : deviceAndStringContainsMap.entrySet()) {
                    String device = entry.getKey();
                    int stringContainsIdx = entry.getValue();
                    if (getStringContainsResult(fields.get(stringContainsIdx - 1))) {
                        isStringContains = true;
                        String[] layerNames = device.split("\\" + IoTDBClient.DOT + "\"");
                        for (int i = 0; i < indexes.size(); i++) {
                            map.put(indexes.get(i), layerName2IndexValue(layerNames[i + 1]));
                        }
                        List<Integer> timeseriesIndexes = deviceAndTimeseriesMap.get(device);
                        timeseriesIndexes.forEach(timeseriesIndex -> {
                            String timeseries = timeseriesList.get(timeseriesIndex - 1);
                            Field field = fields.get(timeseriesIndex - 1);
                            if (field.getDataType().equals(TSDataType.TEXT)) {
                                map.put(timeseries, field.getStringValue());
                            } else {
                                map.put(timeseries, field.getObjectValue(field.getDataType()));
                            }
                        });
                        break;
                    }
                }
                if (map.containsKey(IoTDBClient.NODE_TYPE_IDX)) {
                    String nodeType = (String) map.get(IoTDBClient.NODE_TYPE_IDX);
                    map.put(IoTDBClient.NODE_TYPE_IDX, Integer.valueOf(nodeType));
                }

                if (isStringContains) {
                    storageDataList.add(storageBuilder.storage2Entity(map));
                }
            }
            sessionPool.closeResultSet(wrapper);
            healthChecker.health();
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            healthChecker.unHealth(e);
            throw new IOException(e.getMessage() + System.lineSeparator() + "SQL Statement: " + querySQL, e);
        }
        return storageDataList;
    }

    public List<Long> queryWithSelect(String modelName, String querySQL) throws IOException {
        // TODO refactor select query, https://github.com/apache/iotdb/discussions/3888
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(storageGroup).append(IoTDBClient.DOT).append(modelName);
        SessionDataSetWrapper wrapper = null;
        try {
            if (!sessionPool.checkTimeseriesExists(devicePath.toString())) {
                return null;
            }
            wrapper = sessionPool.executeQueryStatement(querySQL);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", querySQL, wrapper.getColumnNames());
            }
            List<Long> longList = new ArrayList<>();
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                longList.add(rowRecord.getFields().get(1).getLongV());
            }
            healthChecker.health();
            return longList;
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            healthChecker.unHealth(e);
            throw new IOException(e.getMessage() + System.lineSeparator() + "SQL Statement: " + querySQL, e);
        } finally {
            sessionPool.closeResultSet(wrapper);
        }
    }

    /**
     * Query with aggregation function: count, sum, avg, last_value, first_value, min_time, max_time, min_value, max_value
     *
     * @param modelName model name
     * @param querySQL  the SQL for query which should contain aggregation function
     * @return the result of aggregation function
     * @throws IOException IoTDBConnectionException or StatementExecutionException
     */
    public List<Double> queryWithAgg(String modelName, String querySQL) throws IOException {
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(storageGroup).append(IoTDBClient.DOT).append(modelName);
        SessionDataSetWrapper wrapper = null;
        try {
            if (!sessionPool.checkTimeseriesExists(devicePath.toString())) {
                throw new IOException("Timeseries doesn't exist");
            }
            wrapper = sessionPool.executeQueryStatement(querySQL);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", querySQL, wrapper.getColumnNames());
            }
            List<Double> results = new ArrayList<>();
            if (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                for (Field field : fields) {
                    results.add(Double.parseDouble(field.getStringValue()));
                }
            }
            healthChecker.health();
            return results;
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            healthChecker.unHealth(e);
            throw new IOException(e.getMessage() + System.lineSeparator() + "SQL Statement: " + querySQL, e);
        } finally {
            sessionPool.closeResultSet(wrapper);
        }
    }

    /**
     * Delete data <= deleteTime in one timeseries
     *
     * @param device     device name
     * @param deleteTime deleteTime
     * @throws IOException IoTDBConnectionException or StatementExecutionException
     */
    public void deleteData(String device, long deleteTime) throws IOException {
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(storageGroup).append(IoTDBClient.DOT).append(device);
        try {
            sessionPool.deleteData(devicePath.toString(), deleteTime);
            healthChecker.health();
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            healthChecker.unHealth(e);
            throw new IOException(e);
        }
    }

    public String indexValue2LayerName(String indexValue) {
        return "\"" + indexValue + "\"";
    }

    public String layerName2IndexValue(String layerName) {
        return layerName.substring(0, layerName.length() - 1);
    }

    public StringBuilder addQueryIndexValue(String modelName, StringBuilder query, Map<String, String> indexAndValueMap) {
        List<String> indexes = IoTDBTableMetaInfo.get(modelName).getIndexes();
        indexes.forEach(index -> {
            if (indexAndValueMap.containsKey(index)) {
                query.append(IoTDBClient.DOT).append(indexValue2LayerName(indexAndValueMap.get(index)));
            } else {
                query.append(IoTDBClient.DOT).append("*");
            }
        });
        return query;
    }

    public StringBuilder addQueryAsterisk(String modelName, StringBuilder query) {
        List<String> indexes = IoTDBTableMetaInfo.get(modelName).getIndexes();
        indexes.forEach(index -> query.append(IoTDBClient.DOT).append("*"));
        return query;
    }

    private boolean getStringContainsResult(Field field) {
        try {
            return field.getBoolV();
        } catch (NullFieldException e) {
            return false;
        }
    }
}
