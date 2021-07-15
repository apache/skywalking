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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class IoTDBInsertRequest implements InsertRequest, UpdateRequest {

    private String deviceName;
    private long time;
    private List<String> measurements;
    private List<String> values;

    public <T extends StorageData> IoTDBInsertRequest(String modelName, long time, T storageData,
                                                      StorageHashMapBuilder<T> storageBuilder) {
        deviceName = modelName;
        this.time = time;
        Map<String, Object> storageMap = storageBuilder.entity2Storage(storageData);
        measurements = new ArrayList<>(storageMap.keySet());
        List<Object> objectValues = new ArrayList<>(storageMap.values());
        values = new ArrayList<>();
        objectValues.forEach(objectValue -> values.add(objectValue.toString()));

        measurements.add(IoTDBClient.ID_COLUMN);
        values.add(storageData.id());
    }
}
