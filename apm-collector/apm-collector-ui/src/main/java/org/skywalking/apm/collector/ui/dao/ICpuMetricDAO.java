package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;

/**
 * @author pengys5
 */
public interface ICpuMetricDAO {
    int getMetric(int instanceId, long timeBucket);

    JsonArray getMetric(int instanceId, long startTimeBucket, long endTimeBucket);
}
