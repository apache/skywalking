package org.skywalking.apm.collector.core.framework;

import org.skywalking.apm.collector.core.CollectorException;

/**
 * @author pengys5
 */
public abstract class DefineException extends CollectorException {

    public DefineException(String message) {
        super(message);
    }

    public DefineException(String message, Throwable cause) {
        super(message, cause);
    }
}
