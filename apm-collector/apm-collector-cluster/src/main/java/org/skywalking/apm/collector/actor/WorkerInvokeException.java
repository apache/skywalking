package org.skywalking.apm.collector.actor;

/**
 * This exception is raised when call (or ask) worker.
 *
 * @author pengys5
 * @since v3.1-2017
 */
public class WorkerInvokeException extends WorkerException {

    public WorkerInvokeException(String message) {
        super(message);
    }

    public WorkerInvokeException(String message, Throwable cause) {
        super(message, cause);
    }
}
