package com.a.eye.skywalking.storage.disruptor.ack;

import com.a.eye.skywalking.storage.data.spandata.AckSpanData;
import com.lmax.disruptor.EventFactory;

/**
 * Created by wusheng on 2016/11/24.
 */
public class AckSpanFactory implements EventFactory<AckSpanDataHolder> {
    @Override
    public AckSpanDataHolder newInstance() {
        return new AckSpanDataHolder();
    }
}
