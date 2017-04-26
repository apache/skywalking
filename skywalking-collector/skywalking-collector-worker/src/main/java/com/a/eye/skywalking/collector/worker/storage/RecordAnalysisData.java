package com.a.eye.skywalking.collector.worker.storage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class RecordAnalysisData {

    private WindowData<RecordData> windowData = new WindowData(new LinkedHashMap<String, RecordData>());

    public RecordData getOrCreate(String id) {
        if (!windowData.containsKey(id)) {
            windowData.put(id, new RecordData(id));
        }
        return windowData.get(id);
    }

    public Map<String, RecordData> asMap() {
        return windowData.asMap();
    }
}
