package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/14.
 */
public class SpanConvertFailedException extends RuntimeException {
    public SpanConvertFailedException(String message, Exception e) {
        super(message, e);
    }
}
