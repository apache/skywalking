package com.ai.cloud.skywalking.analysis.categorize2chain;

import com.ai.cloud.skywalking.analysis.categorize2chain.model.ChainNode;

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

    public void setTotalCall(long totalCall) {
        this.totalCall = totalCall;
    }

    public long getTotalCostTime() {
        return totalCostTime;
    }

    public void setTotalCostTime(long totalCostTime) {
        this.totalCostTime = totalCostTime;
    }

    public long getCorrectNumber() {
        return correctNumber;
    }
    
    public long getHumanInterruptionNumber() {
        return humanInterruptionNumber;
    }

    public void setCorrectNumber(long correctNumber) {
        this.correctNumber = correctNumber;
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
