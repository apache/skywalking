package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public interface ISegmentCostDAO {
    JsonObject loadTop(long startTime, long endTime, long minCost, long maxCost, String operationName,
        String globalTraceId, int limit, int from, Sort sort);

    public enum Sort {
        Cost, Time
    }
}
