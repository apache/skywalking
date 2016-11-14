package com.a.eye.skywalking.storage.data.exception;

public class IndexMetaStoredFailedException extends RuntimeException {
    public IndexMetaStoredFailedException(String message, Exception e){
        super(message, e);
    }
}
