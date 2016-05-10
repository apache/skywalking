package com.ai.cloud.skywalking.web.dto;

import com.ai.cloud.skywalking.web.util.StringUtil;
import com.ai.cloud.skywalking.web.util.TokenGenerator;

/**
 * Created by xin on 16-4-28.
 */
public class TypicalCallTreeNode {
    private String nodeToken;
    private String viewPoint;
    private String levelId;

    public TypicalCallTreeNode(String parentLevelId, String levelId, String viewPoint) {
        if (StringUtil.isBlank(parentLevelId)){
            this.levelId = levelId;
        }else{
            this.levelId = parentLevelId + "." + levelId;
        }

        this.viewPoint = viewPoint;
        nodeToken = TokenGenerator.generate(levelId + ":" + viewPoint);
    }

    public String getNodeToken() {
        return nodeToken;
    }
}
