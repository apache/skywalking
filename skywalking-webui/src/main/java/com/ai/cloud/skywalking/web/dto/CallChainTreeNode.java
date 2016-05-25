package com.ai.cloud.skywalking.web.dto;

import com.ai.cloud.skywalking.web.util.StringUtil;
import com.ai.cloud.skywalking.web.util.TokenGenerator;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.Map;

/**
 * Created by xin on 16-4-25.
 */
public class CallChainTreeNode {

    private String nodeToken;
    private String traceLevelId;
    private String viewPoint;
    private String viewPointStr;
    private AnlyResult anlyResult;

    public CallChainTreeNode(String qualifierStr, AnlyResult anlyResult) {
        traceLevelId = qualifierStr.substring(0, qualifierStr.indexOf("@"));
        viewPoint = qualifierStr.substring(qualifierStr.indexOf("@") + 1);
        viewPointStr = viewPoint;
        nodeToken = TokenGenerator.generate(traceLevelId + ":" + viewPoint);
        this.anlyResult = anlyResult;
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    public String getViewPoint() {
        return viewPoint;
    }

    public void beautifulViewPoint() {
        if (!StringUtil.isBlank(viewPoint) && viewPoint.length() > 80) {
            viewPoint = viewPoint.substring(0, 50) + "..."
                    + viewPoint.substring(viewPoint.length() - 50);
        }
    }

    public String getNodeToken() {
        return nodeToken;
    }
}
