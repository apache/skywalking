package org.skywalking.apm.collector.core.worker;

/**
 * This exception is raised when worker fails to process job during "call" or "ask" 
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
