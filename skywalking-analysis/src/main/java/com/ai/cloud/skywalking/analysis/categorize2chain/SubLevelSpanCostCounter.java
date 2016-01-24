package com.ai.cloud.skywalking.analysis.categorize2chain;

import java.util.HashMap;
import java.util.Map;

public class SubLevelSpanCostCounter {
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
