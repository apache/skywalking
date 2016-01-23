package com.ai.cloud.skywalking.analysis.categorize2chain;

import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainNode;
import com.ai.cloud.skywalking.analysis.config.Config;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

public class ChainNodeSpecificTimeWindowSummary {

    private String traceLevelId;

    private Map<String, ChainNodeSpecificTimeWindowSummaryValue> summerValueMap;

    public static ChainNodeSpecificTimeWindowSummary newInstance(String traceLevelId) {
        ChainNodeSpecificTimeWindowSummary cns = new ChainNodeSpecificTimeWindowSummary();
        cns.traceLevelId = traceLevelId;
        return cns;
    }

    private ChainNodeSpecificTimeWindowSummary() {
        summerValueMap = new HashMap<String, ChainNodeSpecificTimeWindowSummaryValue>();
    }

    public ChainNodeSpecificTimeWindowSummary(String value) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(value);
        traceLevelId = jsonObject.get("traceLevelId").getAsString();
        summerValueMap = new Gson().fromJson(jsonObject.get("summerValueMap").toString(),
                new TypeToken<Map<String, ChainNodeSpecificTimeWindowSummaryValue>>() {
                }.getType());
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    public void summary(ChainNode node) {
        String key = generateKey(node.getStartDate());
        ChainNodeSpecificTimeWindowSummaryValue summaryResult = summerValueMap.get(key);
        if (summaryResult == null) {
            summaryResult = new ChainNodeSpecificTimeWindowSummaryValue();
            summerValueMap.put(key, summaryResult);
        }
        summaryResult.summary(node);
    }

    private String generateKey(long startTime) {
        long minutes = (startTime % (1000 * 60 * 60)) / (1000 * 60);
        return String.valueOf(minutes / Config.ChainNodeSummary.INTERVAL);
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
