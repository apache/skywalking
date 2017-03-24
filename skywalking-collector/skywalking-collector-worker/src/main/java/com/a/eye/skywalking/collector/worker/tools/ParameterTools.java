package com.a.eye.skywalking.collector.worker.tools;

import java.util.Map;

/**
 * @author pengys5
 */
public class ParameterTools {

    public static String toString(Map<String, String[]> request, String key) {
        if (request.get(key) != null) {
            return request.get(key)[0];
        } else {
            return "";
        }
    }

}
