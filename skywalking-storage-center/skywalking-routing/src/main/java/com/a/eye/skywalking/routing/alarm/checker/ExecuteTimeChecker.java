package com.a.eye.skywalking.routing.alarm.checker;

import com.a.eye.skywalking.network.model.Tag;
import com.a.eye.skywalking.routing.disruptor.ack.AckSpanHolder;

/**
 * Created by xin on 2016/12/8.
 */
public abstract class ExecuteTimeChecker implements ISpanChecker {

    @Override
    public CheckResult check(AckSpanHolder span) {
        long cost = span.getAckSpan().getCost();
        if (isOverThreshold(cost)) {
            return new CheckResult(getFatalLevel(), generateAlarmMessage(span));
        }

        return new CheckResult();
    }

    protected abstract boolean isOverThreshold(long cost);

    protected abstract FatalReason getFatalLevel();

    protected String generateAlarmMessage(AckSpanHolder span) {
        return span.getViewPoint() + span.getAckSpan().getCost() + " ms.";
    }


}
