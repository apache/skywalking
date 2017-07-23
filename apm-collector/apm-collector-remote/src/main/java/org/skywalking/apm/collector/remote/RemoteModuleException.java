package org.skywalking.apm.collector.remote;

import org.skywalking.apm.collector.core.module.ModuleException;

/**
 * @author pengys5
 */
public class RemoteModuleException extends ModuleException {

    public RemoteModuleException(String message) {
        super(message);
    }

    public RemoteModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
