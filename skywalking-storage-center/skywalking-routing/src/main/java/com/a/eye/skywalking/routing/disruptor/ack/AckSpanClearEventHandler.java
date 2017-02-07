package com.a.eye.skywalking.routing.disruptor.ack;

import com.lmax.disruptor.EventHandler;

/**
 * Created by xin on 2017/2/8.
 */
public class AckSpanClearEventHandler implements EventHandler<AckSpanHolder> {

    @Override
    public void onEvent(AckSpanHolder event, long sequence, boolean endOfBatch) throws Exception {
        event.setAckSpan(null);
    }
}
