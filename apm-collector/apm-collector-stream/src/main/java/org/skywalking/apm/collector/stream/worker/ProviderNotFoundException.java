package org.skywalking.apm.collector.stream.worker;

public class ProviderNotFoundException extends Exception {
    public ProviderNotFoundException(String message) {
        super(message);
    }
}
