package com.a.eye.skywalking.reciever.processor.ackspan.alarm;

import com.a.eye.skywalking.protocol.AckSpan;

public interface ISpanChecker {
    void check(AckSpan span);
}
