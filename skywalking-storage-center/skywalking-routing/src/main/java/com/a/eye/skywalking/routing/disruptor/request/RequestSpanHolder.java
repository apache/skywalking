package com.a.eye.skywalking.routing.disruptor.request;

import com.a.eye.skywalking.network.grpc.RequestSpan;

/**
 * Created by xin on 2016/11/27.
 */
public class RequestSpanHolder {
    private RequestSpan requestSpan;

    public void setRequestSpan(RequestSpan requestSpan) {
        this.requestSpan = requestSpan;
    }

    public RequestSpan getRequestSpan() {
        return requestSpan;
    }
}
