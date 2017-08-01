package org.skywalking.apm.collector.queue;

import org.skywalking.apm.collector.core.module.ModuleException;

/**
 * @author pengys5
 */
public class QueueModuleException extends ModuleException {
    public QueueModuleException(String message) {
        super(message);
    }

    public QueueModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
