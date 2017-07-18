package org.skywalking.apm.collector.stream;

public class ProviderNotFoundException extends Exception {
    public ProviderNotFoundException(String message) {
        super(message);
    }
}
