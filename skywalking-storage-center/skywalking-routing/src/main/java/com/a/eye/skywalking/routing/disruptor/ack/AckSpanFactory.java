package com.a.eye.skywalking.routing.disruptor.ack;

import com.lmax.disruptor.EventFactory;

/**
 * Created by xin on 2016/11/27.
 */
public class AckSpanFactory implements EventFactory<AckSpanHolder> {

    @Override
    public AckSpanHolder newInstance() {
        return new AckSpanHolder();
    }
}
