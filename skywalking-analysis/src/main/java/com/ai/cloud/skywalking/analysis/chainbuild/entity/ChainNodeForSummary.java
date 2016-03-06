package com.ai.cloud.skywalking.analysis.chainbuild.entity;

import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;

public class ChainNodeForSummary {

    private String traceLevelId;
    private String viewPointId;

    public ChainNodeForSummary(ChainNode node) {
        this.traceLevelId = node.getTraceLevelId();
        this.viewPointId = node.getViewPoint();
    }

    public void summary(ChainNode node) {

    }
}
