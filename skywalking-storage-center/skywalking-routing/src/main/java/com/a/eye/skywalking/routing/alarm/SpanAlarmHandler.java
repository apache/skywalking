package com.a.eye.skywalking.routing.alarm;

import com.a.eye.skywalking.routing.alarm.checker.*;
import com.a.eye.skywalking.routing.alarm.sender.AlarmMessageSenderFactory;
import com.a.eye.skywalking.routing.disruptor.ack.AckSpanHolder;
import com.a.eye.skywalking.util.TraceIdUtil;
import com.lmax.disruptor.EventHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 2016/12/8.
 */
public class SpanAlarmHandler implements EventHandler<AckSpanHolder> {
    private List<ISpanChecker> spanCheckers = new ArrayList<ISpanChecker>();

    public SpanAlarmHandler() {
        spanCheckers.add(new ExceptionChecker());
        spanCheckers.add(new ExecuteTimePossibleWarningChecker());
        spanCheckers.add(new ExecuteTimePossibleErrorChecker());
    }

    private String generateAlarmMessageKey(AckSpanHolder span, FatalReason reason) {
        return span.getAckSpan().getUsername() + "-" + span.getAckSpan().getApplicationCode() + "-" + (System.currentTimeMillis() / (10000 * 6)) + reason.getDetail();
    }

    @Override
    public void onEvent(AckSpanHolder spanData, long sequence, boolean endOfBatch) throws Exception {
        for (ISpanChecker spanChecker : spanCheckers) {
            CheckResult result = spanChecker.check(spanData);
            if (!result.isPassed()) {
                AlarmMessageSenderFactory.getSender().send(generateAlarmMessageKey(spanData, result.getFatalReason()), TraceIdUtil.formatTraceId(spanData.getAckSpan().getTraceId()), result.getMessage());
            }
        }
    }
}
