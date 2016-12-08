package com.a.eye.skywalking.storage.alarm;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.storage.alarm.checker.*;
import com.a.eye.skywalking.storage.alarm.sender.AlarmMessageSenderFactory;
import com.a.eye.skywalking.storage.data.spandata.AckSpanData;
import com.lmax.disruptor.EventHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 2016/12/8.
 */
public class SpanAlarmHandler implements EventHandler<AckSpanData> {
    private List<ISpanChecker> spanCheckers = new ArrayList<ISpanChecker>();

    public SpanAlarmHandler() {
        spanCheckers.add(new ExceptionChecker());
        spanCheckers.add(new ExecuteTimePossibleWarningChecker());
        spanCheckers.add(new ExecuteTimePossibleErrorChecker());
    }

    private String generateAlarmMessageKey(AckSpanData span, FatalReason reason) {
        return span.getUserName() + "-" + span.getApplicationCode() + "-" + (System.currentTimeMillis() / (10000 * 6)) + "-" + reason;
    }

    @Override
    public void onEvent(AckSpanData spanData, long sequence, boolean endOfBatch) throws Exception {
        for (ISpanChecker spanChecker : spanCheckers) {
            CheckResult result = spanChecker.check(spanData);
            if (!result.isPassed()) {
                AlarmMessageSenderFactory.getSender().send(generateAlarmMessageKey(spanData, result.getFatalReason()), result.getMessage());
            }
        }
    }
}
