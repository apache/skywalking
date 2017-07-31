package org.skywalking.apm.collector.server.jetty;

import org.skywalking.apm.collector.core.CollectorException;

/**
 * @author pengys5
 */
public class ArgumentsParseException extends CollectorException {

    public ArgumentsParseException(String message) {
        super(message);
    }

    public ArgumentsParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
