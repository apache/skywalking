package com.a.eye.skywalking.storage.alarm.checker;

import com.a.eye.skywalking.network.grpc.AckSpan;

/**
 * Created by xin on 2016/12/8.
 */
public abstract class ExecuteTimeChecker implements ISpanChecker {

    @Override
    public CheckResult check(AckSpan span) {
        long cost = span.getCost();
        if (isOverThreshold(cost)) {
            return new CheckResult(getFatalLevel(), generateAlarmMessage(span));
        }

        return new CheckResult();
    }

    protected abstract boolean isOverThreshold(long cost);

    protected abstract FatalReason getFatalLevel();

    protected String generateAlarmMessage(AckSpan span) {
        return span.getViewpointId() + " cost " + span.getCost() + " ms.";
    }


}
