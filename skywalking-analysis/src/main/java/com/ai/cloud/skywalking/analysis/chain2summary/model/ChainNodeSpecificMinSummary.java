package com.ai.cloud.skywalking.analysis.chain2summary.model;

import com.ai.cloud.skywalking.analysis.categorize2chain.ChainNodeSpecificTimeWindowSummary;
import com.ai.cloud.skywalking.analysis.categorize2chain.ChainNodeSpecificTimeWindowSummaryValue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.Map;

public class ChainNodeSpecificMinSummary {

    private String traceLevelId;
    // key: 分钟  value: 统计结果
    private Map<String, ChainNodeSpecificTimeWindowSummaryValue> summerValueMap;


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
            summerValueMap.get(entry.getKey()).accumulate(entry.getValue());
        }
    }
}
