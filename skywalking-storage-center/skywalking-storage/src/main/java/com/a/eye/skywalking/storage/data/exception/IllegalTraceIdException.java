package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/15.
 */
public class IllegalTraceIdException extends RuntimeException {
    public IllegalTraceIdException(String message) {
        super(message);
    }
}
