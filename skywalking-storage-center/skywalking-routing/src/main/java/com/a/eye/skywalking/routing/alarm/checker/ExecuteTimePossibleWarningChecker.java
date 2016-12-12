package com.a.eye.skywalking.routing.alarm.checker;

/**
 * Created by xin on 2016/12/8.
 */
public class ExecuteTimePossibleWarningChecker extends ExecuteTimeChecker {
    @Override
    protected boolean isOverThreshold(long cost) {
        return cost > 500 && cost < 3000;
    }

    @Override
    protected FatalReason getFatalLevel() {
        return FatalReason.EXECUTE_TIME_WARNING;
    }

}
