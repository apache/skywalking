package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import java.util.HashMap;
import java.util.Map;

import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class ChainNodeSpecificMinSummary {
    /**
     * key : 分钟
     * value: 各节点统计数据
     */
    private Map<String, ChainNodeSpecificTimeWindowSummaryValue> summaryValueMap;

    public ChainNodeSpecificMinSummary(String originData) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(originData);
        summaryValueMap = new Gson().fromJson(jsonObject.get("summaryValueMap").toString(),
                new TypeToken<Map<String, ChainNodeSpecificTimeWindowSummaryValue>>() {
                }.getType());
    }

    public ChainNodeSpecificMinSummary() {
        summaryValueMap = new HashMap<String, ChainNodeSpecificTimeWindowSummaryValue>();
    }

    public void summary(String minute, ChainNode node) {
        ChainNodeSpecificTimeWindowSummaryValue summarValue = summaryValueMap.get(minute);
        if (summarValue == null) {
            summarValue = new ChainNodeSpecificTimeWindowSummaryValue();
            summaryValueMap.put(minute, summarValue);
        }

        summarValue.summary(node);
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
