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

import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;

public class IoTDBUtils {
    public static String indexValue2LayerName(String indexValue) {
        return "\"" + indexValue + "\"";
    }

    public static String layerName2IndexValue(String layerName) {
        return layerName.substring(0, layerName.length() - 1);
    }

    public static void addQueryIndexValue(String modelName,
                                          StringBuilder query,
                                          Map<String, String> indexAndValueMap) {
        List<String> indexes = IoTDBTableMetaInfo.get(modelName).getIndexes();
        indexes.forEach(index -> {
            if (indexAndValueMap.containsKey(index)) {
                query.append(IoTDBClient.DOT).append(indexValue2LayerName(indexAndValueMap.get(index)));
            } else {
                query.append(IoTDBClient.DOT).append("*");
            }
        });
    }

    public static void addQueryAsterisk(String modelName, StringBuilder query) {
        List<String> indexes = IoTDBTableMetaInfo.get(modelName).getIndexes();
        indexes.forEach(index -> query.append(IoTDBClient.DOT).append("*"));
    }

    public static void addModelPath(String storageGroup, StringBuilder query, String modelName) {
        query.append(storageGroup).append(IoTDBClient.DOT).append(modelName);
    }
}
