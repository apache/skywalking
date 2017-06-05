package org.skywalking.apm.collector.actor;

public class ProviderNotFoundException extends Exception {
    public ProviderNotFoundException(String message) {
        super(message);
    }
}
