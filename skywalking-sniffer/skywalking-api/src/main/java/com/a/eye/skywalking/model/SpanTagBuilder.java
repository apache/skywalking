package com.a.eye.skywalking.model;

import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.model.Tag;

/**
 * Created by xin on 2016/12/23.
 */
public class SpanTagBuilder {
    private final Span span;

    private SpanTagBuilder(Span span) {
        this.span = span;
    }


    public static SpanTagBuilder newBuilder(Span span) {
        return new SpanTagBuilder(span);
    }


    public SpanTagBuilder setSpanType(int spanType) {
        span.setTag(Tag.SPAN_TYPE, spanType + "");
        return this;
    }


    public Span build() {
        return this.span;
    }


    public RequestSpan buildRequestSpan(RequestSpan.Builder builder) {
        return span.buildRequestSpan(builder).build();
    }

    public AckSpan buildAckSpan(AckSpan.Builder builder){
        return span.buildAckSpan(builder).build();
    }

    public SpanTagBuilder setBusinessKey(String businessKey) {
        span.setTag(Tag.BUSINESS_KEY, businessKey);
        return this;
    }

    public SpanTagBuilder setSpanTypeDesc(String spanTypeDesc) {
        span.setTag(Tag.CALL_DESC, spanTypeDesc);
        return this;
    }

    public SpanTagBuilder setCallType(String callType) {
        span.setTag(Tag.CALL_TYPE, callType);
        return this;
    }

    public SpanTagBuilder setProcessNo(int processNo) {
        span.setTag(Tag.PROCESS_NO, processNo + "");
        return this;
    }

    public SpanTagBuilder setAddress(String address) {
        span.setTag(Tag.ADDRESS, address);
        return this;
    }

    public SpanTagBuilder setStatusCode(int statusCode){
        span.setTag(Tag.STATUS, statusCode + "");
        return this;
    }

    public SpanTagBuilder setExceptionStack(String exceptionStack){
        span.setTag(Tag.EXCEPTION_STACK, exceptionStack);
        return this;
    }
}
