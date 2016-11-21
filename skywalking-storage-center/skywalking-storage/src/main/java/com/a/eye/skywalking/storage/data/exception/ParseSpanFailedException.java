package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/14.
 */
public class ParseSpanFailedException extends RuntimeException {
    public ParseSpanFailedException(String message, Exception e) {
        super(message, e);
    }
}
