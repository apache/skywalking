package com.ai.cloud.skywalking.analysis.chain2summary.model;

import com.ai.cloud.skywalking.analysis.categorize2chain.ChainNodeSpecificTimeWindowSummary;
import com.ai.cloud.skywalking.analysis.categorize2chain.ChainNodeSpecificTimeWindowSummaryValue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

public class ChainNodeSpecificHourSummary {
    private String traceLevelId;
    // key : 小时
    private Map<String, ChainNodeSpecificTimeWindowSummaryValue> summerValueMap;

    public ChainNodeSpecificHourSummary(String originData) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(originData);
        traceLevelId = jsonObject.get("traceLevelId").getAsString();
        summerValueMap = new Gson().fromJson(jsonObject.get("summerValueMap").toString(),
                new TypeToken<Map<String, ChainNodeSpecificTimeWindowSummaryValue>>() {
                }.getType());
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    public void summary(long summaryTimestamp, ChainNodeSpecificTimeWindowSummary value) {
        for (Map.Entry<String, ChainNodeSpecificTimeWindowSummaryValue> entry : value.getSummerValueMap().entrySet()) {
            summerValueMap.get(generateSummaryValueMapKey(summaryTimestamp)).accumulate(entry.getValue());
        }
    }

    private String generateSummaryValueMapKey(long timeStamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(timeStamp));
        return String.valueOf(calendar.get(Calendar.HOUR));
    }
}
