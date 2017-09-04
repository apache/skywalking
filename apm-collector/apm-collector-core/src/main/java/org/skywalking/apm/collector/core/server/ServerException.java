package org.skywalking.apm.collector.core.server;

import org.skywalking.apm.collector.core.CollectorException;

/**
 * @author pengys5
 */
public abstract class ServerException extends CollectorException {

    public ServerException(String message) {
        super(message);
    }

    public ServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
