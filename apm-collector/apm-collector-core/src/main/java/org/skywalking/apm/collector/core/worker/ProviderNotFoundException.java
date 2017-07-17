package org.skywalking.apm.collector.core.worker;

public class ProviderNotFoundException extends Exception {
    public ProviderNotFoundException(String message) {
        super(message);
    }
}
