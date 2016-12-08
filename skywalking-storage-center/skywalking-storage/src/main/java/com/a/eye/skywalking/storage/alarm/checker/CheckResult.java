package com.a.eye.skywalking.storage.alarm.checker;

/**
 * Created by xin on 2016/12/8.
 */
public class CheckResult {
    private boolean passed;
    private FatalReason fatalReason;
    private String message;

    public CheckResult() {
        this.passed = true;
    }

    public CheckResult(FatalReason level, String alarmMessage) {
        this.passed = false;
        this.fatalReason = level;
        this.message = alarmMessage;
    }

    public boolean isPassed() {
        return passed;
    }

    public FatalReason getFatalReason() {
        return fatalReason;
    }

    public String getMessage() {
        return message;
    }
}
