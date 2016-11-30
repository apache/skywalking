package com.a.eye.skywalking.routing.disruptor.ack;

import com.a.eye.skywalking.network.grpc.AckSpan;

/**
 * Created by xin on 2016/11/27.
 */
public class AckSpanHolder {
    private AckSpan ackSpan;

    public void setAckSpan(AckSpan ackSpan) {
        this.ackSpan = ackSpan;
    }

    public AckSpan getAckSpan() {
        return ackSpan;
    }
}
