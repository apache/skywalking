package com.a.eye.skywalking.collector.worker;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class RecordCollection {

    private Map<String, JsonObject> recordMap = new HashMap();

    public void put(String timeSlice, String primaryKey, JsonObject valueObj) {
        recordMap.put(timeSlice + primaryKey, valueObj);
    }
}
