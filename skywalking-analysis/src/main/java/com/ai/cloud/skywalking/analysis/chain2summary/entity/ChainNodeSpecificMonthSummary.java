package com.ai.cloud.skywalking.analysis.chain2summary.entity;

import com.ai.cloud.skywalking.analysis.categorize2chain.entity.ChainNodeSpecificTimeWindowSummary;
import com.ai.cloud.skywalking.analysis.categorize2chain.entity.ChainNodeSpecificTimeWindowSummaryValue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ChainNodeSpecificMonthSummary {

    private Map<String, ChainNodeSpecificTimeWindowSummaryValue> summaryValueMap;
    private String traceLevelId;
    private String key;

    public ChainNodeSpecificMonthSummary() {
        summaryValueMap = new HashMap<String, ChainNodeSpecificTimeWindowSummaryValue>();
    }

    public ChainNodeSpecificMonthSummary(String originData) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(originData);
        traceLevelId = jsonObject.get("traceLevelId").getAsString();
        summaryValueMap = new Gson().fromJson(jsonObject.get("summaryValueMap").toString(),
                new TypeToken<Map<String, ChainNodeSpecificTimeWindowSummaryValue>>() {
                }.getType());
    }

    public void summary(long summaryTimestamp, ChainNodeSpecificTimeWindowSummary value) {
        key = generateSummaryValueMapKey(summaryTimestamp);
        for (Map.Entry<String, ChainNodeSpecificTimeWindowSummaryValue> entry : value.getSummerValueMap().entrySet()) {
            if (summaryValueMap.get(key) == null) {
                summaryValueMap.put(key, new ChainNodeSpecificTimeWindowSummaryValue());
            }
            summaryValueMap.get(key).accumulate(entry.getValue());
        }
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    private String generateSummaryValueMapKey(long timeStamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(timeStamp));
        return String.valueOf(calendar.get(Calendar.MONTH));
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
