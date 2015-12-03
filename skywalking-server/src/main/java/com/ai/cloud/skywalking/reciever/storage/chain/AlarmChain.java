package com.ai.cloud.skywalking.reciever.storage.chain;

import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.storage.Chain;
import com.ai.cloud.skywalking.reciever.storage.IStorageChain;
import com.ai.cloud.skywalking.reciever.storage.chain.alarm.AlarmMessageStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class AlarmChain implements IStorageChain {

    private Logger logger = LogManager.getLogger(AlarmChain.class);

    @Override
    public void doChain(List<Span> spans, Chain chain) {
        if (Config.Alarm.ALARM_OFF_FLAG) {
            chain.doChain(spans);
            return;
        }
        for (Span span : spans) {
            if (span.getStatusCode() != 1)
                continue;
            AlarmMessageStorage.saveAlarmMessage(
                    generateAlarmKey(span)
                    , span.getTraceId());
        }
        chain.doChain(spans);
    }

    private String generateAlarmKey(Span span) {
        return span.getUserId() + "-"
                + span.getApplicationId() + "-"
                + (System.currentTimeMillis() / (10000 * 6));
    }
}
