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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
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
        private final IoTDBTableMetaInfo tableMetaInfo;
        private final List<String> indexValues;
        private final List<String> columnNames;
        private final long timestamp;
        private final List<Field> fields;

        public ToEntity(IoTDBTableMetaInfo tableMetaInfo, List<String> indexes,
                        List<String> columnNames, RowRecord rowRecord) {
            this.tableMetaInfo = tableMetaInfo;
            this.indexValues = new ArrayList<>(tableMetaInfo.getIndexes().size());
            this.columnNames = columnNames;
            this.timestamp = rowRecord.getTimestamp();
            this.fields = rowRecord.getFields();
            // field.get(0) -> Device, transform every layerName to indexValue
            String[] layerNames = fields.get(0).getStringValue().split("\\" + IoTDBClient.DOT + "\"");
            for (int i = 0; i < indexes.size(); i++) {
                indexValues.add(IoTDBUtils.layerName2IndexValue(layerNames[i + 1]));
            }
        }

        @Override
        public Object get(String fieldName) {
            if (IoTDBIndexes.isIndex(fieldName)) {
                String indexValue = indexValues.get(tableMetaInfo.getIndexes().indexOf(fieldName));
                // convert String to Integer for LAYER_IDX value
                return IoTDBIndexes.LAYER_IDX.equals(fieldName) ? Integer.valueOf(indexValue) : indexValue;
            } else {
                // time_bucket has changed to timestamp when writing to IoTDB.
                if (IoTDBClient.TIME_BUCKET.equals(fieldName)) {
                    return TimeBucket.getTimeBucket(timestamp, tableMetaInfo.getModel().getDownsampling());
                }
                // IoTDB doesn't allow a measurement named `timestamp` or contains `.`,
                // so we add double quotation mark to them.
                // Also see IoTDBDataConverter.ToStorage.accept(String, Object)
                if (IoTDBClient.TIMESTAMP.equals(fieldName) || fieldName.contains(".")) {
                    String columnName = IoTDBUtils.addQuotationMark(fieldName);
                    return IoTDBUtils.getFieldValue(fields.get(columnNames.indexOf(columnName) - 1));
                } else {
                    return IoTDBUtils.getFieldValue(fields.get(columnNames.indexOf(fieldName) - 1));
                }
            }
        }

        @Override
        public <T, R> R getWith(final String fieldName, final Function<T, R> typeDecoder) {
            final T value = (T) IoTDBUtils.getFieldValue(fields.get(columnNames.indexOf(fieldName) - 1));
            return typeDecoder.apply(value);
        }
    }

    public static class ToStorage implements Convert2Storage<IoTDBInsertRequest> {
        private final IoTDBInsertRequest request;
        private final IoTDBTableMetaInfo tableMetaInfo;

        public ToStorage(String modelName, long time, String id) {
            this.request = new IoTDBInsertRequest(modelName, time);
            this.tableMetaInfo = IoTDBTableMetaInfo.get(modelName);
            // set id as an index
            this.request.getIndexValues().set(this.request.getIndexes().indexOf(IoTDBIndexes.ID_IDX), id);
        }

        @Override
        public void accept(final String fieldName, final Object fieldValue) {
            if (IoTDBIndexes.isIndex(fieldName)) {
                List<String> indexes = request.getIndexes();
                List<String> indexValues = request.getIndexValues();
                // To avoid indexValue be "null" when inserting, replace null to empty string
                indexValues.set(indexes.indexOf(fieldName),
                                fieldValue == null ? Const.EMPTY_STRING : fieldValue.toString());
            } else {
                // time_bucket has changed to timestamp before calling this method,
                // and IoTDB v0.12 doesn't allow insert null value,
                // so they don't need to be stored.
                if (fieldName.equals(IoTDBClient.TIME_BUCKET) || fieldValue == null) {
                    return;
                }

                List<String> measurements = request.getMeasurements();
                List<TSDataType> measurementTypes = request.getMeasurementTypes();
                List<Object> measurementValues = request.getMeasurementValues();
                // IoTDB doesn't allow a measurement named `timestamp` or contains `.`
                if (fieldName.equals(IoTDBClient.TIMESTAMP) || fieldName.contains(".")) {
                    measurements.add(IoTDBUtils.addQuotationMark(fieldName));
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
            if (IoTDBIndexes.isIndex(fieldName)) {
                return request.getIndexValues().get(request.getIndexes().indexOf(fieldName));
            } else {
                return request.getMeasurementValues().get(request.getMeasurements().indexOf(fieldName));
            }
        }

        @Override
        public IoTDBInsertRequest obtain() {
            return request;
        }
    }
}
