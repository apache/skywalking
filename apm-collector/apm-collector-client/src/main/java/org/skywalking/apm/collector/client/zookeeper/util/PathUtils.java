package org.skywalking.apm.collector.client.zookeeper.util;

/**
 * @author pengys5
 */
public class PathUtils {

    public static String convertKey2Path(String key) {
        String[] keys = key.split("\\.");
        StringBuilder pathBuilder = new StringBuilder();
        for (String subPath : keys) {
            pathBuilder.append("/").append(subPath);
        }
        return pathBuilder.toString();
    }
}
