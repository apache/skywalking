package com.ai.cloud.skywalking.reciever.storage;

public class ChainException extends RuntimeException {

    public ChainException(Throwable cause) {
        super(cause);
    }

    public ChainException(String message, Throwable cause) {
        super(message, cause);
    }
}
