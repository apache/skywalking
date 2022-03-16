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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.base;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;

@Getter
@Setter
@ToString
@Slf4j
public class IoTDBInsertRequest implements InsertRequest, UpdateRequest {
    private String modelName;
    private long time;
    private List<String> indexes;
    private List<String> indexValues;
    private List<String> measurements;
    private List<TSDataType> measurementTypes;
    private List<Object> measurementValues;

    public <T extends StorageData> IoTDBInsertRequest(String modelName, long time, T storageData,
                                                      StorageBuilder<T> storageBuilder) {
        this.modelName = modelName;
        this.time = time;
        indexes = IoTDBTableMetaInfo.get(modelName).getIndexes();
        indexValues = new ArrayList<>(indexes.size());
        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(storageData, toStorage);
        Map<String, Object> storageMap = toStorage.obtain();

        indexes.forEach(index -> {
            if (index.equals(IoTDBIndexes.ID_IDX)) {
                indexValues.add(storageData.id());
            } else if (storageMap.containsKey(index)) {
                // avoid indexValue be "null" when inserting
                if (storageMap.get(index) == null) {
                    indexValues.add("");
                } else {
                    indexValues.add(String.valueOf(storageMap.get(index)));
                }
                storageMap.remove(index);
            }
        });

        // time_bucket has changed to time before calling this method, so remove it from measurements
        storageMap.remove(IoTDBClient.TIME_BUCKET);
        // processing value to make it suitable for storage
        Iterator<Map.Entry<String, Object>> entryIterator = storageMap.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<String, Object> entry = entryIterator.next();
            // IoTDB doesn't allow insert null value.
            if (entry.getValue() == null) {
                entryIterator.remove();
            }
            if (entry.getValue() instanceof StorageDataComplexObject) {
                storageMap.put(entry.getKey(), ((StorageDataComplexObject) entry.getValue()).toStorageData());
            }
        }

        measurements = new ArrayList<>(storageMap.keySet());
        Map<String, TSDataType> columnAndTypeMap = IoTDBTableMetaInfo.get(modelName).getColumnAndTypeMap();
        measurementTypes = new ArrayList<>(measurements.size());
        for (String measurement : measurements) {
            measurementTypes.add(columnAndTypeMap.get(measurement));
        }
        measurementValues = new ArrayList<>(storageMap.values());

        // IoTDB doesn't allow a measurement named `timestamp` or contains `.`
        for (String key : storageMap.keySet()) {
            if (key.equals(IoTDBClient.TIMESTAMP) || key.contains(".")) {
                int idx = measurements.indexOf(key);
                measurements.set(idx, "\"" + key + "\"");
            }
        }
    }
}
