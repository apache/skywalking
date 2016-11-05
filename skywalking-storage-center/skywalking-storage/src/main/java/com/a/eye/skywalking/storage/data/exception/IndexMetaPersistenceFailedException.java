package com.a.eye.skywalking.storage.data.exception;

public class IndexMetaPersistenceFailedException extends RuntimeException {
    public IndexMetaPersistenceFailedException(String message, Exception e){
        super(message, e);
    }
}
