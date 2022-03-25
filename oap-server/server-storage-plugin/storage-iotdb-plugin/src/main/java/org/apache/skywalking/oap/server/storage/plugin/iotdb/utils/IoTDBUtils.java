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
