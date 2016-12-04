package com.a.eye.skywalking.routing.disruptor;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;

/**
 * Created by xin on 2016/11/29.
 */
public class NoopSpanDisruptor extends SpanDisruptor {
    public static NoopSpanDisruptor INSTANCE = new NoopSpanDisruptor();

    private NoopSpanDisruptor() {

    }

    @Override
    public boolean saveSpan(AckSpan ackSpan) {
        return false;
    }

    @Override
    public boolean saveSpan(RequestSpan requestSpan) {
        return false;
    }
}
