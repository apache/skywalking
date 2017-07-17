package org.skywalking.apm.collector.core.module;

import org.skywalking.apm.collector.core.config.ConfigLoaderException;

/**
 * @author pengys5
 */
public class ModuleConfigLoaderException extends ConfigLoaderException {
    public ModuleConfigLoaderException(String message) {
        super(message);
    }

    public ModuleConfigLoaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
