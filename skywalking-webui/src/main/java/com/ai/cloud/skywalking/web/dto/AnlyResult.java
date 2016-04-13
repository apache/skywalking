package com.ai.cloud.skywalking.web.dto;

/**
 * Created by xin on 16-4-12.
 */
public class AnlyResult {
    private long totalCall;
    private long totalCostTime;
    private long correctNumber;
    private long humanInterruptionNumber;

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
}
