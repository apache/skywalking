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
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.utils.IoTDBDataConverter;

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

    public IoTDBInsertRequest(String modelName, long time) {
        this.modelName = modelName;
        this.time = time;
        this.indexes = IoTDBTableMetaInfo.get(modelName).getIndexes();
        this.indexValues = new ArrayList<>(indexes.size());

        int measurementsSize = IoTDBTableMetaInfo.get(modelName).getColumnAndTypeMap().size();
        this.measurements = new ArrayList<>(measurementsSize);
        this.measurementTypes = new ArrayList<>(measurementsSize);
        this.measurementValues = new ArrayList<>(measurementsSize);
    }

    public static <T extends StorageData> IoTDBInsertRequest buildRequest(String modelName, long time, T storageData,
                                                                          StorageBuilder<T> storageBuilder) {
        String id = storageData.id();
        IoTDBDataConverter.ToStorage toStorage = new IoTDBDataConverter.ToStorage(modelName, time, id);
        storageBuilder.entity2Storage(storageData, toStorage);
        return toStorage.obtain();
    }
}
