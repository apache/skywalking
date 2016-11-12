package com.a.eye.skywalking.testframework.api.exception;

public class TraceIdNotSameException extends RuntimeException {
    public TraceIdNotSameException(String message) {
        super(message);
    }
}
