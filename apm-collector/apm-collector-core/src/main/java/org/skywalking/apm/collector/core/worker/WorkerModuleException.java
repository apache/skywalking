package org.skywalking.apm.collector.core.worker;

import org.skywalking.apm.collector.core.module.ModuleException;

/**
 * @author pengys5
 */
public class WorkerModuleException extends ModuleException {

    public WorkerModuleException(String message) {
        super(message);
    }

    public WorkerModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
