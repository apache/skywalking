package org.skywalking.apm.collector.server.jetty;

import org.skywalking.apm.collector.core.server.ServerException;

/**
 * @author pengys5
 */
public class JettyServerException extends ServerException {

    public JettyServerException(String message) {
        super(message);
    }

    public JettyServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
