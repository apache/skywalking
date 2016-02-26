package com.ai.cloud.skywalking.analysis.categorize2chain.entity;

import com.ai.cloud.skywalking.analysis.categorize2chain.po.ChainNode;

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

    public long getTotalCall() {
        return totalCall;
    }

    public long getTotalCostTime() {
        return totalCostTime;
    }

    public long getCorrectNumber() {
        return correctNumber;
    }
    
    public long getHumanInterruptionNumber() {
        return humanInterruptionNumber;
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

    public void accumulate(ChainNodeSpecificTimeWindowSummaryValue value) {
        this.totalCall += value.getTotalCall();
        this.correctNumber += value.getCorrectNumber();
        this.totalCostTime += value.getTotalCostTime();
        this.humanInterruptionNumber += value.getHumanInterruptionNumber();
    }
}
