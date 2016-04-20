package com.ai.cloud.skywalking.web.dto;

/**
 * Created by xin on 16-4-12.
 */
public class AnlyResult {
    private String yearOfAnlyResult;
    private String monthOfAnlyResult;
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

    public void setTotalCall(long totalCall) {
        this.totalCall = totalCall;
    }

    public void setTotalCostTime(long totalCostTime) {
        this.totalCostTime = totalCostTime;
    }

    public void setCorrectNumber(long correctNumber) {
        this.correctNumber = correctNumber;
    }

    public void setHumanInterruptionNumber(long humanInterruptionNumber) {
        this.humanInterruptionNumber = humanInterruptionNumber;
    }

    public String getMonthOfAnlyResult() {
        return monthOfAnlyResult;
    }

    public void setMonthOfAnlyResult(String monthOfAnlyResult) {
        this.monthOfAnlyResult = monthOfAnlyResult;
    }

    public AnlyResult(String yearOfAnlyResult, String monthOfAnlyResult) {
        this.yearOfAnlyResult = yearOfAnlyResult;
        this.monthOfAnlyResult = monthOfAnlyResult;
    }

    public AnlyResult() {
    }

    public String getYearOfAnlyResult() {
        return yearOfAnlyResult;
    }

    public void setYearOfAnlyResult(String yearOfAnlyResult) {
        this.yearOfAnlyResult = yearOfAnlyResult;
    }
}
