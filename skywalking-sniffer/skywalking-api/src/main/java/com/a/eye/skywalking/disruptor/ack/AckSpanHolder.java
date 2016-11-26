package com.a.eye.skywalking.disruptor.ack;

import com.a.eye.skywalking.network.grpc.AckSpan;

/**
 * Created by wusheng on 2016/11/26.
 */
public class AckSpanHolder {
    private AckSpan data;

    public AckSpan getData() {
        return data;
    }

    public void setData(AckSpan data) {
        this.data = data;
    }
}
