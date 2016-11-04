package com.a.eye.skywalking.storage.data.exception;

/**
 * Created by xin on 2016/11/4.
 */
public class FileReaderCreateFailedException extends Throwable {
    public FileReaderCreateFailedException(String message, Exception e) {
        super(message, e);
    }
}
