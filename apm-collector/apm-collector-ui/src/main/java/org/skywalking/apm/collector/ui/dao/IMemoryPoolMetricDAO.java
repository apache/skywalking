package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public interface IMemoryPoolMetricDAO {
    JsonObject getMetric(int instanceId, long timeBucket, int poolType);

    JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket, int poolType);
}
