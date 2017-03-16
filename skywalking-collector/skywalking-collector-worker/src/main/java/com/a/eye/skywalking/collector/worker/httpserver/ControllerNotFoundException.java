package com.a.eye.skywalking.collector.worker.httpserver;

public class ControllerNotFoundException extends Exception {
    public ControllerNotFoundException(String message){
        super(message);
    }
}
