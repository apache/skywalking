package com.ai.cloud.skywalking.web.entity;

/**
 * Created by xin on 16-4-6.
 */
public class CallChainTreeNode {

    private String traceLevelId;

    private String viewPoint;
    public CallChainTreeNode(String traceLevelId, String viewpoint) {
        this.traceLevelId = traceLevelId;
        this.viewPoint = viewpoint;
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }
}
