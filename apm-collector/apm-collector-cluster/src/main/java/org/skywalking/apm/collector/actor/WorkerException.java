package org.skywalking.apm.collector.actor;

/**
 * Defines a general exception a worker can throw when it
 * encounters difficulty.
 *
 * @author pengys5
 * @since v3.1-2017
 */
public class WorkerException extends Exception {

    public WorkerException(String message) {
        super(message);
    }

    public WorkerException(String message, Throwable cause) {
        super(message, cause);
    }
}
