package com.ai.cloud.skywalking.example.resource.exception;

public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
