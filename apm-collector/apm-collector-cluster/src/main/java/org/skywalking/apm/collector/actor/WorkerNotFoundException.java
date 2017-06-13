package org.skywalking.apm.collector.actor;

public class WorkerNotFoundException extends WorkerException {
    public WorkerNotFoundException(String message) {
        super(message);
    }
}
