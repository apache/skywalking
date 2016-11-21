package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/6.
 */
public class ReadSpanDataFailedException extends RuntimeException {
    public ReadSpanDataFailedException(String message, Exception e) {
        super(message, e);
    }
}
