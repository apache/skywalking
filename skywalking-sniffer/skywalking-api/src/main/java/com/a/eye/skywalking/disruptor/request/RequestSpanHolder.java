package com.a.eye.skywalking.disruptor.request;

import com.a.eye.skywalking.network.grpc.RequestSpan;

/**
 * Created by wusheng on 2016/11/26.
 */
public class RequestSpanHolder {
    private RequestSpan data;

    public RequestSpan getData() {
        return data;
    }

    public void setData(RequestSpan data) {
        this.data = data;
    }
}
