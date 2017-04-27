package com.a.eye.skywalking.collector.worker.storage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricAnalysisData {

    private WindowData<MetricData> windowData = new WindowData(new LinkedHashMap<String, MetricData>());

    public MetricData getOrCreate(String id) {
        if (!windowData.containsKey(id)) {
            windowData.put(id, new MetricData(id));
        }
        return windowData.get(id);
    }

    public Map<String, MetricData> asMap() {
        return windowData.asMap();
    }
}
