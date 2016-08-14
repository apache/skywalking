package com.a.eye.skywalking.analysis.chainbuild.entity;

import com.a.eye.skywalking.analysis.chainbuild.po.ChainNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xin on 16-3-10.
 */
public class ChainNodeSpecificDaySummary {

    /**
     * key : 天
     */
    private Map<String, ChainNodeSpecificTimeWindowSummaryValue> summaryValueMap;

    public ChainNodeSpecificDaySummary(String originData) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(originData);
        summaryValueMap = new Gson().fromJson(jsonObject.get("summaryValueMap").toString(),
                new TypeToken<Map<String, ChainNodeSpecificTimeWindowSummaryValue>>() {
                }.getType());
    }

    public ChainNodeSpecificDaySummary() {
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
