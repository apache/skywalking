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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.utils;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;

public class IoTDBDataConverter {

    public static class ToEntity implements Convert2Entity {
        private final Map<String, Object> map;

        public ToEntity(String modelName,
                        RowRecord rowRecord,
                        IoTDBTableMetaInfo tableMetaInfo,
                        List<String> indexes,
                        List<String> columnNames) {
            this.map = new ConcurrentHashMap<>();

            List<Field> fields = rowRecord.getFields();
            // field.get(0) -> Device, transform layerName to indexValue
            String[] layerNames = fields.get(0).getStringValue().split("\\" + IoTDBClient.DOT + "\"");
            for (int i = 0; i < indexes.size(); i++) {
                map.put(indexes.get(i), IoTDBUtils.layerName2IndexValue(layerNames[i + 1]));
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

            // transform timestamp to time_bucket
            if (!UITemplate.INDEX_NAME.equals(modelName)) {
                map.put(IoTDBClient.TIME_BUCKET, TimeBucket.getTimeBucket(
                        rowRecord.getTimestamp(),
                        tableMetaInfo.getModel().getDownsampling()
                ));
            }
            // convert String to Integer for LAYER_IDX value
            if (map.containsKey(IoTDBIndexes.LAYER_IDX)) {
                String layer = (String) map.get(IoTDBIndexes.LAYER_IDX);
                map.put(IoTDBIndexes.LAYER_IDX, Integer.valueOf(layer));
            }
            // add timestamp property for some entities
            if (modelName.equals(BrowserErrorLogRecord.INDEX_NAME) || modelName.equals(LogRecord.INDEX_NAME)) {
                map.put(IoTDBClient.TIMESTAMP, map.get("\"" + IoTDBClient.TIMESTAMP + "\""));
            }
            // remove double quotes
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                if (key.contains(".")) {
                    map.put(key.substring(1, key.length() - 1), entry.getValue());
                }
            }
        }

        @Override
        public Object get(String fieldName) {
            return map.get(fieldName);
        }

        @Override
        public <T, R> R getWith(final String fieldName, final Function<T, R> typeDecoder) {
            return null;
        }
    }

    public static class ToStorage implements Convert2Storage<IoTDBInsertRequest> {
        private final IoTDBInsertRequest request;
        private final IoTDBTableMetaInfo tableMetaInfo;
        private final String id;
        private final Map<String, String> indexMap;

        public ToStorage(String modelName, long time, String id) {
            this.request = new IoTDBInsertRequest(modelName, time);
            this.tableMetaInfo = IoTDBTableMetaInfo.get(modelName);
            this.id = id;
            this.indexMap = new HashMap<>(this.tableMetaInfo.getIndexes().size());
        }

        @Override
        public void accept(final String fieldName, final Object fieldValue) {
            if (IoTDBIndexes.isIndex(fieldName) && fieldValue != null) {
                indexMap.put(fieldName, fieldValue.toString());
            } else {
                // time_bucket has changed to time before calling this method,
                // IoTDB doesn't allow insert null value,
                // so they don't need to be stored.
                if (fieldName.equals(IoTDBClient.TIME_BUCKET) || fieldValue == null) {
                    return;
                }

                List<String> measurements = request.getMeasurements();
                List<TSDataType> measurementTypes = request.getMeasurementTypes();
                List<Object> measurementValues = request.getMeasurementValues();
                // IoTDB doesn't allow a measurement named `timestamp` or contains `.`
                if (fieldName.equals(IoTDBClient.TIMESTAMP) || fieldName.contains(".")) {
                    measurements.add("\"" + fieldName + "\"");
                } else {
                    measurements.add(fieldName);
                }
                measurementTypes.add(tableMetaInfo.getColumnAndTypeMap().get(fieldName));
                if (fieldValue instanceof StorageDataComplexObject) {
                    measurementValues.add(((StorageDataComplexObject) fieldValue).toStorageData());
                } else {
                    measurementValues.add(fieldValue);
                }
            }
        }

        @Override
        public void accept(final String fieldName, final byte[] fieldValue) {
            request.getMeasurements().add(fieldName);
            request.getMeasurementTypes().add(tableMetaInfo.getColumnAndTypeMap().get(fieldName));
            if (CollectionUtils.isEmpty(fieldValue)) {
                request.getMeasurementValues().add(Const.EMPTY_STRING);
            } else {
                request.getMeasurementValues().add(new String(Base64.getEncoder().encode(fieldValue)));
            }
        }

        @Override
        public void accept(final String fieldName, final List<String> fieldValue) {
            request.getMeasurements().add(fieldName);
            request.getMeasurementTypes().add(tableMetaInfo.getColumnAndTypeMap().get(fieldName));
            request.getMeasurementValues().add(fieldValue);
        }

        @Override
        public Object get(String fieldName) {
            if (indexMap.containsKey(fieldName)) {
                return indexMap.get(fieldName);
            } else {
                return request.getMeasurementValues().get(request.getMeasurements().indexOf(fieldName));
            }
        }

        @Override
        public IoTDBInsertRequest obtain() {
            // sort indexValues
            List<String> indexes = request.getIndexes();
            List<String> indexValues = request.getIndexValues();
            for (String index : indexes) {
                if (index.equals(IoTDBIndexes.ID_IDX)) {
                    indexValues.add(id);
                } else {
                    // avoid indexValue be "null" when inserting
                    indexValues.add(indexMap.getOrDefault(index, Const.EMPTY_STRING));
                }
            }
            return request;
        }
    }
}
