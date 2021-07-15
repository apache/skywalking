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

import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.rpc.TSStatusCode;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.util.HealthChecker;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class IoTDBClient implements Client, HealthCheckable {

    private final DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();
    private final IoTDBStorageConfig config;

    private SessionPool sessionPool;
    private final String storageGroup;

    public static final String DOT = ".";
    public static final String ID_COLUMN = "_id";

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

    public void write(IoTDBInsertRequest request) throws IOException {
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(storageGroup).append(IoTDBClient.DOT).append(request.getDeviceName());
        try {
            sessionPool.insertRecord(devicePath.toString(), request.getTime(), request.getMeasurements(), request.getValues());
            healthChecker.health();
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            healthChecker.unHealth(e);
            throw new IOException(e);
        }
    }

    public void write(List<IoTDBInsertRequest> requestList) throws IOException {
        List<String> devicePathList = new ArrayList<>();
        List<Long> timeList = new ArrayList<>();
        List<List<String>> measurementsList = new ArrayList<>();
        List<List<String>> valuesList = new ArrayList<>();
        requestList.forEach(request -> {
            StringBuilder devicePath = new StringBuilder();
            devicePath.append(storageGroup).append(IoTDBClient.DOT).append(request.getDeviceName());
            devicePathList.add(devicePath.toString());
            timeList.add(request.getTime());
            measurementsList.add(request.getMeasurements());
            valuesList.add(request.getValues());

        });
        try {
            sessionPool.insertRecords(devicePathList, timeList, measurementsList, valuesList);
            healthChecker.health();
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            healthChecker.unHealth(e);
            throw new IOException(e);
        }
    }

    public List<? super StorageData> queryForList(String modelName, String querySQL,
                                                  StorageHashMapBuilder<? extends StorageData> storageBuilder)
            throws IOException {
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(storageGroup).append(IoTDBClient.DOT).append(modelName);

        SessionDataSetWrapper wrapper;
        List<? super StorageData> storageDataList = new ArrayList<>();
        try {
            if (!sessionPool.checkTimeseriesExists(devicePath.toString())) {
                return storageDataList;
            }
            wrapper = sessionPool.executeQueryStatement(querySQL);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", querySQL, wrapper);
            }
            List<String> iotDBColumnNames = wrapper.getColumnNames();
            List<String> columnNames = new ArrayList<>(iotDBColumnNames.size());
            iotDBColumnNames.forEach(iotDBColumnName ->
                    columnNames.add(iotDBColumnName.substring(iotDBColumnName.lastIndexOf(".") + 1)));
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                Map<String, Object> map = new HashMap<>();
                for (int i = 0; i < columnNames.size(); i++) {
                    Field field = rowRecord.getFields().get(i);
                    map.put(columnNames.get(i), field.getObjectValue(field.getDataType()));
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

    public List<? super StorageData> queryForListWithContains(String modelName, String querySQL,
                                                              StorageHashMapBuilder<? extends StorageData> storageBuilder)
            throws IOException {
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(storageGroup).append(IoTDBClient.DOT).append(modelName);

        SessionDataSetWrapper wrapper;
        List<? super StorageData> storageDataList = new ArrayList<>();
        try {
            if (!sessionPool.checkTimeseriesExists(devicePath.toString())) {
                return storageDataList;
            }
            wrapper = sessionPool.executeQueryStatement(querySQL);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", querySQL, wrapper);
            }
            List<String> iotDBColumnNames = wrapper.getColumnNames();
            if (!iotDBColumnNames.get(1).startsWith("string_contains")) {
                throw new IOException("method 'string_contains' must be first in select SQL");
            }
            List<String> columnNames = new ArrayList<>(iotDBColumnNames.size());
            iotDBColumnNames.forEach(iotDBColumnName ->
                    columnNames.add(iotDBColumnName.substring(iotDBColumnName.lastIndexOf(".") + 1)));
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                Map<String, Object> map = new HashMap<>();
                if (!rowRecord.getFields().get(1).getBoolV()) {
                    continue;
                }
                for (int i = 0; i < columnNames.size(); i++) {
                    Field field = rowRecord.getFields().get(i);
                    map.put(columnNames.get(i), field.getObjectValue(field.getDataType()));
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

    public List<Long> queryWithSelect(String modelName, String querySQL) throws IOException {
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(storageGroup).append(IoTDBClient.DOT).append(modelName);
        SessionDataSetWrapper wrapper = null;
        try {
            if (!sessionPool.checkTimeseriesExists(devicePath.toString())) {
                return null;
            }
            wrapper = sessionPool.executeQueryStatement(querySQL);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", querySQL, wrapper);
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

    public int queryWithAgg(String modelName, String querySQL) throws IOException {
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(storageGroup).append(IoTDBClient.DOT).append(modelName);
        SessionDataSetWrapper wrapper = null;
        try {
            if (!sessionPool.checkTimeseriesExists(devicePath.toString())) {
                return -1;
            }
            wrapper = sessionPool.executeQueryStatement(querySQL);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", querySQL, wrapper);
            }
            int result = Integer.MIN_VALUE; //TODO check result init value
            if (wrapper.hasNext()) {
                result = wrapper.next().getFields().get(0).getIntV();
            }
            healthChecker.health();
            return result;
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            healthChecker.unHealth(e);
            throw new IOException(e.getMessage() + System.lineSeparator() + "SQL Statement: " + querySQL, e);
        } finally {
            sessionPool.closeResultSet(wrapper);
        }
    }

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
}
