package com.a.eye.skywalking.routing.disruptor.request;

import com.lmax.disruptor.EventFactory;

/**
 * Created by xin on 2016/11/27.
 */
public class RequestSpanFactory implements EventFactory<RequestSpanHolder> {
    @Override
    public RequestSpanHolder newInstance() {
        return new RequestSpanHolder();
    }
}
