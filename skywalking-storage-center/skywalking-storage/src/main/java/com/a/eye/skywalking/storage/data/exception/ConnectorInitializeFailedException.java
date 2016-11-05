package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/5.
 */
public class ConnectorInitializeFailedException extends RuntimeException {
    public ConnectorInitializeFailedException(String message, Exception e) {
        super(message, e);
    }
}
