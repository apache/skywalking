package com.ai.cloud.skywalking.analysis.model;

import java.util.HashMap;
import java.util.Map;

public class CostMap {
    private Map<String, Long> costs = new HashMap<String, Long>();

    public void put(String parentLevel, Long cost) {
        costs.put(parentLevel, cost);
    }

    public boolean exists(String parentLevel) {
        return costs.containsKey(parentLevel);
    }

    public Long get(String parentLevel) {
        return costs.get(parentLevel);
    }
}
