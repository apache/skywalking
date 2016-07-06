package com.ai.cloud.skywalking.reciever.processor.exception;

public class HBaseInitFailedException extends RuntimeException {
    public HBaseInitFailedException(String message, Exception e) {
        super(message, e);
    }
}
