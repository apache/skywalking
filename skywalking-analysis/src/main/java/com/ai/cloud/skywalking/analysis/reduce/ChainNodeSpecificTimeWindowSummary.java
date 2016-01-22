package com.ai.cloud.skywalking.analysis.reduce;

import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

public class ChainNodeSpecificTimeWindowSummary {

    private String traceLevelId;

    private Map<String, SummaryResult> summerResultMap;

    public static ChainNodeSpecificTimeWindowSummary newInstance(String traceLevelId) {
        ChainNodeSpecificTimeWindowSummary cns = new ChainNodeSpecificTimeWindowSummary();
        cns.traceLevelId = traceLevelId;
        return cns;
    }

    private ChainNodeSpecificTimeWindowSummary() {
        summerResultMap = new HashMap<String, SummaryResult>();
    }

    public ChainNodeSpecificTimeWindowSummary(String value) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(value);
        traceLevelId = jsonObject.get("traceLevelId").getAsString();
        summerResultMap = new Gson().fromJson(jsonObject.get("summerResultMap").toString(),
                new TypeToken<Map<String, SummaryResult>>() {
                }.getType());
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    public void summary(ChainNode node) {
        String key = generateKey(node.getStartDate());
        SummaryResult summaryResult = summerResultMap.get(key);
        if (summaryResult == null) {
            summaryResult = new SummaryResult();
            summerResultMap.put(key, summaryResult);
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
