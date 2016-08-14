package com.a.eye.skywalking.analysis.chainbuild.entity;

import com.a.eye.skywalking.analysis.chainbuild.po.ChainNode;

public class ChainNodeSpecificTimeWindowSummaryValue {
    private long totalCall;
    private long totalCostTime;
    private long correctNumber;
    private long humanInterruptionNumber;

    public ChainNodeSpecificTimeWindowSummaryValue() {
        totalCall = 0;
        totalCostTime = 0;
        correctNumber = 0;
        humanInterruptionNumber = 0;
    }


    public void summary(ChainNode node) {
        totalCall++;
        if (node.getStatus() == ChainNode.NodeStatus.NORMAL) {
            correctNumber++;
        }
        if (node.getStatus() == ChainNode.NodeStatus.HUMAN_INTERRUPTION) {
            humanInterruptionNumber++;
        }
        totalCostTime += node.getCost();
    }
}
