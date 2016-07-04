package com.ai.cloud.skywalking.protocol.common;

import com.ai.cloud.skywalking.protocol.exception.SpanTypeCannotConvertException;

/**
 * Created by xin on 16-7-2.
 */
public enum SpanType {

    LOCAL((byte) 1), RPC_CLIENT((byte) 2), RPC_SERVER((byte) 4);

    private byte value;

    SpanType(byte value) {
        this.value = value;
    }

   public static SpanType convert(String spanTypeValue) {
        switch (Byte.valueOf(spanTypeValue)){
            case 1 : return LOCAL;
            case 2 : return RPC_CLIENT;
            case 3 : return RPC_SERVER;
            default:
                throw new SpanTypeCannotConvertException(spanTypeValue);
        }
    }


    public byte getValue() {
        return value;
    }
}
