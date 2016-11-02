package com.a.eye.skywalking.storage.index.exception;

/**
 * Created by xin on 2016/11/2.
 */
public class DataFileIndexSaveFailedException extends Exception {
    public DataFileIndexSaveFailedException(String message, Exception e) {
        super(message, e);
    }
}
