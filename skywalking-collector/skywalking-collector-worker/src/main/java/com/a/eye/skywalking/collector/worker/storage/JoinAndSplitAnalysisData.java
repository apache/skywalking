package com.a.eye.skywalking.collector.worker.storage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class JoinAndSplitAnalysisData {

    private WindowData<JoinAndSplitData> windowData = new WindowData(new LinkedHashMap<String, JoinAndSplitData>());

    public JoinAndSplitData getOrCreate(String id) {
        if (!windowData.containsKey(id)) {
            windowData.put(id, new JoinAndSplitData(id));
        }
        return windowData.get(id);
    }

    public Map<String, JoinAndSplitData> asMap() {
        return windowData.asMap();
    }
}
