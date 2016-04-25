package com.ai.cloud.skywalking.web.dto;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.Calendar;
import java.util.Map;

/**
 * Created by xin on 16-4-25.
 */
public class CallChainTreeNode {

    private String traceLevelId;
    private String viewPoint;
    private AnlyResult anlyResult;

    public CallChainTreeNode(String qualifierStr, String valueStr, String loadKey) {
        String[] qualifierArray = qualifierStr.split("@");
        traceLevelId = qualifierArray[0];
        viewPoint = qualifierArray[1];
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(valueStr);
        Map<String, AnlyResult> resultMap = new Gson().fromJson(jsonObject.getAsJsonObject("summaryValueMap"),
                new TypeToken<Map<String, AnlyResult>>() {
                }.getType());
        anlyResult = resultMap.get(loadKey);

    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    public String getViewPoint() {
        return viewPoint;
    }
}
