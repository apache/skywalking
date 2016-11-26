package com.a.eye.skywalking.disruptor.request;

import com.lmax.disruptor.EventFactory;

/**
 * Created by wusheng on 2016/11/24.
 */
public class RequestSpanFactory implements EventFactory<RequestSpanHolder> {
    @Override
    public RequestSpanHolder newInstance() {
        return new RequestSpanHolder();
    }
}
