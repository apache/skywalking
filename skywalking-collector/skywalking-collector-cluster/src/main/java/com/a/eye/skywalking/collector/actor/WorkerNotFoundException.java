package com.a.eye.skywalking.collector.actor;

public class WorkerNotFoundException extends Exception {
    public WorkerNotFoundException(String message) {
        super(message);
    }
}
