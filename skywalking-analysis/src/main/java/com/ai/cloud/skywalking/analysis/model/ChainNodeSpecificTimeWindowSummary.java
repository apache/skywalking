package com.ai.cloud.skywalking.analysis.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

public class ChainNodeSpecificTimeWindowSummary {

    private String traceLevelId;

    private Map<String, SummaryResult> summerResultMap;
    
    public static ChainNodeSpecificTimeWindowSummary newInstance(String traceLevelId){
    	ChainNodeSpecificTimeWindowSummary cns = new ChainNodeSpecificTimeWindowSummary();
    	cns.traceLevelId = traceLevelId;
    	return cns;
    }
    
    private ChainNodeSpecificTimeWindowSummary(){
    	summerResultMap = new HashMap<String, SummaryResult>();
    }

    public ChainNodeSpecificTimeWindowSummary(String value) {
        JsonObject jsonObject = new Gson().fromJson(value, JsonObject.class);
        traceLevelId = jsonObject.get("traceLevelId").getAsString();
        summerResultMap = new Gson().fromJson(jsonObject.get("summerResultMap").getAsString(),
                new TypeToken<Map<String, SummaryResult>>() {
                }.getType());
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    public void setTraceLevelId(String traceLevelId) {
        this.traceLevelId = traceLevelId;
    }

    public Map<String, SummaryResult> getSummerResultMap() {
        return summerResultMap;
    }

    public void setSummerResultMap(Map<String, SummaryResult> summerResultMap) {
        this.summerResultMap = summerResultMap;
    }

    public void summary(ChainNode node) {
        SummaryResult summaryResult = summerResultMap.get(generateKey(node.getStartDate()));
        if (summaryResult == null) {
            summaryResult = new SummaryResult();
            summerResultMap.put(node.getTraceLevelId(), summaryResult);
        }
        summaryResult.summary(node);
    }

    private String generateKey(long startTime) {

        return null;
    }
}
