package com.ai.cloud.skywalking.analysis.chain2summary.entity;

import com.ai.cloud.skywalking.analysis.categorize2chain.entity.ChainNodeSpecificTimeWindowSummary;
import com.ai.cloud.skywalking.analysis.categorize2chain.entity.ChainNodeSpecificTimeWindowSummaryValue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

public class ChainNodeSpecificMinSummary {

    private String traceLevelId;
    // key: 分钟  value: 统计结果
    private Map<String, ChainNodeSpecificTimeWindowSummaryValue> summerValueMap;


    public  ChainNodeSpecificMinSummary(){
        summerValueMap = new HashMap<String, ChainNodeSpecificTimeWindowSummaryValue>();
    }

    public ChainNodeSpecificMinSummary(String originData) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(originData);
        traceLevelId = jsonObject.get("traceLevelId").getAsString();
        summerValueMap = new Gson().fromJson(jsonObject.get("summerValueMap").toString(),
                new TypeToken<Map<String, ChainNodeSpecificTimeWindowSummaryValue>>() {
                }.getType());
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    public void summary(ChainNodeSpecificTimeWindowSummary value) {
        for (Map.Entry<String, ChainNodeSpecificTimeWindowSummaryValue> entry :value.getSummerValueMap().entrySet()){
            if (summerValueMap.get(entry.getKey()) == null){
                summerValueMap.put(entry.getKey(), new ChainNodeSpecificTimeWindowSummaryValue());
            }
            summerValueMap.get(entry.getKey()).accumulate(entry.getValue());
        }
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
