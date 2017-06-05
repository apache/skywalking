package org.skywalking.apm.collector.actor;

public class WorkerNotFoundException extends Exception {
    public WorkerNotFoundException(String message) {
        super(message);
    }
}
