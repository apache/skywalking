package com.a.eye.skywalking.routing.disruptor.ack;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.model.Tag;

/**
 * Created by xin on 2016/11/27.
 */
public class AckSpanHolder {
    private AckSpan ackSpan;

    public AckSpanHolder() {
    }

    public AckSpanHolder(AckSpan ackSpan) {
        this.ackSpan = ackSpan;
    }

    public void setAckSpan(AckSpan ackSpan) {
        this.ackSpan = ackSpan;
    }

    public AckSpan getAckSpan() {
        return ackSpan;
    }

    public String getViewPoint() {
        return ackSpan.getTagsMap().get(Tag.VIEW_POINT.key());
    }

    public String getUserName() {
        return ackSpan.getTagsMap().get(Tag.USER_NAME.key());
    }

    public String getApplicationCode() {
        return ackSpan.getTagsMap().get(Tag.APPLICATION_CODE.key());
    }

    public int getStatusCode() {
        return Integer.parseInt(ackSpan.getTagsMap().get(Tag.STATUS.key()));
    }

    public String getExceptionStack(){
        return ackSpan.getTagsMap().get(Tag.EXCEPTION_STACK.key());
    }
}
