package com.a.eye.skywalking.collector.worker.storage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MergeAnalysisData {

    private WindowData<MergeData> windowData = new WindowData(new LinkedHashMap<String, MergeData>());

    public MergeData getOrCreate(String id) {
        if (!windowData.containsKey(id)) {
            windowData.put(id, new MergeData(id));
        }
        return windowData.get(id);
    }

    public Map<String, MergeData> asMap() {
        return windowData.asMap();
    }
}
