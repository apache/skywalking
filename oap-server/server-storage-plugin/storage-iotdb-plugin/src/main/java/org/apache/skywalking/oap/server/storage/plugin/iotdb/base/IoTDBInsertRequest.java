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
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;

@Getter
@Setter
public class IoTDBInsertRequest implements InsertRequest, UpdateRequest {
    private String modelName;
    private long time;
    private List<String> indexes;
    private List<String> indexValues;
    private List<String> timeseriesList;
    private List<TSDataType> timeseriesTypes;
    private List<Object> timeseriesValues;

    public <T extends StorageData> IoTDBInsertRequest(String modelName, long time, T storageData,
                                                      StorageHashMapBuilder<T> storageBuilder) {
        this.modelName = modelName;
        this.time = time;
        indexes = IoTDBTableMetaInfo.get(modelName).getIndexes();
        indexValues = new ArrayList<>(indexes.size());
        Map<String, Object> storageMap = storageBuilder.entity2Storage(storageData);

        indexes.forEach(index -> {
            // TODO check indexValue is empty or not
            if (index.equals(IoTDBClient.ID_IDX)) {
                indexValues.add(storageData.id());
            } else if (storageMap.containsKey(index)) {
                indexValues.add(String.valueOf(storageMap.get(index)));
                storageMap.remove(index);
            }
        });

        // time_bucket has changed to time before calling this method, so remove it from timeseriesList
        storageMap.remove(IoTDBClient.TIME_BUCKET);
        timeseriesList = new ArrayList<>(storageMap.keySet());
        Map<String, TSDataType> columnTypeMap = IoTDBTableMetaInfo.get(modelName).getColumnTypeMap();
        timeseriesTypes = new ArrayList<>(columnTypeMap.keySet().size());
        timeseriesList.forEach(timeseries -> timeseriesTypes.add(columnTypeMap.get(timeseries)));
        timeseriesValues = new ArrayList<>(storageMap.values());

        if (storageMap.containsKey(IoTDBClient.TIMESTAMP)) {
            int idx = timeseriesList.indexOf(IoTDBClient.TIMESTAMP);
            timeseriesList.set(idx, "\"" + IoTDBClient.TIMESTAMP + "\"");
        }

        storageMap.forEach((String key, Object value) -> {
            if (key.contains(".")) {
                int idx = timeseriesList.indexOf(key);
                timeseriesList.set(idx, "\"" + key + "\"");
            }
        });

        for (int i = 0; i < timeseriesValues.size(); i++) {
            Object timeseriesValue = timeseriesValues.get(i);
            if (timeseriesValue instanceof StorageDataComplexObject) {
                timeseriesValues.set(i, ((StorageDataComplexObject) timeseriesValue).toStorageData());
            }
        }
    }
}
