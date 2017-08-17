package org.skywalking.apm.collector.core.module;

import org.skywalking.apm.collector.core.framework.DefineException;

/**
 * @author pengys5
 */
public class ModuleDefineException extends DefineException {
    public ModuleDefineException(String message) {
        super(message);
    }

    public ModuleDefineException(String message, Throwable cause) {
        super(message, cause);
    }
}
