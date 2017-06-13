package org.skywalking.apm.collector.worker.httpserver;

import org.skywalking.apm.collector.actor.WorkerException;

/**
 * This exception is raised when argument not found or data type conversion from request.
 *
 * @author pengys5
 * @since v3.1-2017
 */
public class ArgumentsParseException extends WorkerException {

    public ArgumentsParseException(String message) {
        super(message);
    }

    public ArgumentsParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
