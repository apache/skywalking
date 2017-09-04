package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public interface IMemoryMetricDAO {
    JsonObject getMetric(int instanceId, long timeBucket, boolean isHeap);

    JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket, boolean isHeap);
}
