package com.a.eye.skywalking.storage.exception;

/**
 * Index data cannot be saved.
 */
public class IndexDataSaveFailedException extends Exception {

    public IndexDataSaveFailedException(String message) {
        super(message);
    }
}
