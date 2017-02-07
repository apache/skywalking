package com.a.eye.skywalking.storage.disruptor.request;

import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.storage.data.spandata.RequestSpanData;

/**
 * @author zhangxin
 */
public class RequestSpanDataHolder {

    private RequestSpanData requestSpanData;

    public void clearData() {
        this.requestSpanData = null;
    }

    public RequestSpanData getRequestSpanData() {
        return requestSpanData;
    }


    public void fillData(RequestSpan requestSpan) {
        requestSpanData = new RequestSpanData(requestSpan);
    }
}
