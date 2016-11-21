package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/5.
 */
public class CreateDataFileOperatorFailedException extends RuntimeException {
    public CreateDataFileOperatorFailedException(String message, Exception e){
        super(message, e);
    }
}
