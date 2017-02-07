package com.a.eye.skywalking.storage.disruptor.request;

import com.a.eye.skywalking.storage.data.spandata.RequestSpanData;
import com.lmax.disruptor.EventFactory;

/**
 * Created by wusheng on 2016/11/24.
 */
public class RequestSpanFactory implements EventFactory<RequestSpanDataHolder> {
    @Override
    public RequestSpanDataHolder newInstance() {
        return new RequestSpanDataHolder();
    }
}
