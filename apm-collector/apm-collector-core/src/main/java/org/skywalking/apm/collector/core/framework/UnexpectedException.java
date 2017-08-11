package org.skywalking.apm.collector.core.framework;

/**
 * @author pengys5
 */
public class UnexpectedException extends RuntimeException {

    public UnexpectedException(String message) {
        super(message);
    }
}
