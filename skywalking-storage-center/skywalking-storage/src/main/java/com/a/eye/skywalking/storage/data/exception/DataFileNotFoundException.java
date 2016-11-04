package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/4.
 */
public class DataFileNotFoundException extends Exception {
    public DataFileNotFoundException(String message, Exception e) {
        super(message, e);
    }
}
