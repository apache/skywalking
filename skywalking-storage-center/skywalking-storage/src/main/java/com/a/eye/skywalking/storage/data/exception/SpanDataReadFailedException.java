package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/6.
 */
public class SpanDataReadFailedException extends RuntimeException {
    public SpanDataReadFailedException(String message, Exception e) {
        super(message, e);
    }
}
