package org.skywalking.apm.collector.worker.instance;

public class PersistenceFailedException extends RuntimeException {
    public PersistenceFailedException(String message) {
        super(message);
    }
}
