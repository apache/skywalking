package com.ai.cloud.skywalking.web.dto;

import com.ai.cloud.skywalking.web.util.TokenGenerator;

/**
 * Created by xin on 16-4-28.
 */
public class TypicalCallTreeNode {
    private String nodeToken;
    private String levelId;
    private String viewpoint;


    public TypicalCallTreeNode(String levelId, String viewPoint) {
        this.levelId = levelId;
        this.viewpoint = viewPoint;
        this.nodeToken = TokenGenerator.generate(levelId + ":" + viewPoint);
    }
}
