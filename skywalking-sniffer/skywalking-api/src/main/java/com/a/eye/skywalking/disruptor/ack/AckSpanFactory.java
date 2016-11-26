package com.a.eye.skywalking.disruptor.ack;

import com.lmax.disruptor.EventFactory;

/**
 * Created by wusheng on 2016/11/24.
 */
public class AckSpanFactory implements EventFactory<AckSpanHolder> {
    @Override
    public AckSpanHolder newInstance() {
        return new AckSpanHolder();
    }
}
