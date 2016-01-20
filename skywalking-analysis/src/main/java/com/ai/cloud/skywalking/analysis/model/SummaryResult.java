package com.ai.cloud.skywalking.analysis.model;

public class SummaryResult {
    private long totalCall;
    private long totalCostTime;
    private long correctNumber;

    public SummaryResult() {
        totalCall = 0;
        totalCostTime = 0;
        correctNumber = 0;
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

    public void setCorrectNumber(long correctNumber) {
        this.correctNumber = correctNumber;
    }

    public void summary(ChainNode node) {

        totalCall++;
        if (node.getStatus() == ChainNode.NodeStatus.NORMAL) {
            correctNumber++;
        }

        totalCostTime += node.getCost();
    }
}
