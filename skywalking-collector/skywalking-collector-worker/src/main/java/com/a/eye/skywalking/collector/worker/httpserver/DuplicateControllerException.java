package com.a.eye.skywalking.collector.worker.httpserver;

public class DuplicateControllerException extends Exception {
    public DuplicateControllerException(String message){
        super(message);
    }
}
