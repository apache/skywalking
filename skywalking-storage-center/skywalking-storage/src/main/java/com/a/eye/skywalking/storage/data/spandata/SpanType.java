package com.a.eye.skywalking.storage.data.spandata;

/**
 * Created by xin on 2016/11/12.
 */
public enum SpanType {
    RequestSpan(1),
    ACKSpan(2);

    int value;

    SpanType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static SpanType convert(int value) {
        switch (value) {
            case 1:
                return RequestSpan;
            case 2:
                return ACKSpan;
            default:
                throw new IllegalArgumentException("Failed to convert to value" + value);
        }
    }
}
