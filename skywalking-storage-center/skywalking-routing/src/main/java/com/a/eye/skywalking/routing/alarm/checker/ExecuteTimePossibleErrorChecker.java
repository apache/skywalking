package com.a.eye.skywalking.routing.alarm.checker;

/**
 * Created by xin on 2016/12/8.
 */
public class ExecuteTimePossibleErrorChecker extends ExecuteTimeChecker {

    @Override
    protected boolean isOverThreshold(long cost) {
        return cost >= 3000;
    }

    @Override
    protected FatalReason getFatalLevel() {
        return FatalReason.EXECUTE_TIME_ERROR;
    }
}
