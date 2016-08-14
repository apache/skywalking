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
public class ChainNodeSpecificMonthSummary {
    /**
     * key : 月
     */
    private Map<String, ChainNodeSpecificTimeWindowSummaryValue> summaryValueMap;

    public ChainNodeSpecificMonthSummary(String originData) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(originData);
        summaryValueMap = new Gson().fromJson(jsonObject.get("summaryValueMap").toString(),
                new TypeToken<Map<String, ChainNodeSpecificTimeWindowSummaryValue>>() {
                }.getType());
    }

    public ChainNodeSpecificMonthSummary() {
        summaryValueMap = new HashMap<String, ChainNodeSpecificTimeWindowSummaryValue>();
    }

    public void summary(String month, ChainNode node) {
        ChainNodeSpecificTimeWindowSummaryValue summarValue = summaryValueMap.get(month);
        if (summarValue == null) {
            summarValue = new ChainNodeSpecificTimeWindowSummaryValue();
            summaryValueMap.put(month, summarValue);
        }

        summarValue.summary(node);
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
