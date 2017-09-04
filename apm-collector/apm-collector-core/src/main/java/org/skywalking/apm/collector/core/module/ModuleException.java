package org.skywalking.apm.collector.core.module;

import org.skywalking.apm.collector.core.framework.DefineException;

/**
 * @author pengys5
 */
public abstract class ModuleException extends DefineException {

    public ModuleException(String message) {
        super(message);
    }

    public ModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
