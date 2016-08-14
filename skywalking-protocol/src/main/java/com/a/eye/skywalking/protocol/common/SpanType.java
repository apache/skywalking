package com.a.eye.skywalking.protocol.common;

import com.a.eye.skywalking.protocol.exception.SpanTypeCannotConvertException;

/**
 * Created by xin on 16-7-2.
 */
public enum SpanType {

    LOCAL(1),
    RPC_CLIENT(2),
    RPC_SERVER(4);

    private int value;

    SpanType(int value) {
        this.value = value;
    }

    public static SpanType convert(int spanTypeValue) {
        switch (spanTypeValue) {
            case 1:
                return LOCAL;
            case 2:
                return RPC_CLIENT;
            case 4:
                return RPC_SERVER;
            default:
                throw new SpanTypeCannotConvertException(spanTypeValue + "");
        }
    }


    public int getValue() {
        return value;
    }


}
