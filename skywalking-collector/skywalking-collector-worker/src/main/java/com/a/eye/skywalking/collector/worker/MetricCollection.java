package com.a.eye.skywalking.collector.worker;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricCollection {
    private Map<String, Metric> metricMap = new HashMap();

    public void put(String timeSlice, String name, Long value) {
        String timeSliceName = name + timeSlice;

        if (metricMap.containsKey(timeSliceName)) {
            Long metric = metricMap.get(timeSliceName).getMetricValue();
            metricMap.get(timeSliceName).setMetricValue(metric + value);
        } else {
            metricMap.put(timeSliceName, new Metric(timeSlice, name, value));
        }
    }
}
