package com.a.eye.skywalking.protocol.exception;

public class SpanTypeCannotConvertException extends RuntimeException {
    public SpanTypeCannotConvertException(String spanTypeValue) {
        super("Can not convert SpanTypeValue[" + spanTypeValue + "]");
    }
}
