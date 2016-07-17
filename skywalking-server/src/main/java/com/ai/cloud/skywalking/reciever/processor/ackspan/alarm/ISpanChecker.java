package com.ai.cloud.skywalking.reciever.processor.ackspan.alarm;

import com.ai.cloud.skywalking.protocol.AckSpan;

public interface ISpanChecker {
    void check(AckSpan span);
}
