package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/20.
 */
public class IndexOperateFailedException extends RuntimeException {
    public IndexOperateFailedException(String message, Exception e) {
        super(message, e);
    }
}
