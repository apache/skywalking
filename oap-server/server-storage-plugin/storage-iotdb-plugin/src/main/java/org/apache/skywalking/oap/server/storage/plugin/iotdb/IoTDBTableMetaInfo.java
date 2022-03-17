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

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class IoTDBTableMetaInfo {
    private static final Map<String, IoTDBTableMetaInfo> TABLE_META_INFOS = new HashMap<>();

    private final Model model;
    private final Map<String, TSDataType> columnAndTypeMap;
    private final List<String> indexes;

    public static void addModel(Model model) {
        final List<ModelColumn> columns = model.getColumns();
        final Map<String, String> storageAndIndexMap = new HashMap<>();
        final Map<String, TSDataType> columnAndTypeMap = new HashMap<>();
        final List<String> indexes = new ArrayList<>();

        storageAndIndexMap.put(model.getName(), IoTDBIndexes.ID_IDX);
        columns.forEach(column -> {
            String columnName = column.getColumnName().getName();
            if (IoTDBIndexes.isIndex(columnName)) {
                storageAndIndexMap.put(column.getColumnName().getStorageName(), columnName);
            } else {
                columnAndTypeMap.put(columnName, typeToTSDataType(column.getType()));
            }
        });

        // index order: id, entity_id, node_type, service_id, service_group, trace_id
        indexes.add(IoTDBIndexes.ID_IDX);
        if (storageAndIndexMap.containsValue(IoTDBIndexes.ENTITY_ID_IDX)) {
            indexes.add(IoTDBIndexes.ENTITY_ID_IDX);
        }
        if (storageAndIndexMap.containsValue(IoTDBIndexes.LAYER_IDX)) {
            indexes.add(IoTDBIndexes.LAYER_IDX);
        }
        if (storageAndIndexMap.containsValue(IoTDBIndexes.SERVICE_ID_IDX)) {
            indexes.add(IoTDBIndexes.SERVICE_ID_IDX);
        }
        if (storageAndIndexMap.containsValue(IoTDBIndexes.GROUP_IDX)) {
            indexes.add(IoTDBIndexes.GROUP_IDX);
        }
        if (storageAndIndexMap.containsValue(IoTDBIndexes.TRACE_ID_IDX)) {
            indexes.add(IoTDBIndexes.TRACE_ID_IDX);
        }
        if (storageAndIndexMap.containsValue(IoTDBIndexes.INSTANCE_ID_INX)) {
            indexes.add(IoTDBIndexes.INSTANCE_ID_INX);
        }
        if (storageAndIndexMap.containsValue(IoTDBIndexes.PROCESS_ID_INX)) {
            indexes.add(IoTDBIndexes.PROCESS_ID_INX);
        }
        if (storageAndIndexMap.containsValue(IoTDBIndexes.AGENT_ID_INX)) {
            indexes.add(IoTDBIndexes.AGENT_ID_INX);
        }

        final IoTDBTableMetaInfo tableMetaInfo = IoTDBTableMetaInfo.builder().model(model)
                .columnAndTypeMap(columnAndTypeMap).indexes(indexes).build();
        TABLE_META_INFOS.put(model.getName(), tableMetaInfo);
    }

    public static IoTDBTableMetaInfo get(String moduleName) {
        return TABLE_META_INFOS.get(moduleName);
    }

    private static TSDataType typeToTSDataType(Class<?> type) {
        if (Integer.class.equals(type) || int.class.equals(type) || Layer.class.equals(type)) {
            return TSDataType.INT32;
        } else if (Long.class.equals(type) || long.class.equals(type)) {
            return TSDataType.INT64;
        } else if (Float.class.equals(type) || float.class.equals(type)) {
            return TSDataType.FLOAT;
        } else if (Double.class.equals(type) || double.class.equals(type)) {
            return TSDataType.DOUBLE;
        } else if (Boolean.class.equals(type) || boolean.class.equals(type)) {
            return TSDataType.BOOLEAN;
        } else if (String.class.equals(type)) {
            return TSDataType.TEXT;
        } else if (StorageDataComplexObject.class.isAssignableFrom(type)) {
            return TSDataType.TEXT;
        } else if (byte[].class.equals(type)) {
            return TSDataType.TEXT;
        } else if (JsonObject.class.equals(type)) {
            return TSDataType.TEXT;
        } else if (List.class.isAssignableFrom(type)) {
            return TSDataType.TEXT;
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type.getName());
        }
    }
}
