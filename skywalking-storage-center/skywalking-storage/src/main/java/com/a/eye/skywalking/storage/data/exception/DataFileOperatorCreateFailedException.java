package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/5.
 */
public class DataFileOperatorCreateFailedException extends RuntimeException {
    public DataFileOperatorCreateFailedException(String message, Exception e){
        super(message, e);
    }
}
