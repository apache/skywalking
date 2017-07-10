package org.skywalking.apm.collector.core;

/**
 * @author pengys5
 */
public class CollectorException extends Exception {

    public CollectorException(String message) {
        super(message);
    }

    public CollectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
