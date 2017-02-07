package com.a.eye.skywalking.storage.disruptor.ack;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.storage.data.spandata.AckSpanData;

/**
 * @author zhangxin
 */
public class AckSpanDataHolder {
    private AckSpanData ackSpanData;

    public AckSpanData getAckSpanData() {
        return ackSpanData;
    }

    public void clearData() {
        this.ackSpanData = null;
    }

    public void fillData(AckSpan ackSpan) {
        this.ackSpanData = new AckSpanData(ackSpan);
    }
}
