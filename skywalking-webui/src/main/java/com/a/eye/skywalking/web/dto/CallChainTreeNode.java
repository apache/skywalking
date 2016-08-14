package com.a.eye.skywalking.web.dto;

import com.a.eye.skywalking.web.util.StringUtil;
import com.a.eye.skywalking.web.util.TokenGenerator;

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
