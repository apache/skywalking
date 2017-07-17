package org.skywalking.apm.collector.core.worker;

public class WorkerNotFoundException extends WorkerException {
    public WorkerNotFoundException(String message) {
        super(message);
    }
}
