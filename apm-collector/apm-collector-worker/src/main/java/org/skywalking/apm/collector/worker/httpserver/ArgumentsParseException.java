package org.skywalking.apm.collector.worker.httpserver;

import org.skywalking.apm.collector.actor.WorkerException;

/**
 * This exception is raised when can't find the required argument or data type conversion fails from request.
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
