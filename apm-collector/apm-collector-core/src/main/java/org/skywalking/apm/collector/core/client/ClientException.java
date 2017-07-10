package org.skywalking.apm.collector.core.client;

import org.skywalking.apm.collector.core.CollectorException;

/**
 * @author pengys5
 */
public abstract class ClientException extends CollectorException {
    public ClientException(String message) {
        super(message);
    }

    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
